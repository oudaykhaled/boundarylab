package dev.boundarylab.desktop.cli

import dev.boundarylab.core.FieldPackCodec
import dev.boundarylab.core.FieldPackEntry
import dev.boundarylab.core.FieldPackManifest
import dev.boundarylab.desktop.DesktopClient
import dev.boundarylab.desktop.DesktopProtocolException
import dev.boundarylab.desktop.Wire
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import kotlin.system.exitProcess

private const val MAX_BYTES = 134217728L
private const val MAX_MANIFEST = 16384
private const val FILE_OPERATION = "regular-file-sha256-v1"
private const val GENERATED_OPERATION = "generated-sha256-v1"

class CliError(val code: String) : RuntimeException(code)

/** Bounded manifest input. Trusted directory ancestry; attachments are opened only by the host. */
fun loadManifest(path: Path): FieldPackManifest {
    val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
    if (!attributes.isRegularFile) throw CliError("UNSUPPORTED_MANIFEST_SOURCE")
    FileChannel.open(path, READ, NOFOLLOW_LINKS).use { channel ->
        val buffer = ByteBuffer.allocate(MAX_MANIFEST + 1)
        while (buffer.hasRemaining() && channel.read(buffer) != -1) Unit
        if (buffer.position() > MAX_MANIFEST) throw CliError("MANIFEST_TOO_LARGE")
        return FieldPackCodec.decode(buffer.array().copyOf(buffer.position()))
    }
}

/** Publish a completed private temporary file using atomic no-replace hard-link creation. */
fun publishManifest(output: Path, bytes: ByteArray) {
    FieldPackCodec.decode(bytes)
    val absolute = output.toAbsolutePath().normalize()
    val parent = absolute.parent.toRealPath()
    val target = parent.resolve(absolute.fileName)
    val temporary = Files.createTempFile(parent, ".fieldpack-", ".tmp",
        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
    try {
        FileChannel.open(temporary, WRITE).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
        Files.createLink(target, temporary)
    } finally {
        Files.deleteIfExists(temporary)
    }
}

private class Arguments(args: Array<String>) {
    val command = args.firstOrNull() ?: throw CliError("INVALID_ARGUMENTS")
    private val values = linkedMapOf<String, MutableList<String>>()
    init {
        if (args.size % 2 != 1) throw CliError("INVALID_ARGUMENTS")
        args.drop(1).chunked(2).forEach { (key, value) ->
            if (!key.startsWith("--")) throw CliError("INVALID_ARGUMENTS")
            values.getOrPut(key) { mutableListOf() }.add(value)
        }
        val allowed = when (command) {
            "generated" -> setOf("--socket", "--bytes", "--seed")
            "create" -> setOf("--socket", "--name", "--output")
            "verify" -> setOf("--socket", "--manifest")
            else -> throw CliError("INVALID_ARGUMENTS")
        }
        if (values.keys != allowed || values.any { (key, value) -> key != "--name" && value.size != 1 }) {
            throw CliError("INVALID_ARGUMENTS")
        }
    }
    fun one(key: String): String = values.getValue(key).single()
    fun many(key: String): List<String> = values.getValue(key).toList()
}

private class Attempt(private val client: DesktopClient, operation: String) : AutoCloseable {
    val hostPid: Long
    private val epoch: String
    private val sessionId: String
    init {
        val capabilities = call(mapOf("op" to "capabilities"))
        if (capabilities["protocolMajor"] != 1L || operation !in (capabilities["operations"] as? List<*> ?: emptyList<Any>())) {
            throw CliError("UNSUPPORTED_CAPABILITY")
        }
        hostPid = capabilities["pid"] as? Long ?: throw CliError("INVALID_HOST_RESPONSE")
        if (hostPid == ProcessHandle.current().pid()) throw CliError("HOST_MUST_BE_SEPARATE_PROCESS")
        val session = call(mapOf("op" to "openSession"))
        epoch = session["epoch"] as? String ?: throw CliError("INVALID_HOST_RESPONSE")
        sessionId = session["sessionId"] as? String ?: throw CliError("INVALID_HOST_RESPONSE")
    }
    private fun call(request: Map<String, Any?>): Map<String, Any?> {
        val response = client.request(request)
        if (response["ok"] != true) throw CliError(response["code"] as? String ?: "HOST_ERROR")
        return response
    }
    fun validateOutput(path: Path) {
        call(mapOf("op" to "validateOutput", "path" to path.toAbsolutePath().normalize().toString()))
    }
    fun hash(operation: String, bytes: Long, seed: Long = 0, alias: String? = null, expectedSize: Long? = null): Map<String, Any?> {
        val request = linkedMapOf<String, Any?>("op" to "submit", "epoch" to epoch,
            "sessionId" to sessionId, "requestId" to UUID.randomUUID().toString(),
            "operation" to operation, "totalBytes" to bytes, "seed" to seed, "deadlineMillis" to 30000L)
        if (alias != null) {
            request.remove("totalBytes")
            request.remove("seed")
            request["alias"] = alias
            request["maxBytes"] = bytes
        }
        if (expectedSize != null) request["expectedSize"] = expectedSize
        var result = call(request)
        val jobId = result["jobId"] as? String ?: throw CliError("INVALID_HOST_RESPONSE")
        val stop = System.nanoTime() + 35_000_000_000L
        while (result["state"] == "ACCEPTED" || result["state"] == "RUNNING") {
            if (System.nanoTime() >= stop) throw CliError("LOST_OR_UNKNOWN")
            Thread.sleep(250)
            result = call(mapOf("op" to "status", "epoch" to epoch, "sessionId" to sessionId, "jobId" to jobId))
        }
        if (result["state"] != "SUCCEEDED") throw CliError(result["errorCode"] as? String ?: "UNREADABLE_OR_UNSUPPORTED")
        return result
    }
    override fun close() {
        // Connection close revokes the session even if the explicit close fails.
        runCatching { call(mapOf("op" to "closeSession", "epoch" to epoch, "sessionId" to sessionId)) }
    }
}

fun runCli(args: Array<String>): Int {
    try {
        val arguments = Arguments(args)
        // Validate ALL manifest entries and aliases before opening a host connection or admitting jobs.
        val manifest = if (arguments.command == "verify") loadManifest(Path.of(arguments.one("--manifest"))) else null
        val names = if (arguments.command == "create") arguments.many("--name").sorted() else emptyList()
        if (arguments.command == "create" && (names.size !in 1..32 || names.distinct().size != names.size || names.any { !FieldPackCodec.validateAlias(it) })) {
            throw CliError("INVALID_NAMES")
        }
        val bytes = if (arguments.command == "generated") arguments.one("--bytes").toLongOrNull() ?: throw CliError("INVALID_BYTES") else 0
        val seed = if (arguments.command == "generated") arguments.one("--seed").toLongOrNull() ?: throw CliError("INVALID_SEED") else 0
        if (bytes !in 0..MAX_BYTES) throw CliError("INVALID_BYTES")
        DesktopClient(Path.of(arguments.one("--socket"))).use { client ->
            Attempt(client, if (arguments.command == "generated") GENERATED_OPERATION else FILE_OPERATION).use { attempt ->
                when (arguments.command) {
                    "generated" -> {
                        val result = attempt.hash(GENERATED_OPERATION, bytes, seed)
                        printJson(mapOf("result" to "SUCCEEDED", "size" to result["totalBytes"], "sha256" to result["sha256"],
                            "hostPid" to attempt.hostPid, "cliPid" to ProcessHandle.current().pid()))
                        return 0
                    }
                    "create" -> {
                        val output = Path.of(arguments.one("--output"))
                        attempt.validateOutput(output)
                        var remaining = MAX_BYTES
                        val entries = names.map { name ->
                            val result = attempt.hash(FILE_OPERATION, remaining, alias = name)
                            val size = result["totalBytes"] as? Long ?: throw CliError("INVALID_HOST_RESPONSE")
                            if (size !in 0..remaining) throw CliError("PACK_TOO_LARGE")
                            remaining -= size
                            FieldPackEntry(name, size, result["sha256"] as? String ?: throw CliError("INVALID_HOST_RESPONSE"))
                        }
                        val encoded = FieldPackCodec.encode(FieldPackManifest(entries))
                        attempt.validateOutput(output)
                        publishManifest(output, encoded)
                        printJson(mapOf("result" to "CREATED", "files" to names.size, "hostPid" to attempt.hostPid, "cliPid" to ProcessHandle.current().pid()))
                        return 0
                    }
                    "verify" -> {
                        val results = manifest!!.files.map { expected ->
                            val outcome = try {
                                val actual = attempt.hash(FILE_OPERATION, expected.size, alias = expected.name, expectedSize = expected.size)
                                when {
                                    actual["totalBytes"] != expected.size -> "SIZE_MISMATCH"
                                    actual["sha256"] != expected.sha256 -> "DIGEST_MISMATCH"
                                    else -> "MATCH"
                                }
                            } catch (error: CliError) {
                                when (error.code) {
                                    "MISSING" -> "MISSING"
                                    "SIZE_MISMATCH" -> "SIZE_MISMATCH"
                                    "UNREADABLE_OR_UNSUPPORTED", "INPUT_CHANGED", "SOURCE_CHANGED", "UNSUPPORTED_SOURCE", "INPUT_TOO_LARGE", "IO_ERROR" -> "UNREADABLE_OR_UNSUPPORTED"
                                    else -> throw error
                                }
                            }
                            mapOf("name" to expected.name, "outcome" to outcome)
                        }
                        printJson(mapOf("scope" to "manifest_entries_only", "files" to results,
                            "hostPid" to attempt.hostPid, "cliPid" to ProcessHandle.current().pid()))
                        return if (results.all { it["outcome"] == "MATCH" }) 0 else 1
                    }
                }
            }
        }
        throw CliError("INVALID_ARGUMENTS")
    } catch (error: Exception) {
        val code = when (error) {
            is CliError -> error.code
            is DesktopProtocolException -> error.code
            is InterruptedException -> "INTERRUPTED"
            is java.io.IOException -> "IO_ERROR"
            else -> "INVALID_INPUT"
        }
        printJson(mapOf("error" to code))
        return if (error is InterruptedException) 130 else 2
    }
}

private fun printJson(value: Map<String, Any?>) = println(String(Wire.encode(value), Charsets.UTF_8))
fun main(args: Array<String>) {
    exitProcess(runCli(args))
}
