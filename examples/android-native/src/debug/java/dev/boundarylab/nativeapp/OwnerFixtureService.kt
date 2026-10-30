package dev.boundarylab.nativeapp

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import dev.boundarylab.sdk.BoundaryClient
import dev.boundarylab.sdk.JobHandle

/** Debug-only cross-UID denial fixture. Signature-protected and absent from release. */
class OwnerFixtureService : Service() {
    private lateinit var client: BoundaryClient
    private var handle: JobHandle? = null
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            1 -> prepare(message.replyTo)
            2 -> { client.close(); handle = null; reply(message.replyTo, Bundle().apply { putBoolean("ok", true) }); stopSelf() }
            3 -> android.os.Process.killProcess(android.os.Process.myPid())
        }
        true
    })

    override fun onCreate() { super.onCreate(); client = BoundaryClient(this) }
    override fun onBind(intent: Intent) = messenger.binder
    private fun prepare(destination: Messenger?) {
        handle?.let { return sendHandle(destination, it) }
        client.connect { connection ->
            connection.fold(onSuccess = {
                client.submitGenerated(seed = 0, totalBytes = 0) { result ->
                    result.fold(onSuccess = { handle = it; sendHandle(destination, it) },
                        onFailure = { sendError(destination) })
                }
            }, onFailure = { sendError(destination) })
        }
    }
    private fun sendHandle(destination: Messenger?, value: JobHandle) = reply(destination, Bundle().apply {
        putBoolean("ok", true)
        putString("epoch", value.epoch); putString("sessionId", value.sessionId)
        putString("jobId", value.jobId); putString("requestId", value.requestId)
    })
    private fun sendError(destination: Messenger?) = reply(destination, Bundle().apply { putBoolean("ok", false) })
    private fun reply(destination: Messenger?, value: Bundle) {
        runCatching { destination?.send(Message.obtain().apply { what = 1; data = value }) }
    }
    override fun onDestroy() { client.close(); super.onDestroy() }
}
