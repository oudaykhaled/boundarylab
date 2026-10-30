package dev.boundarylab.core

import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** One state monitor and one physical worker. Blocking source work never holds the monitor. */
class CoreEngine(
    private val enableFileOperation: Boolean = false,
    private val autoStart: Boolean = true,
    private val clock: MonotonicClock = MonotonicClock.SYSTEM,
    private val limits: EngineLimits = EngineLimits(),
) : AutoCloseable {
    val epoch: String = UUID.randomUUID().toString()
    private val monitor = Object()
    private val sessions = linkedMapOf<String, Session>()
    private val jobs = linkedMapOf<String, Job>()
    private val ready = ArrayDeque<String>()
    private val retained = linkedMapOf<String, Job>()
    private var retainedBytes = 0L
    private var ownedSources = 0
    private var running: Job? = null
    private var closed = false
    private val buffer = ByteArray(limits.bufferBytes)

    private class Session(val owner: String) {
        val queue = ArrayDeque<Job>()
        val requests = mutableMapOf<String, Job>()
    }

    private class OwnedSource(val delegate: PositionalSource) {
        private val closed = AtomicBoolean()
        fun close(): Boolean? {
            if (!closed.compareAndSet(false, true)) return null
            return try { delegate.close(); true } catch (_: Exception) { false }
        }
    }

    private class Job(
        val id: String,
        val sessionId: String,
        val request: JobRequest,
        val admittedAt: Long,
        val accountedBytes: Long,
        val source: OwnedSource?,
    ) {
        var state = JobState.ACCEPTED
        var cancelRequested = false
        var processed = 0L
        var digest: String? = null
        var error: ErrorCode? = null
        var finishedAt = 0L
        val terminal: Boolean get() = state != JobState.ACCEPTED && state != JobState.RUNNING
    }

    init {
        if (autoStart) thread(name = "boundarylab-worker", isDaemon = true) {
            while (true) {
                if (runNext()) continue
                synchronized(monitor) {
                    if (closed && running == null) return@thread
                    if (ready.isEmpty() || running != null) monitor.wait()
                }
            }
        }
    }

    fun capabilities(): Capabilities = Capabilities(
        epoch,
        if (enableFileOperation) setOf(GENERATED_OPERATION, FILE_OPERATION) else setOf(GENERATED_OPERATION),
        limits,
    )

    fun openSession(owner: String): SessionHandle = synchronized(monitor) {
        ensureOpen()
        prune()
        if (owner.isBlank() || owner.length > 256) fail(ErrorCode.INVALID)
        if (sessions.size >= limits.maxSessions) fail(ErrorCode.BUSY)
        val id = UUID.randomUUID().toString()
        sessions[id] = Session(owner)
        SessionHandle(epoch, id)
    }

    /** Authorizes a transport request before the adapter opens any local input. */
    fun validateSession(owner: String, sessionId: String, epoch: String) {
        synchronized(monitor) { authorize(owner, sessionId, epoch) }
    }

    /** This call consumes [source] even when it throws or returns an existing job. */
    fun submit(
        owner: String, sessionId: String, epoch: String,
        request: JobRequest, source: PositionalSource? = null,
    ): JobSnapshot {
        val owned = source?.let(::OwnedSource)
        var transferred = false
        try {
            return synchronized(monitor) {
                val session = authorize(owner, sessionId, epoch)
                validate(request, source != null)
                session.requests[request.requestId]?.let {
                    if (it.request != request) fail(ErrorCode.CONFLICT)
                    return@synchronized snapshot(it)
                }
                if (source != null && source.size != request.totalBytes) fail(ErrorCode.INVALID)
                val queued = sessions.values.sumOf { it.queue.size }
                val outstanding = session.requests.values.count { !it.terminal }
                if (queued >= limits.maxQueuedJobs || outstanding >= limits.maxOutstandingPerSession ||
                    (owned != null && ownedSources >= limits.maxQueuedJobs + 1)) fail(ErrorCode.BUSY)
                val bytes = accountedBytes(request)
                // Each possible result must itself fit; count and bytes eviction happens at commit.
                if (bytes > limits.maxRetainedBytes) fail(ErrorCode.INVALID)
                val job = Job(UUID.randomUUID().toString(), sessionId, request, clock.nowMillis(), bytes, owned)
                jobs[job.id] = job
                session.requests[request.requestId] = job
                if (session.queue.isEmpty()) ready.addLast(sessionId)
                session.queue.addLast(job)
                if (owned != null) ownedSources++
                transferred = true
                monitor.notifyAll()
                snapshot(job)
            }
        } catch (e: BoundaryException) {
            throw e
        } catch (_: Exception) {
            throw BoundaryException(ErrorCode.IO_ERROR)
        } finally {
            if (!transferred) owned?.close()
        }
    }

    fun status(owner: String, sessionId: String, epoch: String, jobId: String): JobSnapshot =
        synchronized(monitor) { snapshot(findJob(owner, sessionId, epoch, jobId)) }

    fun statusByRequest(owner: String, sessionId: String, epoch: String, requestId: String): JobSnapshot =
        synchronized(monitor) {
            val session = authorize(owner, sessionId, epoch)
            if (!canonicalUuid(requestId)) fail(ErrorCode.INVALID)
            snapshot(session.requests[requestId] ?: fail(ErrorCode.EXPIRED_OR_UNKNOWN))
        }

    fun cancel(owner: String, sessionId: String, epoch: String, jobId: String): JobSnapshot {
        var cleanup: Job? = null
        val result = synchronized(monitor) {
            val job = findJob(owner, sessionId, epoch, jobId)
            if (!job.terminal) {
                job.cancelRequested = true
                if (job.state == JobState.ACCEPTED && running !== job) {
                    removeQueued(job)
                    commit(job, JobState.CANCELLED)
                    cleanup = job
                }
            }
            snapshot(job)
        }
        cleanup?.let(::closeSource)
        return result
    }

    fun closeSession(owner: String, sessionId: String, epoch: String) {
        val cleanup = synchronized(monitor) {
            authorize(owner, sessionId, epoch)
            revokeSession(sessionId)
        }
        cleanup.forEach(::closeSource)
    }

    /** Nonblocking shutdown request. A blocked source retains its worker until it exits. */
    override fun close() {
        val cleanup = synchronized(monitor) {
            if (closed) return
            closed = true
            val result = sessions.keys.toList().flatMap(::revokeSession)
            monitor.notifyAll()
            result
        }
        cleanup.forEach(::closeSource)
    }

    fun metrics(): EngineMetrics = synchronized(monitor) {
        prune()
        EngineMetrics(sessions.size, sessions.values.sumOf { it.queue.size },
            if (running == null) 0 else 1, retained.size, retainedBytes, ownedSources)
    }

    /** Executes at most one job, synchronously. A concurrent worker makes this return false. */
    fun runNext(): Boolean {
        val job = synchronized(monitor) {
            prune()
            if (closed || running != null || ready.isEmpty()) return false
            val sessionId = ready.removeFirst()
            val session = sessions.getValue(sessionId)
            val selected = session.queue.removeFirst()
            if (session.queue.isNotEmpty()) ready.addLast(sessionId)
            running = selected
            // Expired queued work must never become RUNNING or read its source.
            if (!expired(selected)) selected.state = JobState.RUNNING
            selected
        }
        var digest: String? = null
        var error: ErrorCode? = ErrorCode.IO_ERROR
        try {
            checkpoint(job)
            digest = hash(job)
            error = null
        } catch (e: BoundaryException) {
            error = e.code
        } catch (_: Exception) {
            error = ErrorCode.IO_ERROR
        } finally {
            if (!closeSource(job) && error == null) error = ErrorCode.IO_ERROR
            synchronized(monitor) {
                when {
                    job.cancelRequested -> commit(job, JobState.CANCELLED)
                    expired(job) -> commit(job, JobState.FAILED, ErrorCode.DEADLINE_EXCEEDED)
                    error != null -> commit(job, JobState.FAILED, error)
                    else -> commit(job, JobState.SUCCEEDED, digest = digest)
                }
                running = null
                monitor.notifyAll()
            }
        }
        return true
    }

    private fun hash(job: Job): String {
        val request = job.request
        val source = job.source?.delegate
        if (source != null && source.size != request.totalBytes) fail(ErrorCode.SOURCE_CHANGED)
        val digest = MessageDigest.getInstance("SHA-256")
        var position = 0L
        while (position < request.totalBytes) {
            checkpoint(job)
            val count = minOf(buffer.size.toLong(), request.totalBytes - position).toInt()
            val consumed = if (source == null) {
                for (i in 0 until count) buffer[i] = (request.seed + position + i).toByte()
                count
            } else {
                val read = source.readAt(position, buffer, 0, count)
                if (read !in 1..count) fail(ErrorCode.IO_ERROR)
                read
            }
            digest.update(buffer, 0, consumed)
            position = Math.addExact(position, consumed.toLong())
            synchronized(monitor) { job.processed = position }
        }
        checkpoint(job)
        if (source != null) {
            // A bounded EOF probe detects growth even if the adapter's metadata is coarse.
            if (source.readAt(position, buffer, 0, 1) != -1) fail(ErrorCode.SOURCE_CHANGED)
            if (!source.verifyUnchanged()) fail(ErrorCode.SOURCE_CHANGED)
        }
        checkpoint(job)
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun checkpoint(job: Job) = synchronized(monitor) {
        if (job.cancelRequested) fail(ErrorCode.DENIED)
        if (expired(job)) fail(ErrorCode.DEADLINE_EXCEEDED)
    }

    private fun expired(job: Job): Boolean = clock.nowMillis() - job.admittedAt >= job.request.deadlineMillis

    private fun closeSource(job: Job): Boolean {
        val source = job.source ?: return true
        val result = source.close() ?: return true
        synchronized(monitor) { ownedSources-- }
        return result
    }

    private fun authorize(owner: String, sessionId: String, requestedEpoch: String): Session {
        if (requestedEpoch != epoch) fail(ErrorCode.STALE_EPOCH)
        ensureOpen()
        if (!canonicalUuid(sessionId)) fail(ErrorCode.INVALID)
        prune()
        val session = sessions[sessionId] ?: fail(ErrorCode.EXPIRED_OR_UNKNOWN)
        if (session.owner != owner) fail(ErrorCode.DENIED)
        return session
    }

    private fun findJob(owner: String, sessionId: String, requestedEpoch: String, jobId: String): Job {
        authorize(owner, sessionId, requestedEpoch)
        if (!canonicalUuid(jobId)) fail(ErrorCode.INVALID)
        val job = jobs[jobId] ?: fail(ErrorCode.EXPIRED_OR_UNKNOWN)
        if (job.sessionId != sessionId) fail(ErrorCode.DENIED)
        return job
    }

    private fun validate(request: JobRequest, hasSource: Boolean) {
        if (request.protocolMajor != 1) fail(ErrorCode.INCOMPATIBLE)
        if (!canonicalUuid(request.requestId) || request.totalBytes !in 0..limits.maxJobBytes ||
            request.deadlineMillis !in 1..limits.maxDeadlineMillis) fail(ErrorCode.INVALID)
        when (request.operation) {
            GENERATED_OPERATION -> if (hasSource || request.alias != null) fail(ErrorCode.INVALID)
            FILE_OPERATION -> {
                if (!enableFileOperation) fail(ErrorCode.UNSUPPORTED)
                if (!hasSource || request.seed != 0L || request.alias == null || !FieldPackCodec.validateAlias(request.alias))
                    fail(ErrorCode.INVALID)
            }
            else -> fail(ErrorCode.UNSUPPORTED)
        }
        if (descriptorBytes(request) > limits.maxDescriptorBytes) fail(ErrorCode.INVALID)
    }

    private fun descriptorBytes(request: JobRequest): Int =
        ("{\"requestId\":\"${request.requestId}\",\"operation\":\"${request.operation}\"," +
            "\"totalBytes\":${request.totalBytes},\"seed\":${request.seed}," +
            "\"deadlineMillis\":${request.deadlineMillis},\"alias\":" +
            (request.alias?.let { "\"$it\"" } ?: "null") +
            ",\"protocolMajor\":${request.protocolMajor}}").toByteArray(Charsets.UTF_8).size

    // The fixed 768-byte snapshot envelope covers identifiers, all key names, counters,
    // longest error/state, and a 64-byte digest; JVM object overhead is excluded.
    private fun accountedBytes(request: JobRequest): Long = descriptorBytes(request).toLong() + 768

    private fun removeQueued(job: Job) {
        val session = sessions[job.sessionId] ?: return
        session.queue.remove(job)
        if (session.queue.isEmpty()) ready.remove(job.sessionId)
    }

    private fun revokeSession(sessionId: String): List<Job> {
        val session = sessions.remove(sessionId) ?: return emptyList()
        ready.remove(sessionId)
        val cleanup = session.queue.toList()
        session.requests.values.toList().forEach { job ->
            job.cancelRequested = !job.terminal
            if (job.state == JobState.ACCEPTED) commit(job, JobState.CANCELLED)
            if (job.terminal) forget(job)
        }
        return cleanup
    }

    private fun commit(job: Job, state: JobState, error: ErrorCode? = null, digest: String? = null) {
        if (job.terminal) return
        job.state = state
        job.error = error
        job.digest = digest
        job.finishedAt = clock.nowMillis()
        if (!sessions.containsKey(job.sessionId)) {
            jobs.remove(job.id)
            return
        }
        prune()
        while (retained.isNotEmpty() && (retained.size >= limits.maxRetainedEntries ||
                retainedBytes > limits.maxRetainedBytes - job.accountedBytes)) {
            forget(retained.values.first())
        }
        retained[job.id] = job
        retainedBytes = Math.addExact(retainedBytes, job.accountedBytes)
    }

    private fun prune() {
        val now = clock.nowMillis()
        retained.values.filter { now - it.finishedAt >= limits.terminalTtlMillis }.toList().forEach(::forget)
    }

    private fun forget(job: Job) {
        if (retained.remove(job.id) != null) retainedBytes -= job.accountedBytes
        jobs.remove(job.id)
        sessions[job.sessionId]?.requests?.remove(job.request.requestId)
    }

    private fun snapshot(job: Job) = JobSnapshot(epoch, job.sessionId, job.id,
        job.request.requestId, job.request.operation, job.state, job.request.totalBytes,
        job.processed, job.digest, job.error?.name, job.cancelRequested)

    private fun ensureOpen() { if (closed) fail(ErrorCode.EXPIRED_OR_UNKNOWN) }
    private fun canonicalUuid(value: String): Boolean =
        value.length == 36 && try { UUID.fromString(value).toString() == value } catch (_: IllegalArgumentException) { false }
    private fun fail(code: ErrorCode): Nothing = throw BoundaryException(code)
}
