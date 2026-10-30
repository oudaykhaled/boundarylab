package dev.boundarylab.sdk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SystemClock
import dev.boundarylab.ipc.IBoundaryBroker
import dev.boundarylab.core.FieldPackCodec
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One foreground-demand connection/session. IPC is serialized off the main thread;
 * callbacks are delivered on main. Closing the client ends demand and never replays work.
 */
class BoundaryClient(context: Context) : AutoCloseable {
    private val context = context.applicationContext
    private val brokerComponent = ComponentName("dev.boundarylab.broker", "dev.boundarylab.broker.BoundaryBrokerService")
    private val main = Handler(Looper.getMainLooper())
    private val worker = ScheduledThreadPoolExecutor(1) { task -> Thread(task, "boundary-sdk") }
        .apply { removeOnCancelPolicy = true }
    private val pending = AtomicInteger()
    private var remote: IBoundaryBroker? = null
    private var connection: ServiceConnection? = null
    private var death: IBinder.DeathRecipient? = null
    private var token: IBinder? = null
    private var sessionId: String? = null
    private var capabilities: BrokerCapabilities? = null
    private val connectCallbacks = mutableListOf<(Result<BrokerCapabilities>) -> Unit>()
    private val lastPoll = mutableMapOf<String, Long>()
    private val polling = mutableSetOf<String>()
    private var retries = 0
    @Volatile private var demand = false
    @Volatile private var disposed = false
    @Volatile private var generation = 0L
    @Volatile var state: ConnectionState = ConnectionState.UNBOUND
        private set
    @Volatile var onLoss: ((String) -> Unit)? = null

    fun connect(callback: (Result<BrokerCapabilities>) -> Unit) {
        if (!admit(callback)) return
        demand = true
        val completed = AtomicBoolean(false)
        lateinit var timeout: Runnable
        val completion: (Result<BrokerCapabilities>) -> Unit = {
            if (completed.compareAndSet(false, true)) {
                main.removeCallbacks(timeout)
                callback(it)
            }
        }
        timeout = Runnable { completion(Result.failure(BoundaryException(if (disposed) "CLOSED" else "TIMEOUT_UNKNOWN"))) }
        main.postDelayed(timeout, 5_000)
        execute(onRejected = {
            pending.decrementAndGet()
            main.removeCallbacks(timeout)
            deliver(completion, Result.failure(BoundaryException("CLOSED")))
        }) {
            try {
                if (disposed) {
                    deliver(completion, Result.failure(BoundaryException("CLOSED"))); return@execute
                }
                capabilities?.takeIf { state == ConnectionState.READY }?.let {
                    deliverForGeneration(generation, completion, Result.success(it)); return@execute
                }
                if (connectCallbacks.size >= 16) {
                    deliver(completion, Result.failure(BoundaryException("BUSY"))); return@execute
                }
                connectCallbacks += completion
                if (state != ConnectionState.BINDING && state != ConnectionState.RECOVERING) {
                    retries = 0
                    bind()
                }
            } finally { pending.decrementAndGet() }
        }
    }

    private fun bind() {
        if (!demand || disposed) return
        val expected = ++generation
        state = ConnectionState.BINDING
        val next = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                dispatch {
                    if (expected == generation && demand) {
                        if (name != brokerComponent) terminalConnection("DENIED")
                        else handshake(expected, binder)
                    }
                }
            }
            override fun onServiceDisconnected(name: ComponentName) = lost(expected)
            override fun onBindingDied(name: ComponentName) = lost(expected)
            override fun onNullBinding(name: ComponentName) {
                dispatch { if (expected == generation) terminalConnection("UNAVAILABLE") }
            }
        }
        connection = next
        try {
            verifyBrokerIdentity()
            val accepted = context.bindService(Intent().setComponent(brokerComponent), next, Context.BIND_AUTO_CREATE)
            if (!accepted) terminalConnection("UNAVAILABLE")
        } catch (_: SecurityException) {
            terminalConnection("DENIED")
        } catch (_: Exception) {
            terminalConnection("UNAVAILABLE")
        }
    }

    private fun handshake(expected: Long, binder: IBinder) {
        try {
            verifyBrokerIdentity()
            val service = IBoundaryBroker.Stub.asInterface(binder)
            val caps = decode(service.capabilities())
            if (caps.getInt("protocolMajor") != 1) throw BoundaryException("INCOMPATIBLE")
            val operations = caps.getJSONArray("operations")
            val accepted = BrokerCapabilities(caps.getString("epoch"), 1, caps.getInt("protocolMinor"),
                (0 until operations.length()).map { operations.getString(it) }.toSet(),
                caps.getLong("maxBytes"), caps.getInt("brokerPid"))
            val deathRecipient = IBinder.DeathRecipient { lost(expected) }
            binder.linkToDeath(deathRecipient, 0)
            remote = service
            death = deathRecipient
            token = Binder()
            val session = decode(service.openSession(token, 1))
            if (session.getString("epoch") != accepted.epoch) throw BoundaryException("LOST_OR_UNKNOWN")
            sessionId = session.getString("sessionId")
            capabilities = accepted
            state = ConnectionState.READY
            retries = 0
            val callbacks = connectCallbacks.toList()
            connectCallbacks.clear()
            callbacks.forEach { deliverForGeneration(expected, it, Result.success(accepted)) }
        } catch (error: BoundaryException) {
            if (error.code in setOf("DENIED", "INCOMPATIBLE")) terminalConnection(error.code)
            else lose(expected)
        } catch (_: SecurityException) {
            terminalConnection("DENIED")
        } catch (_: Exception) {
            lose(expected)
        }
    }

    private fun verifyBrokerIdentity() {
        val service = context.packageManager.getServiceInfo(brokerComponent, 0)
        if (!service.exported || service.permission != "dev.boundarylab.permission.BIND_BROKER" ||
            context.packageManager.checkSignatures(context.applicationInfo.uid, service.applicationInfo.uid) !=
            PackageManager.SIGNATURE_MATCH) throw SecurityException("Broker signature denied")
    }

    private fun execute(onRejected: () -> Unit = {}, action: () -> Unit) {
        try { worker.execute(action) }
        catch (_: java.util.concurrent.RejectedExecutionException) { onRejected() }
    }

    private fun dispatch(action: () -> Unit) {
        if (!disposed && !worker.isShutdown) {
            execute(action = action)
        }
    }

    private fun lost(expected: Long) { dispatch { lose(expected) } }

    private fun lose(expected: Long) {
        if (expected != generation || disposed) return
        ++generation
        clearConnection()
        state = ConnectionState.RECOVERING
        // Enqueue before any retry can post a READY callback. Main FIFO preserves loss
        // before recovery even if main is delayed; never discard the sole loss signal.
        main.post { if (!disposed) onLoss?.invoke("LOST_OR_UNKNOWN") }
        if (demand && retries < 3) {
            val delay = 250L shl retries++
            worker.schedule({ if (state == ConnectionState.RECOVERING && demand) bind() }, delay, TimeUnit.MILLISECONDS)
        } else terminalConnection("UNAVAILABLE")
    }

    private fun terminalConnection(code: String) {
        ++generation
        clearConnection()
        state = when (code) {
            "DENIED" -> ConnectionState.DENIED
            "INCOMPATIBLE" -> ConnectionState.INCOMPATIBLE
            else -> ConnectionState.UNAVAILABLE
        }
        val callbacks = connectCallbacks.toList()
        connectCallbacks.clear()
        callbacks.forEach { deliver(it, Result.failure(BoundaryException(code))) }
    }

    private fun clearConnection() {
        death?.let { recipient -> runCatching { remote?.asBinder()?.unlinkToDeath(recipient, 0) } }
        connection?.let { runCatching { context.unbindService(it) } }
        connection = null
        remote = null
        death = null
        token = null
        sessionId = null
        capabilities = null
        polling.clear()
        lastPoll.clear()
    }

    fun submitGenerated(seed: Long, totalBytes: Long, requestId: String = UUID.randomUUID().toString(),
                        deadlineMs: Long = 30_000, callback: (Result<JobHandle>) -> Unit) {
        submit("generated-sha256-v1", totalBytes, seed, null, null, requestId, deadlineMs, callback)
    }

    /** Takes ownership immediately. Caller must not close/reuse the submitted local handle. */
    fun submitFile(pfd: ParcelFileDescriptor, alias: String, requestId: String = UUID.randomUUID().toString(),
                   deadlineMs: Long = 30_000, callback: (Result<JobHandle>) -> Unit) {
        submit("regular-file-sha256-v1", 0, 0, alias, pfd, requestId, deadlineMs, callback)
    }

    private fun submit(operation: String, totalBytes: Long, seed: Long, alias: String?,
                       pfd: ParcelFileDescriptor?, requestId: String, deadlineMs: Long,
                       callback: (Result<JobHandle>) -> Unit) {
        request(callback, cleanup = { pfd?.close() }) { service, caps, session, current ->
            validateUuid(requestId)
            if (deadlineMs !in 1L..120_000L) throw BoundaryException("INVALID")
            if (alias != null && (alias.length !in 1..64 || !FieldPackCodec.validateAlias(alias)))
                throw BoundaryException("INVALID")
            val declaredBytes = pfd?.statSize ?: totalBytes
            if (declaredBytes !in 0L..134_217_728L) throw BoundaryException("INVALID")
            if (operation !in caps.operations) throw BoundaryException("UNSUPPORTED")
            val result = decode(service.submit(caps.epoch, session, requestId, operation, declaredBytes,
                seed, deadlineMs, alias, pfd))
            handle(result, current)
        }
    }

    fun statusByRequest(requestId: String, callback: (Result<JobHandle>) -> Unit) {
        poll(requestId, callback) { service, caps, session, current ->
            handle(decode(service.statusByRequest(caps.epoch, session, requestId)), current)
        }
    }

    /** One poll per request at a time, >=250 ms between IPC starts, including request lookup. */
    fun status(handle: JobHandle, callback: (Result<JobSnapshot>) -> Unit) {
        poll(handle.requestId, callback, expectedGeneration = handle.generation) { service, caps, session, current ->
            checkHandle(handle, caps, session, current)
            snapshot(decode(service.status(caps.epoch, session, handle.jobId)))
        }
    }

    private fun <T> poll(requestId: String, callback: (Result<T>) -> Unit,
                         expectedGeneration: Long = generation,
                         action: (IBoundaryBroker, BrokerCapabilities, String, Long) -> T) {
        val expected = expectedGeneration
        if (!admit(callback)) return
        val completed = AtomicBoolean(false)
        lateinit var timeout: Runnable
        val completion: (Result<T>) -> Unit = {
            if (completed.compareAndSet(false, true)) {
                main.removeCallbacks(timeout)
                callback(it)
            }
        }
        timeout = Runnable {
            completion(Result.failure(BoundaryException(when {
                disposed -> "CLOSED"
                expected != generation -> "LOST_OR_UNKNOWN"
                else -> "TIMEOUT_UNKNOWN"
            })))
        }
        main.postDelayed(timeout, 5_000)
        val reject = {
            pending.decrementAndGet()
            deliver(completion, Result.failure(BoundaryException("CLOSED")))
        }
        execute(onRejected = reject) {
            if (expected != generation || disposed) {
                pending.decrementAndGet()
                deliver(completion, Result.failure(BoundaryException(if (disposed) "CLOSED" else "LOST_OR_UNKNOWN")))
                return@execute
            }
            try { validateUuid(requestId) } catch (error: BoundaryException) {
                pending.decrementAndGet()
                deliver(completion, Result.failure(error)); return@execute
            }
            if (!polling.add(requestId)) {
                pending.decrementAndGet()
                deliver(completion, Result.failure(BoundaryException("BUSY"))); return@execute
            }
            val elapsed = SystemClock.elapsedRealtime()
            // Entries older than the spacing interval need no throttle memory. Never evict
            // recent entries to make room: doing that would allow a rapid revisit to bypass it.
            lastPoll.entries.removeAll { elapsed - it.value >= 250 && it.key !in polling }
            if (requestId !in lastPoll && lastPoll.size >= 64) {
                polling.remove(requestId)
                pending.decrementAndGet()
                deliver(completion, Result.failure(BoundaryException("BUSY"))); return@execute
            }
            val previous = lastPoll.getOrPut(requestId) { elapsed - 250 }
            val wait = (previous + 250 - elapsed).coerceAtLeast(0)
            try {
                worker.schedule({
                    request(completion, cleanup = { if (expected == generation) polling.remove(requestId) },
                        alreadyAdmitted = true, expectedGeneration = expected) { service, caps, session, current ->
                        lastPoll[requestId] = SystemClock.elapsedRealtime()
                        action(service, caps, session, current)
                    }
                }, wait, TimeUnit.MILLISECONDS)
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                polling.remove(requestId)
                reject()
            }
        }
    }

    fun cancel(handle: JobHandle, callback: (Result<JobSnapshot>) -> Unit) {
        request(callback, expectedGeneration = handle.generation) { service, caps, session, current ->
            checkHandle(handle, caps, session, current)
            snapshot(decode(service.cancel(caps.epoch, session, handle.jobId)))
        }
    }

    private fun validateUuid(value: String) {
        // Bound before parsing/encoding so malformed caller data never reaches a Binder parcel.
        if (value.length != 36 || runCatching { UUID.fromString(value).toString() != value }.getOrDefault(true))
            throw BoundaryException("INVALID")
    }

    private fun checkHandle(handle: JobHandle, caps: BrokerCapabilities, session: String, current: Long) {
        validateUuid(handle.epoch)
        validateUuid(handle.sessionId)
        validateUuid(handle.jobId)
        validateUuid(handle.requestId)
        if (handle.generation != current || handle.epoch != caps.epoch || handle.sessionId != session)
            throw BoundaryException("LOST_OR_UNKNOWN")
    }

    private fun <T> admit(callback: (Result<T>) -> Unit): Boolean {
        if (disposed) {
            deliver(callback, Result.failure(BoundaryException("CLOSED"))); return false
        }
        if (pending.incrementAndGet() > 32) {
            pending.decrementAndGet()
            deliver(callback, Result.failure(BoundaryException("BUSY"))); return false
        }
        return true
    }

    private fun <T> request(callback: (Result<T>) -> Unit, cleanup: () -> Unit = {},
                            alreadyAdmitted: Boolean = false, expectedGeneration: Long = generation,
                            action: (IBoundaryBroker, BrokerCapabilities, String, Long) -> T) {
        val expected = expectedGeneration
        if (!alreadyAdmitted && !admit(callback)) {
            runCatching(cleanup)
            return
        }
        val completed = AtomicBoolean(false)
        val timeout = Runnable {
            if (completed.compareAndSet(false, true)) callback(Result.failure(BoundaryException(when {
                disposed -> "CLOSED"
                expected != generation -> "LOST_OR_UNKNOWN"
                else -> "TIMEOUT_UNKNOWN"
            })))
        }
        main.postDelayed(timeout, 5_000)
        execute(onRejected = {
            main.removeCallbacks(timeout)
            runCatching(cleanup)
            pending.decrementAndGet()
            if (completed.compareAndSet(false, true))
                deliver(callback, Result.failure(BoundaryException("CLOSED")))
        }) {
            val result = try {
                if (disposed) throw BoundaryException("CLOSED")
                if (expected != generation) throw BoundaryException("LOST_OR_UNKNOWN")
                val service = remote ?: throw BoundaryException("UNAVAILABLE")
                val caps = capabilities ?: throw BoundaryException("UNAVAILABLE")
                val session = sessionId ?: throw BoundaryException("UNAVAILABLE")
                Result.success(action(service, caps, session, expected))
            } catch (_: RemoteException) {
                lose(expected)
                Result.failure(BoundaryException("LOST_OR_UNKNOWN"))
            } catch (_: SecurityException) {
                Result.failure(BoundaryException("DENIED"))
            } catch (error: Exception) {
                Result.failure(error)
            } finally {
                runCatching(cleanup)
                pending.decrementAndGet()
            }
            main.removeCallbacks(timeout)
            if (completed.compareAndSet(false, true)) deliverForGeneration(expected, callback, result)
        }
    }

    private fun decode(raw: String): JSONObject {
        if (raw.toByteArray(Charsets.UTF_8).size > 16_384) throw BoundaryException("INVALID")
        val value = JSONObject(raw)
        if (!value.getBoolean("ok")) throw BoundaryException(value.getString("error"))
        return value
    }

    private fun handle(value: JSONObject, current: Long) = JobHandle(value.getString("epoch"),
        value.getString("sessionId"), value.getString("jobId"), value.getString("requestId"), current)

    private fun snapshot(value: JSONObject) = JobSnapshot(value.getString("state"), value.getLong("bytesProcessed"),
        if (value.isNull("sha256")) null else value.getString("sha256"),
        if (value.isNull("error")) null else value.getString("error"), value.getBoolean("cancelRequested"))

    private fun <T> deliver(callback: (Result<T>) -> Unit, result: Result<T>) { main.post { callback(result) } }

    private fun <T> deliverForGeneration(expected: Long, callback: (Result<T>) -> Unit, result: Result<T>) {
        main.post {
            callback(when {
                disposed -> Result.failure(BoundaryException("CLOSED"))
                expected != generation -> Result.failure(BoundaryException("LOST_OR_UNKNOWN"))
                else -> result
            })
        }
    }

    @Synchronized
    override fun close() {
        if (disposed) return
        disposed = true
        demand = false
        ++generation
        execute {
            runCatching {
                sessionId?.let { session -> capabilities?.let { caps -> remote?.closeSession(caps.epoch, session) } }
            }
            clearConnection()
            state = ConnectionState.CLOSED
            connectCallbacks.forEach { deliver(it, Result.failure(BoundaryException("CLOSED"))) }
            connectCallbacks.clear()
            worker.shutdown()
        }
    }
}
