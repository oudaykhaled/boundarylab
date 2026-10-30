package dev.boundarylab.broker

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import dev.boundarylab.core.CoreEngine
import dev.boundarylab.core.ErrorCode
import dev.boundarylab.core.JobRequest
import dev.boundarylab.core.JobSnapshot
import dev.boundarylab.core.BoundaryException
import dev.boundarylab.ipc.IBoundaryBroker
import org.json.JSONArray
import org.json.JSONObject

/** A distinct application/process; only bounded admission and metadata work occurs on Binder. */
class BoundaryBrokerService : Service() {
    private val engine = CoreEngine(enableFileOperation = true)
    private val sessionLock = Any()
    private val inputSlots = java.util.concurrent.Semaphore(17)
    private data class Lease(val owner: String, val epoch: String, val token: IBinder, val death: IBinder.DeathRecipient)
    private val leases = mutableMapOf<String, Lease>()

    private fun owner(): String {
        // Capture before any asynchronous work or identity clearing. UID includes Android user.
        val uid = Binder.getCallingUid()
        enforceCallingOrSelfPermission("dev.boundarylab.permission.BIND_BROKER", "Signature permission required")
        if (uid / 100_000 != Process.myUid() / 100_000 ||
            packageManager.checkSignatures(uid, Process.myUid()) != PackageManager.SIGNATURE_MATCH
        ) throw SecurityException("Caller denied")
        return "android:$uid"
    }

    private fun response(action: () -> JSONObject): String = try {
        action().put("ok", true).toString().also { check(it.toByteArray(Charsets.UTF_8).size <= 16_384) }
    } catch (error: BoundaryException) {
        JSONObject().put("ok", false).put("error", error.code.name).toString()
    } catch (_: SecurityException) {
        JSONObject().put("ok", false).put("error", "DENIED").toString()
    } catch (_: IllegalArgumentException) {
        JSONObject().put("ok", false).put("error", "INVALID").toString()
    } catch (_: Exception) {
        JSONObject().put("ok", false).put("error", "IO_ERROR").toString()
    }

    private fun snapshot(job: JobSnapshot) = JSONObject()
        .put("epoch", job.epoch).put("sessionId", job.sessionId).put("jobId", job.jobId)
        .put("requestId", job.requestId).put("state", job.state.name)
        .put("bytesProcessed", job.bytesProcessed).put("sha256", job.digest ?: JSONObject.NULL)
        .put("error", job.errorCode ?: JSONObject.NULL).put("cancelRequested", job.cancelRequested)

    private fun revoke(sessionId: String, expected: Lease) = synchronized(sessionLock) {
        if (leases[sessionId] === expected) {
            leases.remove(sessionId)
            runCatching { expected.token.unlinkToDeath(expected.death, 0) }
            engine.closeSession(expected.owner, sessionId, expected.epoch)
        }
    }

    private val binder = object : IBoundaryBroker.Stub() {
        override fun capabilities(): String = response {
            owner()
            JSONObject().put("epoch", engine.epoch).put("protocolMajor", 1).put("protocolMinor", 0)
                .put("operations", JSONArray(listOf("generated-sha256-v1", "regular-file-sha256-v1")))
                .put("maxBytes", 134_217_728L).put("brokerPid", Process.myPid())
                .put("maxSessions", 16).put("maxQueuedJobs", 16).put("maxOutstandingPerSession", 4)
                .put("workBufferBytes", 65_536).put("maxResponseBytes", 16_384)
                .put("maxMetadataBytes", 4_096).put("maxRetainedEntries", 64)
                .put("terminalTtlMillis", 60_000).put("maxRetainedBytes", 1_048_576)
                .put("maxDeadlineMillis", 120_000).put("minPollMillis", 250)
        }

        override fun openSession(clientToken: IBinder?, protocolMajor: Int): String = response {
            val caller = owner()
            if (protocolMajor != 1) throw BoundaryException(ErrorCode.INCOMPATIBLE)
            require(clientToken != null)
            synchronized(sessionLock) {
                val session = engine.openSession(caller)
                lateinit var lease: Lease
                val death = IBinder.DeathRecipient { revoke(session.sessionId, lease) }
                lease = Lease(caller, session.epoch, clientToken, death)
                try {
                    clientToken.linkToDeath(death, 0)
                    if (!clientToken.isBinderAlive) throw IllegalStateException("Dead token")
                    leases[session.sessionId] = lease
                    JSONObject().put("epoch", session.epoch).put("sessionId", session.sessionId)
                } catch (error: Exception) {
                    runCatching { clientToken.unlinkToDeath(death, 0) }
                    engine.closeSession(caller, session.sessionId, session.epoch)
                    throw error
                }
            }
        }

        override fun submit(epoch: String?, sessionId: String?, requestId: String?, operation: String?,
                            totalBytes: Long, seed: Long, deadlineMillis: Long, alias: String?,
                            input: ParcelFileDescriptor?): String {
            var ownedInput = input
            var inputPermit = false
            return response {
                try {
                    val caller = owner()
                    require(epoch != null && sessionId != null && requestId != null && operation != null)
                    require(listOf(epoch, sessionId, requestId, operation, alias.orEmpty()).sumOf { it.toByteArray(Charsets.UTF_8).size } <= 4_096)
                    engine.validateSession(caller, sessionId, epoch)
                    val source = input?.let {
                        if (!inputSlots.tryAcquire()) throw BoundaryException(ErrorCode.BUSY)
                        inputPermit = true
                        AndroidFileSource.take(it) { inputSlots.release() }
                    }
                    val request = JobRequest(requestId, operation, totalBytes, seed, deadlineMillis, alias)
                    ownedInput = null // Core owns source on EVERY outcome, including duplicate/rejection.
                    inputPermit = false // Source close now owns the permit.
                    snapshot(engine.submit(caller, sessionId, epoch, request, source))
                } finally {
                    try { ownedInput?.close() } finally { if (inputPermit) inputSlots.release() }
                }
            }
        }

        override fun status(epoch: String?, sessionId: String?, jobId: String?): String = response {
            val caller = owner()
            require(epoch != null && sessionId != null && jobId != null)
            snapshot(engine.status(caller, sessionId, epoch, jobId))
        }

        override fun statusByRequest(epoch: String?, sessionId: String?, requestId: String?): String = response {
            val caller = owner()
            require(epoch != null && sessionId != null && requestId != null)
            snapshot(engine.statusByRequest(caller, sessionId, epoch, requestId))
        }

        override fun cancel(epoch: String?, sessionId: String?, jobId: String?): String = response {
            val caller = owner()
            require(epoch != null && sessionId != null && jobId != null)
            snapshot(engine.cancel(caller, sessionId, epoch, jobId))
        }

        override fun closeSession(epoch: String?, sessionId: String?): String = response {
            val caller = owner()
            require(epoch != null && sessionId != null)
            synchronized(sessionLock) {
                engine.closeSession(caller, sessionId, epoch)
                leases.remove(sessionId)?.let { runCatching { it.token.unlinkToDeath(it.death, 0) } }
            }
            JSONObject()
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        synchronized(sessionLock) {
            leases.values.forEach { runCatching { it.token.unlinkToDeath(it.death, 0) } }
            leases.clear()
            engine.close()
        }
        super.onDestroy()
    }
}
