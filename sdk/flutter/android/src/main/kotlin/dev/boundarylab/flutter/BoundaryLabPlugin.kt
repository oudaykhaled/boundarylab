package dev.boundarylab.flutter

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import dev.boundarylab.core.FieldPackCodec
import dev.boundarylab.core.FieldPackEntry
import dev.boundarylab.core.FieldPackManifest
import dev.boundarylab.sdk.BoundaryClient
import dev.boundarylab.sdk.BoundaryException
import dev.boundarylab.sdk.JobHandle
import dev.boundarylab.sdk.JobSnapshot
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import java.util.UUID
import java.util.concurrent.Executors

/** Every instance belongs to exactly one engine; no global session or URI store. */
class BoundaryLabPlugin : FlutterPlugin, ActivityAware, MethodChannel.MethodCallHandler,
    PluginRegistry.ActivityResultListener {
    private lateinit var channel: MethodChannel
    private lateinit var client: BoundaryClient
    private lateinit var resolver: android.content.ContentResolver
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var activityBinding: ActivityPluginBinding? = null
    @Volatile private var attached = false
    @Volatile private var generation = 0L
    private val selections = linkedMapOf<String, SelectedFile>()
    private var active: JobHandle? = null
    private var operation: MethodChannel.Result? = null
    private var cancellation: MethodChannel.Result? = null
    private var admissionPending = false
    private var openingPending = false
    private var openSignal: android.os.CancellationSignal? = null
    private var picker: MethodChannel.Result? = null
    private var exporter: MethodChannel.Result? = null
    private var manifest: ByteArray? = null
    private var capabilities = emptySet<String>()

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        resolver = binding.applicationContext.contentResolver
        client = BoundaryClient(binding.applicationContext)
        channel = MethodChannel(binding.binaryMessenger, "dev.boundarylab/client")
        channel.setMethodCallHandler(this)
        attached = true
        client.onLoss = {
            invalidate("LOST_OR_UNKNOWN")
            completeCancellation()
            channel.invokeMethod("lost", null)
        }
    }

    override fun onMethodCall(call: MethodCall, rawResult: MethodChannel.Result) {
        if (!attached) return rawResult.error("DISPOSED", "Engine is disposed", null)
        val result = OnceResult(rawResult)
        when (call.method) {
            "connect" -> connect(result)
            "select" -> select(result)
            "prepare" -> prepare(call, result)
            "generated" -> generated(call, result)
            "cancel" -> cancel(result)
            "clear" -> { invalidate("CANCELLED"); selections.clear(); result.success(null) }
            "export" -> export(result)
            "dispose" -> { result.success(null); dispose() }
            else -> result.notImplemented()
        }
    }

    private fun connect(result: MethodChannel.Result) {
        val ticket = generation
        client.connect { outcome ->
            if (!current(ticket)) return@connect result.error("CANCELLED", "Connection superseded", null)
            outcome.fold({ capabilities = it.operations; result.success(null) }, { fail(result, it) })
        }
    }

    private fun select(result: MethodChannel.Result) {
        if (picker != null || exporter != null || operation != null) return busy(result)
        val activity = activityBinding?.activity ?: return unavailable(result)
        invalidate("CANCELLED")
        selections.clear()
        picker = result
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { activity.startActivityForResult(intent, PICK) }
        catch (error: Exception) { picker = null; fail(result, error) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        return when (requestCode) {
            PICK -> { completeSelection(resultCode, data); true }
            EXPORT -> { completeExport(resultCode, data); true }
            else -> false
        }
    }

    private fun completeSelection(resultCode: Int, data: Intent?) {
        val result = picker ?: return
        if (resultCode != Activity.RESULT_OK) { picker = null; return result.success(emptyList<Any>()) }
        val clip = data?.clipData
        val uris = if (clip != null) (0 until clip.itemCount).map { clip.getItemAt(it).uri }
            else listOfNotNull(data?.data)
        if (uris.isEmpty() || uris.size > MAX_ENTRIES || uris.distinct().size != uris.size) {
            picker = null
            return result.error("INVALID", "Select between 1 and 32 distinct files", null)
        }
        val ticket = generation
        io.execute {
            val files = uris.mapIndexed { index, uri ->
                val displayName = runCatching {
                    resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0)?.substringAfterLast('/')?.substringAfterLast(92.toChar())?.filter { ch -> !ch.isISOControl() }?.take(128) else null
                    }
                }.getOrNull() ?: "Selected attachment ${index + 1}"
                SelectedFile(uri, "attachment-${(index + 1).toString().padStart(3, '0')}.bin", displayName)
            }
            handler.post {
                if (!current(ticket)) result.error("CANCELLED", "Selection superseded", null)
                else {
                    picker = null
                    files.forEach { selections[UUID.randomUUID().toString()] = it }
                    result.success(selections.map { (handle, file) ->
                        mapOf("handle" to handle, "alias" to file.alias, "displayName" to file.displayName)
                    })
                }
            }
        }
    }

    private fun prepare(call: MethodCall, result: MethodChannel.Result) {
        if (operation != null || picker != null || exporter != null || cancellation != null) return busy(result)
        if ("regular-file-sha256-v1" !in capabilities) return unsupported(result)
        val handles = call.argument<List<String>>("handles") ?: emptyList()
        if (handles.isEmpty() || handles.size > MAX_ENTRIES || handles.distinct().size != handles.size) {
            return result.error("INVALID", "Invalid selection", null)
        }
        val files = handles.mapNotNull { selections[it] }
        if (files.size != handles.size) return result.error("STALE_SELECTION", "Select files again", null)
        invalidate("CANCELLED")
        operation = result
        nextFile(generation, files, mutableListOf())
    }

    private fun nextFile(ticket: Long, files: List<SelectedFile>, entries: MutableList<FieldPackEntry>) {
        if (!current(ticket)) return
        if (entries.size == files.size) return completePack(entries)
        val file = files[entries.size]
        channel.invokeMethod("progress", mapOf("alias" to file.alias, "index" to entries.size + 1, "total" to files.size, "bytes" to 0L, "state" to "OPENING"))
        openingPending = true
        val signal = android.os.CancellationSignal()
        openSignal = signal
        io.execute {
            try {
                val fd = resolver.openFileDescriptor(file.uri, "r", signal) ?: error("Missing descriptor")
                val size = fd.statSize
                handler.post {
                    openingPending = false; openSignal = null
                    if (!current(ticket)) { fd.close(); completeCancellation(); return@post }
                    if (size < 0 || size > MAX_BYTES - entries.sumOf { it.size }) {
                        fd.close(); finishError("INVALID", "Unknown size or pack exceeds 128 MiB")
                    } else submitFile(ticket, fd, file, files, entries)
                }
            } catch (error: Exception) {
                handler.post {
                    openingPending = false; openSignal = null
                    if (current(ticket)) finishError("IO_ERROR", "Cannot open selected file")
                    else completeCancellation()
                }
            }
        }
    }

    private fun submitFile(ticket: Long, fd: android.os.ParcelFileDescriptor, file: SelectedFile,
                           files: List<SelectedFile>, entries: MutableList<FieldPackEntry>) {
        admissionPending = true
        client.submitFile(fd, file.alias) { outcome ->
            admissionPending = false
            if (!current(ticket)) { staleAdmission(outcome.getOrNull()); return@submitFile }
            outcome.fold({ handle ->
                active = handle
                poll(ticket, handle) { snapshot ->
                    entries.add(FieldPackEntry(file.alias, snapshot.bytesProcessed, snapshot.sha256!!))
                    nextFile(ticket, files, entries)
                }
            }, { finishFailure(it) })
        }
    }

    private fun completePack(entries: List<FieldPackEntry>) {
        try {
            manifest = FieldPackCodec.encode(FieldPackManifest(entries))
            operation?.success(entries.sortedBy { it.name }.map { entryMap(it) })
            operation = null
            active = null
        } catch (error: Exception) { finishError("INVALID", "Pack validation failed") }
    }

    private fun generated(call: MethodCall, result: MethodChannel.Result) {
        if (operation != null || picker != null || exporter != null || cancellation != null) return busy(result)
        if ("generated-sha256-v1" !in capabilities) return unsupported(result)
        val arguments = GeneratedArguments.decode(call.arguments)
            ?: return result.error("INVALID", "Expected signed integer seed and bounded integer bytes", null)
        invalidate("CANCELLED")
        operation = result
        val ticket = generation
        admissionPending = true
        client.submitGenerated(arguments.seed, arguments.bytes, deadlineMs = 30000L) { outcome ->
            admissionPending = false
            if (!current(ticket)) { staleAdmission(outcome.getOrNull()); return@submitGenerated }
            outcome.fold({ handle ->
                active = handle
                poll(ticket, handle) { snapshot ->
                    operation?.success(entryMap(FieldPackEntry("generated.bin", snapshot.bytesProcessed, snapshot.sha256!!)))
                    operation = null; active = null
                }
            }, { finishFailure(it) })
        }
    }

    private fun poll(ticket: Long, handle: JobHandle, success: (JobSnapshot) -> Unit) {
        if (!current(ticket)) return
        client.status(handle) { outcome ->
            if (!current(ticket)) return@status
            outcome.fold({ snapshot ->
                channel.invokeMethod("progress", mapOf("bytes" to snapshot.bytesProcessed, "state" to snapshot.state))
                when (snapshot.state) {
                    "SUCCEEDED" -> if (snapshot.sha256 != null) success(snapshot) else finishError("INVALID", "Missing digest")
                    "CANCELLED" -> finishError("CANCELLED", "Job cancelled")
                    "FAILED" -> finishError(snapshot.error ?: "FAILED", "Job failed")
                    else -> handler.postDelayed({ poll(ticket, handle, success) }, POLL_MS)
                }
            }, { finishFailure(it) })
        }
    }

    private fun export(result: MethodChannel.Result) {
        if (operation != null || picker != null || exporter != null || cancellation != null) return busy(result)
        if (manifest == null) return result.error("NOT_READY", "Prepare the complete pack first", null)
        val activity = activityBinding?.activity ?: return unavailable(result)
        exporter = result
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "inspection.fieldpack.json")
        }
        try { activity.startActivityForResult(intent, EXPORT) }
        catch (error: Exception) { exporter = null; fail(result, error) }
    }

    private fun completeExport(resultCode: Int, data: Intent?) {
        val result = exporter ?: return
        exporter = null
        val uri = data?.data
        if (resultCode != Activity.RESULT_OK || uri == null) return result.success(false)
        val bytes = manifest ?: return result.error("NOT_READY", "Attempt invalidated", null)
        val ticket = generation
        io.execute {
            try {
                check(current(ticket))
                resolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() }
                    ?: error("No output stream")
                check(current(ticket))
                handler.post {
                    if (current(ticket)) result.success(true)
                    else result.error("CANCELLED", "Export superseded", null)
                }
            } catch (error: Exception) {
                runCatching { android.provider.DocumentsContract.deleteDocument(resolver, uri) }
                handler.post { result.error(if (current(ticket)) "EXPORT_FAILED" else "CANCELLED", "Destination write failed or attempt changed", null) }
            }
        }
    }

    private fun cancel(result: MethodChannel.Result) {
        if (cancellation != null) return busy(result)
        val handle = active
        cancellation = result
        invalidate("CANCELLED")
        if (handle != null) drain(handle)
        else if (!admissionPending && !openingPending) completeCancellation()
    }

    private fun staleAdmission(handle: JobHandle?) {
        if (handle != null && attached) drain(handle)
        else completeCancellation()
    }

    private fun drain(handle: JobHandle) {
        client.cancel(handle) { outcome ->
            outcome.fold({ snapshot ->
                if (snapshot.isTerminal) completeCancellation()
                else handler.postDelayed({ drain(handle) }, POLL_MS)
            }, { error ->
                cancellation?.error((error as? BoundaryException)?.code ?: "LOST_OR_UNKNOWN", "Cleanup outcome unknown", null)
                cancellation = null
            })
        }
    }

    private fun completeCancellation() {
        cancellation?.success(null)
        cancellation = null
    }

    private fun invalidate(code: String) {
        generation++
        openSignal?.cancel()
        manifest = null
        picker?.error(code, "Selection invalidated", null); picker = null
        exporter?.error(code, "Export invalidated", null); exporter = null
        active?.let { client.cancel(it) {} }
        active = null
        operation?.error(code, "Attempt invalidated", null)
        operation = null
    }

    private fun finishError(code: String, message: String) {
        manifest = null
        active = null
        operation?.error(code, message, null)
        operation = null
    }
    private fun finishFailure(error: Throwable) = finishError((error as? BoundaryException)?.code ?: "UNAVAILABLE", "Broker operation failed")
    private fun fail(result: MethodChannel.Result, error: Throwable) = result.error((error as? BoundaryException)?.code ?: "UNAVAILABLE", "Operation unavailable", null)
    private fun current(ticket: Long) = attached && ticket == generation
    private fun entryMap(entry: FieldPackEntry) = mapOf("name" to entry.name, "size" to entry.size, "sha256" to entry.sha256)
    private fun busy(result: MethodChannel.Result) = result.error("BUSY", "Another operation is active", null)
    private fun unavailable(result: MethodChannel.Result) = result.error("NO_ACTIVITY", "An activity is required", null)
    private fun unsupported(result: MethodChannel.Result) = result.error("UNSUPPORTED", "Broker lacks this capability", null)

    private fun detachActivity() {
        activityBinding?.removeActivityResultListener(this)
        activityBinding = null
        picker?.error("ACTIVITY_LOST", "Selection interrupted", null); picker = null
        exporter?.error("ACTIVITY_LOST", "Export interrupted", null); exporter = null
    }
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityBinding = binding
        binding.addActivityResultListener(this)
    }
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) = onAttachedToActivity(binding)
    override fun onDetachedFromActivityForConfigChanges() = detachActivity()
    override fun onDetachedFromActivity() { detachActivity(); invalidate("CANCELLED"); selections.clear() }
    private fun dispose() {
        if (!attached) return
        invalidate("DISPOSED")
        detachActivity()
        attached = false
        completeCancellation()
        selections.clear()
        client.onLoss = null
        client.close()
        io.shutdown()
    }
    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        dispose()
        channel.setMethodCallHandler(null)
    }
    private inner class OnceResult(private val delegate: MethodChannel.Result) : MethodChannel.Result {
        private var completed = false
        private fun deliver(action: () -> Unit) {
            if (completed || !attached) return
            completed = true
            action()
        }
        override fun success(result: Any?) = deliver { delegate.success(result) }
        override fun error(code: String, message: String?, details: Any?) = deliver { delegate.error(code, message, details) }
        override fun notImplemented() = deliver { delegate.notImplemented() }
    }
    private data class SelectedFile(val uri: Uri, val alias: String, val displayName: String)
    companion object {
        private const val PICK = 47001
        private const val EXPORT = 47002
        private const val MAX_ENTRIES = 32
        private const val MAX_BYTES = 134217728L
        private const val POLL_MS = 250L
    }
}
