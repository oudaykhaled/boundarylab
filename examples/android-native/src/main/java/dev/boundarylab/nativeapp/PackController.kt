package dev.boundarylab.nativeapp

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.system.Os
import android.provider.OpenableColumns
import dev.boundarylab.sdk.BoundaryClient
import dev.boundarylab.sdk.BoundaryException
import dev.boundarylab.sdk.JobHandle
import dev.boundarylab.sdk.JobSnapshot
import java.util.concurrent.Executors

/** One IO lane bounds provider opens; stale results never enter a later attempt. */
class PackController(context: Context, private val changed: () -> Unit) : AutoCloseable {
    private val resolver = context.contentResolver
    private val client = BoundaryClient(context.applicationContext)
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var revision = 0L
    private var disposed = false
    private var signal: CancellationSignal? = null
    private var active: JobHandle? = null
    private var attempt: PackAttempt? = null
    private var inputs = emptyList<Uri>()
    private var names = emptyList<String>()
    private val events = ArrayDeque<String>()
    private var operations = emptySet<String>()
    private var total = 0L
    private var cancelRequested = false
    var busy = false; private set
    var message = "Choose attachments to begin."; private set
    var generatedResult = "No sample run yet."; private set
    val aliases: List<String> get() = inputs.indices.map(PackAttempt::alias)
    val exportReady: Boolean get() = !busy && attempt?.complete == true
    val exportToken: Long get() = revision
    val displayNames: List<String> get() = names
    val history: String get() = if (events.isEmpty()) "No completed attempts in this session." else events.joinToString("\n\n")
    val completed: Int get() = attempt?.count ?: 0

    init {
        client.onLoss = { fail("Connection lost. The outcome is unknown; this attempt cannot be exported. Start a new attempt.") }
    }

    fun select(uris: List<Uri>) {
        if (busy) return
        invalidate()
        inputs = if (uris.size in 1..32 && uris.distinct().size == uris.size) uris else emptyList()
        names = inputs.indices.map { "Selected attachment ${it + 1}" }
        message = if (inputs.isEmpty()) "Choose 1–32 distinct attachments." else "${inputs.size} attachments selected. Ready to prepare."
        changed()
    }

    fun prepare() {
        if (busy || inputs.isEmpty()) return
        invalidate()
        cancelRequested = false
        attempt = PackAttempt(aliases)
        total = 0
        busy = true
        val token = revision
        message = "Connecting…"
        changed()
        connect(token, "regular-file-sha256-v1") { process(token, 0) }
    }

    private fun connect(token: Long, operation: String, ready: () -> Unit) {
        client.connect { result ->
            if (!current(token)) return@connect
            if (cancelRequested) { finishCancellation(); return@connect }
            result.fold(onSuccess = {
                operations = it.operations
                if (operation !in operations) fail("This service does not support the selected operation.") else ready()
            }, onFailure = { fail(readable(it)) })
        }
    }

    private fun process(token: Long, index: Int) {
        if (!current(token)) return
        if (index == inputs.size) {
            busy = false
            active = null
            message = "Ready to export. All ${inputs.size} attachments prepared."
            remember("Pack ready · ${inputs.size} attachments")
            changed()
            return
        }
        message = "Preparing ${index + 1} of ${inputs.size} · ${PackAttempt.alias(index)}"
        changed()
        val openSignal = CancellationSignal().also { signal = it }
        val uri = inputs[index]
        io.execute {
            val opened = runCatching {
                val descriptor = resolver.openFileDescriptor(uri, "r", openSignal)
                    ?: error("Provider returned no file")
                try {
                    val size = Os.fstat(descriptor.fileDescriptor).st_size
                    require(size in 0..PackAttempt.MAX_BYTES)
                    val name = runCatching {
                        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null, openSignal)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0)?.filter { !it.isISOControl() }?.take(200) else null
                        }
                    }.getOrNull() ?: "Selected attachment ${index + 1}"
                    Triple(descriptor, size, name)
                } catch (error: Throwable) { descriptor.close(); throw error }
            }
            main.post {
                if (!current(token)) { opened.getOrNull()?.first?.close(); return@post }
                signal = null
                if (cancelRequested) {
                    opened.getOrNull()?.first?.close()
                    finishCancellation()
                    return@post
                }
                opened.fold(onSuccess = { (descriptor, size, name) ->
                    names = names.toMutableList().also { it[index] = name }
                    changed()
                    if (total > PackAttempt.MAX_BYTES - size) {
                        descriptor.close()
                        fail("Attachments exceed the 128 MiB pack limit.")
                    } else {
                        total += size
                        client.submitFile(descriptor, PackAttempt.alias(index)) { result ->
                            admitted(token, result) { snapshot -> completeFile(token, index, snapshot) }
                        }
                    }
                }, onFailure = { fail("Could not open this attachment. Choose a supported local regular file and try again.") })
            }
        }
    }

    private fun completeFile(token: Long, index: Int, snapshot: JobSnapshot) {
        try {
            attempt!!.succeed(PackAttempt.alias(index), snapshot.bytesProcessed, snapshot.sha256 ?: error("Missing digest"))
            process(token, index + 1)
        } catch (error: Exception) { fail("The attachment result could not complete this pack. Start a new attempt.") }
    }

    private fun admitted(token: Long, result: Result<JobHandle>, success: (JobSnapshot) -> Unit) {
        result.fold(onSuccess = { handle ->
            if (!current(token)) { client.cancel(handle) {}; return@fold }
            active = handle
            if (cancelRequested) client.cancel(handle) {}
            poll(token, handle, success)
        }, onFailure = { if (current(token)) fail(readable(it)) })
    }

    private fun poll(token: Long, handle: JobHandle, success: (JobSnapshot) -> Unit) {
        if (!current(token)) return
        client.status(handle) { result ->
            if (!current(token)) return@status
            result.fold(onSuccess = { snapshot ->
                if (cancelRequested && snapshot.isTerminal) {
                    finishCancellation()
                    return@fold
                }
                when (snapshot.state) {
                    "SUCCEEDED" -> { active = null; success(snapshot) }
                    "FAILED", "CANCELLED" -> fail(readable(BoundaryException(snapshot.error ?: snapshot.state)))
                    else -> main.postDelayed({ poll(token, handle, success) }, 300)
                }
            }, onFailure = { fail(readable(it)) })
        }
    }

    fun runGenerated() {
        if (busy) return
        invalidate()
        busy = true
        cancelRequested = false
        val token = revision
        message = "Running the generated sample…"
        changed()
        connect(token, "generated-sha256-v1") {
            client.submitGenerated(seed = 0, totalBytes = 65_537) { result ->
                admitted(token, result) { snapshot ->
                    busy = false
                    generatedResult = "65,537 bytes · SHA-256\n${snapshot.sha256}"
                    message = "Sample completed through the connected service."
                    remember("Generated sample completed · 65,537 bytes")
                    changed()
                }
            }
        }
    }

    fun cancel() {
        if (!busy || cancelRequested) return
        cancelRequested = true
        attempt?.invalidate()
        signal?.cancel()
        active?.let { client.cancel(it) {} }
        message = "Cancellation requested. Waiting for the current read to finish…"
        changed()
    }

    private fun finishCancellation() {
        invalidate()
        busy = false
        message = "Cancelled. Start a new attempt to prepare all attachments again."
        remember("Preparation cancelled · no complete manifest")
        changed()
    }

    fun manifest(): ByteArray = checkNotNull(attempt).manifest()
    fun exportMessage(text: String) { message = text; changed() }
    private fun remember(text: String) {
        if (events.size == 10) events.removeFirst()
        events.addLast(text)
    }
    private fun current(token: Long) = !disposed && token == revision
    private fun invalidate() {
        revision++
        signal?.cancel()
        signal = null
        attempt?.invalidate()
        attempt = null
        active = null
    }
    private fun fail(text: String) {
        if (disposed) return
        invalidate()
        busy = false
        message = text
        remember(text)
        changed()
    }
    private fun readable(error: Throwable): String = when ((error as? BoundaryException)?.code) {
        "BUSY" -> "The service is busy. Start a new attempt when it is available."
        "LOST_OR_UNKNOWN", "STALE_EPOCH" -> "Connection lost. The outcome is unknown; this attempt cannot be exported. Start a new attempt."
        "CANCELLED" -> "Cancelled. No complete pack is available."
        "UNSUPPORTED", "INVALID" -> "This attachment is unsupported. Use local, regular files up to 128 MiB in total."
        "DENIED" -> "Access was denied. Check the installed service and selected attachments."
        "SOURCE_CHANGED" -> "An attachment changed while reading. Keep files stable and start a new attempt."
        else -> "Preparation failed. Check the service and attachments, then start a new attempt."
    }
    override fun close() {
        disposed = true
        invalidate()
        // Keep posted descriptor deliveries alive: their stale branch closes the handle.
        // Pending poll callbacks observe disposed/revision and return without IPC.
        client.close()
        io.shutdown()
    }
}
