package dev.boundarylab.desktop

import dev.boundarylab.core.BoundaryException
import dev.boundarylab.core.CoreEngine
import dev.boundarylab.core.FILE_OPERATION
import dev.boundarylab.core.FieldPackCodec
import dev.boundarylab.core.GENERATED_OPERATION
import dev.boundarylab.core.JobRequest
import dev.boundarylab.core.JobSnapshot
import dev.boundarylab.core.PositionalSource
import dev.boundarylab.core.SessionHandle
import java.io.Closeable
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/** A missing/unproven provider does not advertise file support. */
interface DesktopFileProvider : AutoCloseable {
    val supported: Boolean
    fun open(alias: String): PositionalSource
    fun approveOutput(output: Path)
    override fun close()
}

/** Same-user UDS transport. All policy and hashing belong to the supplied core. */
class DesktopHost(
    socket: Path,
    private val engine: CoreEngine,
    private val fileProvider: DesktopFileProvider? = null,
) : Closeable {
    private val socketPath = prepareSocketPath(socket)
    private val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
    private val stopped = AtomicBoolean(false)
    private val connections = ConcurrentHashMap.newKeySet<SocketChannel>()
    private val slots = Semaphore(MAX_CONNECTIONS)
    private val executor = Executors.newFixedThreadPool(MAX_CONNECTIONS) { task ->
        Thread(task, "boundarylab-connection").apply { isDaemon = true }
    }
    private var socketIdentity: Any? = null

    init {
        try {
            server.bind(UnixDomainSocketAddress.of(socketPath), MAX_CONNECTIONS)
            socketIdentity = Files.readAttributes(socketPath, BasicFileAttributes::class.java, NOFOLLOW_LINKS).fileKey()
            Files.setPosixFilePermissions(socketPath, PosixFilePermissions.fromString("rw-------"))
        } catch (failure: Throwable) {
            server.close()
            executor.shutdownNow()
            deleteOwnedSocket()
            throw failure
        }
    }

    /** Blocks only on socket acceptance; bounded connection handlers use frame deadlines. */
    fun run() {
        while (!stopped.get()) {
            val connection = try { server.accept() } catch (failure: IOException) {
                if (stopped.get()) return else throw failure
            }
            if (!slots.tryAcquire()) { connection.close(); continue }
            connections.add(connection)
            try {
                connection.configureBlocking(false)
                executor.execute { serve(connection) }
            } catch (failure: Exception) {
                connection.close(); connections.remove(connection); slots.release()
                if (!stopped.get()) throw failure
            }
        }
    }

    private fun serve(connection: SocketChannel) {
        // This token is created here, never deserialized from the request.
        val owner = UUID.randomUUID().toString()
        val sessions = linkedMapOf<String, SessionHandle>()
        try {
            while (!stopped.get()) {
                val request = Wire.read(connection)
                val response = try { dispatch(owner, sessions, request) }
                catch (failure: BoundaryException) { error(failure.code.name) }
                catch (failure: DesktopProtocolException) { error(failure.code) }
                catch (_: Exception) { error("IO_ERROR") }
                Wire.write(connection, response)
            }
        } catch (_: IOException) {
            // EOF, malformed framing and deadline expiry all revoke the connection's sessions.
        } finally {
            for (handle in sessions.values) {
                try { engine.closeSession(owner, handle.sessionId, handle.epoch) } catch (_: Exception) { }
            }
            try { connection.close() } finally {
                connections.remove(connection)
                slots.release()
            }
        }
    }

    private fun dispatch(
        owner: String, sessions: MutableMap<String, SessionHandle>, request: Map<String, Any?>,
    ): Map<String, Any?> {
        val op = request.text("op")
        val fields = when (op) {
            "capabilities", "openSession" -> setOf("op", "protocolMajor")
            "validateOutput" -> setOf("op", "protocolMajor", "path")
            "submit" -> setOf("op", "protocolMajor", "epoch", "sessionId", "requestId", "operation",
                "totalBytes", "seed", "deadlineMillis", "alias", "maxBytes", "expectedSize")
            "status", "cancel" -> setOf("op", "protocolMajor", "epoch", "sessionId", "jobId")
            "queryRequest" -> setOf("op", "protocolMajor", "epoch", "sessionId", "requestId")
            "closeSession" -> setOf("op", "protocolMajor", "epoch", "sessionId")
            else -> throw DesktopProtocolException("INVALID")
        }
        if (!fields.containsAll(request.keys)) throw DesktopProtocolException("INVALID")
        if (request.number("protocolMajor", 1) != 1L) throw DesktopProtocolException("INCOMPATIBLE")
        if (op == "capabilities") return capabilities()
        if (op == "openSession") {
            val session = engine.openSession(owner)
            sessions[session.sessionId] = session
            return mapOf("ok" to true, "epoch" to session.epoch, "sessionId" to session.sessionId)
        }
        if (op == "validateOutput") {
            val provider = fileProvider?.takeIf { it.supported } ?: throw DesktopProtocolException("UNSUPPORTED")
            val output = Path.of(request.text("path"))
            if (!output.isAbsolute) throw DesktopProtocolException("INVALID")
            provider.approveOutput(output)
            return mapOf("ok" to true)
        }
        val epoch = request.uuid("epoch")
        val sessionId = request.uuid("sessionId")
        if (epoch != engine.capabilities().epoch) throw DesktopProtocolException("STALE_EPOCH")
        // Check before adapter opening; core independently enforces owner on every operation.
        if (sessions[sessionId]?.epoch != epoch) throw DesktopProtocolException("DENIED")
        engine.validateSession(owner, sessionId, epoch)
        return when (op) {
            "submit" -> submit(owner, sessionId, epoch, request)
            "status" -> snapshot(engine.status(owner, sessionId, epoch, request.uuid("jobId")))
            "queryRequest" -> snapshot(engine.statusByRequest(owner, sessionId, epoch, request.uuid("requestId")))
            "cancel" -> snapshot(engine.cancel(owner, sessionId, epoch, request.uuid("jobId")))
            "closeSession" -> {
                engine.closeSession(owner, sessionId, epoch)
                sessions.remove(sessionId)
                mapOf("ok" to true)
            }
            else -> throw DesktopProtocolException("INVALID")
        }
    }

    private fun submit(owner: String, sessionId: String, epoch: String, wire: Map<String, Any?>): Map<String, Any?> {
        val operation = wire.text("operation")
        val requestId = wire.uuid("requestId")
        val deadline = wire.number("deadlineMillis", 30_000)
        if (deadline !in 1..120_000) throw DesktopProtocolException("INVALID")
        if (operation == GENERATED_OPERATION) {
            if (wire.keys.any { it in setOf("alias", "maxBytes", "expectedSize") }) throw DesktopProtocolException("INVALID")
            return snapshot(engine.submit(owner, sessionId, epoch, JobRequest(
                requestId, operation, wire.number("totalBytes"), wire.number("seed", 0), deadline)))
        }
        if (operation != FILE_OPERATION) throw DesktopProtocolException("UNSUPPORTED")
        if (wire.containsKey("seed") || wire.containsKey("totalBytes")) throw DesktopProtocolException("INVALID")
        val provider = fileProvider?.takeIf { it.supported } ?: throw DesktopProtocolException("UNSUPPORTED")
        val alias = wire.text("alias")
        if (!FieldPackCodec.validateAlias(alias)) throw DesktopProtocolException("INVALID")
        val maxBytes = wire.number("maxBytes", FieldPackCodec.MAX_TOTAL_BYTES)
        val expected = if (wire.containsKey("expectedSize")) wire.number("expectedSize") else null
        if (maxBytes !in 0..FieldPackCodec.MAX_TOTAL_BYTES || expected != null && expected !in 0..FieldPackCodec.MAX_TOTAL_BYTES)
            throw DesktopProtocolException("INVALID")
        val source = provider.open(alias)
        val size: Long
        try {
            size = source.size
            if (expected != null && expected != size) throw DesktopProtocolException("SIZE_MISMATCH")
            if (size !in 0..maxBytes) throw DesktopProtocolException("INVALID")
        } catch (failure: Throwable) { source.close(); throw failure }
        // Ownership transfers on this invocation, including duplicate/rejected submissions.
        return snapshot(engine.submit(owner, sessionId, epoch, JobRequest(
            requestId, operation, size, deadlineMillis = deadline, alias = alias), source))
    }

    private fun capabilities(): Map<String, Any?> {
        val caps = engine.capabilities()
        val limits = caps.limits
        val operations = caps.operations.filter { it != FILE_OPERATION || fileProvider?.supported == true }.sorted()
        return mapOf("ok" to true, "protocolMajor" to caps.protocolMajor, "protocolMinor" to caps.protocolMinor,
            "epoch" to caps.epoch, "operations" to operations, "pid" to ProcessHandle.current().pid(),
            "limits" to mapOf("maxJobBytes" to limits.maxJobBytes, "bufferBytes" to limits.bufferBytes,
                "workers" to limits.workers, "maxQueuedJobs" to limits.maxQueuedJobs, "maxSessions" to limits.maxSessions,
                "maxOutstandingPerSession" to limits.maxOutstandingPerSession, "maxDescriptorBytes" to limits.maxDescriptorBytes,
                "maxResponseBytes" to limits.maxResponseBytes, "maxRetainedEntries" to limits.maxRetainedEntries,
                "terminalTtlMillis" to limits.terminalTtlMillis, "maxRetainedBytes" to limits.maxRetainedBytes,
                "maxDeadlineMillis" to limits.maxDeadlineMillis, "maxConnections" to MAX_CONNECTIONS))
    }

    override fun close() {
        if (!stopped.compareAndSet(false, true)) return
        server.close()
        connections.forEach { try { it.close() } catch (_: IOException) { } }
        executor.shutdown()
        engine.close()
        fileProvider?.close()
        deleteOwnedSocket()
    }

    private fun deleteOwnedSocket() {
        if (socketIdentity == null) return
        if (Files.exists(socketPath, NOFOLLOW_LINKS) &&
            Files.readAttributes(socketPath, BasicFileAttributes::class.java, NOFOLLOW_LINKS).fileKey() == socketIdentity)
            Files.delete(socketPath)
    }

    companion object {
        const val MAX_CONNECTIONS = 16
        private fun prepareSocketPath(socket: Path): Path {
            val absolute = socket.toAbsolutePath().normalize()
            val parent = absolute.parent ?: throw DesktopProtocolException("INVALID")
            if (!Files.exists(parent, NOFOLLOW_LINKS)) {
                Files.createDirectory(parent, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
            }
            if (!Files.isDirectory(parent, NOFOLLOW_LINKS) || Files.isSymbolicLink(parent)) throw DesktopProtocolException("INVALID")
            val currentOwner = parent.fileSystem.userPrincipalLookupService.lookupPrincipalByName(System.getProperty("user.name"))
            if (Files.getOwner(parent, NOFOLLOW_LINKS) != currentOwner ||
                Files.getPosixFilePermissions(parent, NOFOLLOW_LINKS) != PosixFilePermissions.fromString("rwx------"))
                throw DesktopProtocolException("DENIED")
            val resolved = parent.toRealPath().resolve(absolute.fileName)
            if (Files.exists(resolved, NOFOLLOW_LINKS)) throw DesktopProtocolException("BUSY")
            return resolved
        }
        private fun snapshot(job: JobSnapshot): Map<String, Any?> = mapOf(
            "ok" to true, "epoch" to job.epoch, "sessionId" to job.sessionId, "jobId" to job.jobId,
            "requestId" to job.requestId, "operation" to job.operation, "state" to job.state.name,
            "totalBytes" to job.totalBytes, "bytesProcessed" to job.bytesProcessed, "sha256" to job.digest,
            "errorCode" to job.errorCode, "cancelRequested" to job.cancelRequested,
        )
        private fun error(code: String): Map<String, Any?> = mapOf("ok" to false, "code" to code, "message" to code)
    }
}

private fun Map<String, Any?>.text(key: String): String = this[key] as? String ?: throw DesktopProtocolException("INVALID")
private fun Map<String, Any?>.number(key: String, default: Long? = null): Long =
    if (!containsKey(key) && default != null) default else this[key] as? Long ?: throw DesktopProtocolException("INVALID")
private fun Map<String, Any?>.uuid(key: String): String {
    val value = text(key)
    if (!Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(value))
        throw DesktopProtocolException("INVALID")
    return value
}

fun main(args: Array<String>) {
    try {
        val options = linkedMapOf<String, String>()
        if (args.size % 2 != 0) throw DesktopProtocolException("INVALID")
        for (index in args.indices step 2) {
            if (args[index] !in setOf("--socket", "--root") || options.put(args[index], args[index + 1]) != null)
                throw DesktopProtocolException("INVALID")
        }
        val socket = Path.of(options["--socket"] ?: throw DesktopProtocolException("INVALID"))
        val provider = options["--root"]?.let {
            try { RootedFileProvider(Path.of(it)) }
            catch (failure: DesktopProtocolException) {
                if (failure.code != "UNSUPPORTED") throw failure
                System.err.println("File capability unavailable: UNSUPPORTED")
                null
            }
        }
        val engine = CoreEngine(enableFileOperation = provider?.supported == true)
        DesktopHost(socket, engine, provider).use { host ->
            Runtime.getRuntime().addShutdownHook(Thread { host.close() })
            println("READY pid=${ProcessHandle.current().pid()}")
            host.run()
        }
    } catch (failure: Exception) {
        val code = (failure as? DesktopProtocolException)?.code ?: "IO_ERROR"
        System.err.println("Host startup/runtime failed: $code")
        kotlin.system.exitProcess(2)
    }
}
