package dev.boundarylab.consumer

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.WindowInsets
import dev.boundarylab.core.FieldPackCodec
import dev.boundarylab.core.FieldPackEntry
import dev.boundarylab.core.FieldPackManifest
import dev.boundarylab.sdk.BoundaryClient
import dev.boundarylab.sdk.BoundaryException
import dev.boundarylab.sdk.BrokerEndpoint
import dev.boundarylab.sdk.JobHandle
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Published-artifact consumer: document bytes travel to the broker through an owned descriptor. */
class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val documentWorker = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(2), { task -> Thread(task, "consumer-documents") })
    private var client: BoundaryClient? = null
    private lateinit var status: TextView
    private lateinit var generated: Button
    private lateinit var choose: Button
    private lateinit var export: Button
    @Volatile private var generation = 0
    @Volatile private var disposed = false
    private var manifest: ByteArray? = null
    private val byteCount = 65_536L
    private val seed = 17L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            id = R.id.workload_status
            contentDescription = "Workload status"
            text = "Choose a local document to hash. Whole, regular, read-only files only; cloud/pipe providers may be unsupported. No text extraction or preview."
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        generated = Button(this).apply {
            id = R.id.run_workload
            text = "Run generated SHA-256"
            setOnClickListener { startWorkload(null) }
        }
        choose = Button(this).apply {
            id = R.id.choose_document
            text = "Choose document and hash"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, PICK_DOCUMENT)
            }
        }
        export = Button(this).apply {
            id = R.id.export_manifest
            text = "Export FieldPack manifest"
            isEnabled = false
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "fieldpack.json")
                }, EXPORT_MANIFEST)
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(choose)
            addView(generated)
            addView(export)
            addView(status)
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(content)
            setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom)
                insets
            }
            requestApplyInsets()
        })
    }

    @Deprecated("Activity result callback retained for a dependency-free platform sample")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            PICK_DOCUMENT -> startWorkload(uri)
            EXPORT_MANIFEST -> {
                val bytes = manifest ?: return
                val attempt = generation
                setBusy(true)
                documentTask(attempt) {
                    try {
                        if (attempt != generation || disposed) return@documentTask
                        val output = contentResolver.openOutputStream(uri, "wt") ?: error("Unavailable output")
                        output.use { it.write(bytes) }
                        postForAttempt(attempt) { setBusy(false); report("MANIFEST_EXPORTED bytes=${bytes.size}") }
                    } catch (_: Exception) {
                        postForAttempt(attempt) { setBusy(false); report("FAILED: manifest export unavailable") }
                    }
                }
            }
        }
    }

    private fun startWorkload(uri: Uri?) {
        client?.close()
        val attempt = ++generation
        setBusy(true)
        manifest = null
        export.isEnabled = false
        report("CONNECTING")
        val endpoint = BrokerEndpoint(ComponentName(BuildConfig.BROKER_PACKAGE, BuildConfig.BROKER_SERVICE),
            BuildConfig.BIND_PERMISSION)
        val connected = if (endpoint == BrokerEndpoint.DEFAULT) BoundaryClient(this) else BoundaryClient(this, endpoint)
        client = connected
        connected.onLoss = { if (attempt == generation) finishAttempt("LOST: $it") }
        connected.connect { result ->
            if (attempt != generation) return@connect
            result.fold(onSuccess = { caps ->
                report("CONNECTED brokerPid=${caps.brokerPid} clientPid=${android.os.Process.myPid()}")
                if (uri == null) submitGenerated(connected, attempt)
                else submitDocument(connected, uri, attempt)
            }, onFailure = { fail(it) })
        }
    }

    private fun submitGenerated(connected: BoundaryClient, attempt: Int) {
        connected.submitGenerated(seed, byteCount) { submitted ->
            if (attempt == generation) submitted.fold(
                onSuccess = { poll(connected, it, attempt, "generated.bin", true, SystemClock.elapsedRealtime() + 35_000) },
                onFailure = { fail(it) })
        }
    }

    private fun submitDocument(connected: BoundaryClient, uri: Uri, attempt: Int) {
        documentTask(attempt) {
            var owned: ParcelFileDescriptor? = null
            try {
                if (attempt != generation || disposed) return@documentTask
                val alias = documentAlias(uri)
                if (attempt != generation || disposed) return@documentTask
                val descriptor = contentResolver.openFileDescriptor(uri, "r") ?: error("Unavailable descriptor")
                owned = descriptor
                if (attempt != generation || disposed) return@documentTask
                // Ownership transfers immediately, including closed/busy outcomes. Never close it here afterwards.
                owned = null
                connected.submitFile(descriptor, alias) { submitted ->
                    if (attempt == generation && !disposed) submitted.fold(
                        onSuccess = { poll(connected, it, attempt, alias, false, SystemClock.elapsedRealtime() + 35_000) },
                        onFailure = { fail(it) })
                }
            } catch (_: Exception) {
                postForAttempt(attempt) { finishAttempt("FAILED: document unavailable") }
            } finally { runCatching { owned?.close() } }
        }
    }

    private fun documentTask(attempt: Int, task: () -> Unit) {
        try { documentWorker.execute(task) }
        catch (_: RejectedExecutionException) {
            if (attempt == generation && !disposed) finishAttempt("FAILED: BUSY")
        }
    }

    private fun postForAttempt(attempt: Int, action: () -> Unit) {
        main.post {
            if (attempt == generation && !disposed) action()
        }
    }

    private fun documentAlias(uri: Uri): String {
        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "document.bin"
        val candidate = name.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9._-]"), "_").take(64)
        return candidate.takeIf(FieldPackCodec::validateAlias) ?: "document.bin"
    }

    private fun poll(connected: BoundaryClient, handle: JobHandle, attempt: Int, alias: String, probe: Boolean, until: Long) {
        connected.status(handle) { result ->
            if (attempt != generation) return@status
            result.fold(onSuccess = { snapshot ->
                if (snapshot.isTerminal) {
                    val digest = snapshot.sha256
                    if (snapshot.state != "SUCCEEDED" || digest == null) {
                        finishAttempt("FAILED state=${snapshot.state} error=${snapshot.error}")
                    } else if (probe && (snapshot.bytesProcessed != byteCount || digest != generatedDigest())) {
                        finishAttempt("FAILED generated digest mismatch")
                    } else {
                        val value = FieldPackManifest(listOf(FieldPackEntry(alias, snapshot.bytesProcessed, digest)))
                        manifest = FieldPackCodec.encode(value).also { check(FieldPackCodec.decode(it) == value) }
                        val marker = if (probe) "PUBLISHED_ANDROID_OK" else "DOCUMENT_SHA256_OK"
                        finishAttempt("$marker bytes=${snapshot.bytesProcessed} sha256=$digest alias=$alias")
                    }
                } else if (SystemClock.elapsedRealtime() >= until) finishAttempt("TIMEOUT_UNKNOWN")
                else main.postDelayed({ if (attempt == generation) poll(connected, handle, attempt, alias, probe, until) }, 300)
            }, onFailure = { fail(it) })
        }
    }

    private fun generatedDigest(): String = MessageDigest.getInstance("SHA-256")
        .digest(ByteArray(byteCount.toInt()) { ((seed + it) and 255).toByte() })
        .joinToString("") { "%02x".format(it) }
    private fun fail(error: Throwable) = finishAttempt("FAILED: ${(error as? BoundaryException)?.code ?: "UNAVAILABLE"}")
    private fun report(message: String) {
        status.text = message
        Log.i("BoundaryLabConsumer", message)
    }
    private fun setBusy(busy: Boolean) {
        generated.isEnabled = !busy
        choose.isEnabled = !busy
        export.isEnabled = !busy && manifest != null
    }
    private fun finishAttempt(message: String) {
        ++generation
        report(message)
        setBusy(false)
        client?.close()
        client = null
    }
    override fun onStop() {
        ++generation
        main.removeCallbacksAndMessages(null)
        client?.close()
        client = null
        setBusy(false)
        super.onStop()
    }
    override fun onDestroy() {
        disposed = true
        documentWorker.shutdownNow()
        super.onDestroy()
    }
    private companion object {
        const val PICK_DOCUMENT = 1
        const val EXPORT_MANIFEST = 2
    }
}
