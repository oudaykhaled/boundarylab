package dev.boundarylab.sdk

/** All handles are volatile and are invalid after a connection generation changes. */
data class JobHandle(
    val epoch: String,
    val sessionId: String,
    val jobId: String,
    val requestId: String,
    val generation: Long,
)

data class JobSnapshot(
    val state: String,
    val bytesProcessed: Long,
    val sha256: String?,
    val error: String?,
    val cancelRequested: Boolean,
) {
    val isTerminal: Boolean get() = state in setOf("SUCCEEDED", "FAILED", "CANCELLED")
}

data class BrokerCapabilities(
    val epoch: String,
    val protocolMajor: Int,
    val protocolMinor: Int,
    val operations: Set<String>,
    val maxBytes: Long,
    val brokerPid: Int,
)

class BoundaryException(val code: String, message: String = code) : Exception(message)

enum class ConnectionState { UNBOUND, BINDING, READY, RECOVERING, DENIED, INCOMPATIBLE, UNAVAILABLE, CLOSED }
