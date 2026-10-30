package dev.boundarylab.core

const val GENERATED_OPERATION = "generated-sha256-v1"
const val FILE_OPERATION = "regular-file-sha256-v1"

data class SessionHandle(val epoch: String, val sessionId: String)

data class JobRequest(
    val requestId: String,
    val operation: String,
    val totalBytes: Long,
    val seed: Long = 0,
    val deadlineMillis: Long = 30_000,
    val alias: String? = null,
    val protocolMajor: Int = 1,
)

enum class JobState { ACCEPTED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

enum class ErrorCode {
    INVALID, BUSY, DENIED, INCOMPATIBLE, UNSUPPORTED, CONFLICT, STALE_EPOCH,
    EXPIRED_OR_UNKNOWN, DEADLINE_EXCEEDED, IO_ERROR, SOURCE_CHANGED,
}

/** Stable code only: adapter exceptions and private source paths never escape. */
class BoundaryException(val code: ErrorCode) : RuntimeException(code.name)

data class JobSnapshot(
    val epoch: String,
    val sessionId: String,
    val jobId: String,
    val requestId: String,
    val operation: String,
    val state: JobState,
    val totalBytes: Long,
    val bytesProcessed: Long,
    val digest: String? = null,
    val errorCode: String? = null,
    val cancelRequested: Boolean = false,
)

/** Lower limits support constrained hosts and deterministic cap-boundary tests. */
data class EngineLimits(
    val maxJobBytes: Long = 134_217_728,
    val bufferBytes: Int = 65_536,
    val workers: Int = 1,
    val maxQueuedJobs: Int = 16,
    val maxSessions: Int = 16,
    val maxOutstandingPerSession: Int = 4,
    val maxDescriptorBytes: Int = 4_096,
    val maxResponseBytes: Int = 16_384,
    val maxRetainedEntries: Int = 64,
    val terminalTtlMillis: Long = 60_000,
    val maxRetainedBytes: Long = 1_048_576,
    val maxDeadlineMillis: Long = 120_000,
) {
    init {
        require(maxJobBytes in 0..134_217_728 && bufferBytes in 1..65_536 && workers == 1)
        require(maxQueuedJobs in 1..16 && maxSessions in 1..16 && maxOutstandingPerSession in 1..4)
        require(maxDescriptorBytes in 1..4_096 && maxResponseBytes in 1..16_384)
        require(maxRetainedEntries in 1..64 && terminalTtlMillis in 1..60_000)
        require(maxRetainedBytes in 1..1_048_576 && maxDeadlineMillis in 1..120_000)
    }
}

data class Capabilities(
    val epoch: String,
    val operations: Set<String>,
    val limits: EngineLimits,
    val protocolMajor: Int = 1,
    val protocolMinor: Int = 0,
)

data class EngineMetrics(
    val sessions: Int,
    val queuedJobs: Int,
    val runningJobs: Int,
    val retainedEntries: Int,
    val retainedBytes: Long,
    val ownedSources: Int,
)

fun interface MonotonicClock {
    fun nowMillis(): Long

    companion object {
        val SYSTEM = MonotonicClock { System.nanoTime() / 1_000_000 }
    }
}
