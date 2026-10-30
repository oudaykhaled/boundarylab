package dev.boundarylab.desktop

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.Closeable
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** The host is a child JVM; no in-process CoreEngine executes these client requests. */
class HostProcessTest {
    @Test fun `separate JVM generated vector ownership dedup and closed session`() = HostProcess().use { host ->
        DesktopClient(host.socket).use { first ->
            val caps = first.request(mapOf("op" to "capabilities"))
            assertEquals(true, caps["ok"])
            assertEquals(host.process.pid(), caps["pid"])
            assertNotEquals(ProcessHandle.current().pid(), caps["pid"])
            assertEquals(listOf("generated-sha256-v1"), caps["operations"])
            val session = first.request(mapOf("op" to "openSession"))
            val requestId = UUID.randomUUID().toString()
            val submit = sessionFields(session) + mapOf("op" to "submit", "requestId" to requestId,
                "operation" to "generated-sha256-v1", "totalBytes" to 0L, "seed" to 0L)
            val accepted = first.request(submit)
            assertEquals(true, accepted["ok"])
            val handle = sessionFields(session) + mapOf("jobId" to accepted["jobId"])
            val terminal = awaitTerminal(first, handle)
            assertEquals("SUCCEEDED", terminal["state"])
            assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", terminal["sha256"])
            assertEquals(accepted["jobId"], first.request(submit)["jobId"])
            assertEquals("CONFLICT", first.request(submit + ("totalBytes" to 1L))["code"])
            assertEquals(accepted["jobId"], first.request(sessionFields(session) + mapOf("op" to "queryRequest", "requestId" to requestId))["jobId"])
            DesktopClient(host.socket).use { other ->
                assertEquals("DENIED", other.request(handle + ("op" to "status"))["code"])
                assertEquals("DENIED", other.request(handle + ("op" to "cancel"))["code"])
                assertEquals("INVALID", other.request(handle + mapOf("op" to "status", "owner" to "forged"))["code"])
            }
            assertEquals("STALE_EPOCH", first.request(handle + mapOf("op" to "status", "epoch" to UUID.randomUUID().toString()))["code"])
            assertEquals(true, first.request(sessionFields(session) + ("op" to "closeSession"))["ok"])
            assertEquals("DENIED", first.request(handle + ("op" to "status"))["code"])
        }
    }

    @Test fun `connection death releases all sessions and forcibly killed process is not replayed`() = HostProcess().use { host ->
        DesktopClient(host.socket).use { first ->
            repeat(16) { assertEquals(true, first.request(mapOf("op" to "openSession"))["ok"]) }
            assertEquals("BUSY", first.request(mapOf("op" to "openSession"))["code"])
        }
        DesktopClient(host.socket).use { second ->
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            var response: Map<String, Any?>
            do {
                response = second.request(mapOf("op" to "openSession"))
                if (response["ok"] == true) break
                Thread.sleep(20)
            } while (System.nanoTime() < deadline)
            assertEquals(true, response["ok"])
            repeat(15) { assertEquals(true, second.request(mapOf("op" to "openSession"))["ok"]) }
            host.kill()
            val lost = assertThrows(DesktopProtocolException::class.java) { second.request(mapOf("op" to "capabilities")) }
            assertEquals("LOST_OR_UNKNOWN", lost.code)
            assertEquals("LOST_OR_UNKNOWN", assertThrows(DesktopProtocolException::class.java) {
                second.request(mapOf("op" to "capabilities"))
            }.code)
        }
    }

    @Test fun `generated chunks cancel cooperatively and a new host rejects old epochs`() {
        lateinit var oldHandle: Map<String, Any?>
        HostProcess().use { host ->
            DesktopClient(host.socket).use { client ->
                val session = client.request(mapOf("op" to "openSession"))
                val accepted = client.request(sessionFields(session) + mapOf("op" to "submit",
                    "operation" to "generated-sha256-v1", "requestId" to UUID.randomUUID().toString(),
                    "totalBytes" to 65_537L, "seed" to 255L))
                oldHandle = sessionFields(session) + mapOf("jobId" to accepted["jobId"])
                val result = awaitTerminal(client, oldHandle)
                val oracle = MessageDigest.getInstance("SHA-256").digest(ByteArray(65_537) { ((255 + it) and 255).toByte() })
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
                assertEquals(oracle, result["sha256"])
                val longJob = client.request(sessionFields(session) + mapOf("op" to "submit",
                    "operation" to "generated-sha256-v1", "requestId" to UUID.randomUUID().toString(),
                    "totalBytes" to 134_217_728L))
                val handle = sessionFields(session) + mapOf("jobId" to longJob["jobId"])
                val cancelled = client.request(handle + ("op" to "cancel"))
                assertTrue(cancelled["state"] in setOf("RUNNING", "CANCELLED", "SUCCEEDED"))
                if (cancelled["state"] == "RUNNING") assertEquals(true, cancelled["cancelRequested"])
                val terminal = awaitTerminal(client, handle)
                assertTrue(terminal["state"] in setOf("CANCELLED", "SUCCEEDED"))
                if (terminal["state"] == "CANCELLED") assertNull(terminal["sha256"])
                assertEquals(terminal["state"], client.request(handle + ("op" to "cancel"))["state"])
            }
        }
        HostProcess().use { replacement ->
            DesktopClient(replacement.socket).use { client ->
                val caps = client.request(mapOf("op" to "capabilities"))
                assertNotEquals(oldHandle["epoch"], caps["epoch"])
                assertEquals("STALE_EPOCH", client.request(oldHandle + ("op" to "status"))["code"])
            }
        }
    }

    @Test fun `socket mode is private and malformed or oversized frames never kill host`() = HostProcess().use { host ->
        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(host.socket.parent))
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(host.socket))
        for (length in listOf(0, -1, 16_385, Int.MAX_VALUE)) {
            host.raw().use { channel ->
                channel.write(ByteBuffer.allocate(4).putInt(length).flip())
                channel.configureBlocking(false)
                assertThrows(java.io.EOFException::class.java) { Wire.read(channel, 2_000) }
            }
        }
        host.raw().use { channel -> channel.write(ByteBuffer.wrap(byteArrayOf(0, 0))) }
        host.raw().use { channel ->
            channel.write(ByteBuffer.allocate(6).putInt(100).put(123).put(34).flip())
        }
        host.raw().use { channel ->
            val bad = "{\"op\":\"capabilities\",\"op\":\"openSession\"}".toByteArray()
            channel.write(ByteBuffer.allocate(4 + bad.size).putInt(bad.size).put(bad).flip())
            channel.configureBlocking(false)
            assertThrows(java.io.EOFException::class.java) { Wire.read(channel, 2_000) }
        }
        DesktopClient(host.socket).use { assertEquals(true, it.request(mapOf("op" to "capabilities"))["ok"]) }
    }

    @Test fun `partial frame absolute deadline releases a stalled connection`() = HostProcess().use { host ->
        host.raw().use { channel ->
            channel.write(ByteBuffer.wrap(byteArrayOf(0)))
            channel.configureBlocking(false)
            assertThrows(java.io.EOFException::class.java) { Wire.read(channel, 7_000) }
        }
        DesktopClient(host.socket).use { assertEquals(true, it.request(mapOf("op" to "openSession"))["ok"]) }
    }

    @Test fun `at most sixteen connections occupy handlers`() = HostProcess().use { host ->
        val active = mutableListOf<SocketChannel>()
        try {
            repeat(16) {
                active += host.raw().apply { write(ByteBuffer.wrap(byteArrayOf(0))) }
            }
            host.raw().use { rejected ->
                rejected.configureBlocking(false)
                assertThrows(java.io.EOFException::class.java) { Wire.read(rejected, 2_000) }
            }
        } finally { active.forEach { it.close() } }
        Unit // JUnit requires a void return type; assertThrows otherwise infers EOFException.
    }

    @Test fun `invalid protocol numeric booleans and unknown fields reject before work`() = HostProcess().use { host ->
        DesktopClient(host.socket).use { client ->
            assertEquals("INCOMPATIBLE", client.request(mapOf("op" to "openSession", "protocolMajor" to 2L))["code"])
            assertEquals("INVALID", client.request(mapOf("op" to "openSession", "protocolMajor" to true))["code"])
            val session = client.request(mapOf("op" to "openSession"))
            val submit = sessionFields(session) + mapOf("op" to "submit", "requestId" to UUID.randomUUID().toString(),
                "operation" to "generated-sha256-v1", "totalBytes" to true)
            assertEquals("INVALID", client.request(submit)["code"])
            assertEquals("INVALID", client.request(submit + ("totalBytes" to 134_217_729L))["code"])
            assertEquals("UNSUPPORTED", client.request(submit + mapOf("operation" to "regular-file-sha256-v1", "totalBytes" to 0L) - "totalBytes")["code"])
        }
    }

    private fun sessionFields(session: Map<String, Any?>) = mapOf("epoch" to session["epoch"], "sessionId" to session["sessionId"])
    private fun awaitTerminal(client: DesktopClient, handle: Map<String, Any?>): Map<String, Any?> {
        repeat(40) {
            val result = client.request(handle + ("op" to "status"))
            if (result["state"] in setOf("SUCCEEDED", "FAILED", "CANCELLED")) return result
            Thread.sleep(250)
        }
        fail<Unit>("Job did not become terminal")
        error("unreachable")
    }

    private class HostProcess : Closeable {
        private val directory = Files.createTempDirectory(Path.of("/tmp"), "blh-",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        val socket: Path = directory.resolve("host.sock")
        val process: Process = ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", System.getProperty("boundarylab.test.classpath"), "dev.boundarylab.desktop.HostKt",
            "--socket", socket.toString()).redirectErrorStream(true).start()
        init {
            val line = CompletableFuture.supplyAsync { process.inputStream.bufferedReader().readLine() }.get(10, TimeUnit.SECONDS)
            check(line?.startsWith("READY pid=") == true) { "Host did not start: $line" }
        }
        fun raw(): SocketChannel = SocketChannel.open(StandardProtocolFamily.UNIX).apply { connect(UnixDomainSocketAddress.of(socket)) }
        fun stop() {
            process.destroy()
            if (!process.waitFor(5, TimeUnit.SECONDS)) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS) }
        }
        fun kill() {
            process.destroyForcibly()
            check(process.waitFor(5, TimeUnit.SECONDS)) { "Killed host did not exit" }
        }
        override fun close() { stop(); Files.deleteIfExists(socket); Files.deleteIfExists(directory) }
    }
}
