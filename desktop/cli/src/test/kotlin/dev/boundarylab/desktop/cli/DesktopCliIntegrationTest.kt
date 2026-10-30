package dev.boundarylab.desktop.cli

import dev.boundarylab.core.FieldPackCodec
import dev.boundarylab.core.FieldPackEntry
import dev.boundarylab.core.FieldPackManifest
import dev.boundarylab.desktop.DesktopClient
import dev.boundarylab.desktop.Wire
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Each invocation executes the application in a fresh JVM against a different host JVM. */
class DesktopCliIntegrationTest {
    @TempDir lateinit var temporary: Path
    private val classpath get() = System.getProperty("boundarylab.test.classpath")
    private val javaExecutable get() = Path.of(System.getProperty("java.home"), "bin", "java").toString()
    private val abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    private val empty = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    private fun manifest() = FieldPackCodec.encode(FieldPackManifest(listOf(
        FieldPackEntry("abc.txt", 3, abc), FieldPackEntry("empty.bin", 0, empty))))
    private data class Run(val exit: Int, val body: Map<String, Any?>)

    private inner class Host(root: Path?) : AutoCloseable {
        // macOS Unix socket paths have a short platform limit.
        val directory: Path = Files.createTempDirectory(Path.of("/tmp"), "bl-")
        val socket: Path = directory.resolve("h.sock")
        val log: Path = directory.resolve("host.log")
        val process: Process
        init {
            val command = mutableListOf(javaExecutable)
            System.getProperty("boundarylab.native.library")?.let { command += "-Dboundarylab.native.library=$it" }
            command += listOf("-cp", classpath, "dev.boundarylab.desktop.HostKt", "--socket", socket.toString())
            if (root != null) command += listOf("--root", root.toString())
            process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start()
            val stop = System.nanoTime() + 10_000_000_000L
            var ready = false
            while (System.nanoTime() < stop) {
                if (!process.isAlive) fail<Unit>("Host exited: ${Files.readString(log)}")
                if (Files.exists(socket) && runCatching {
                        DesktopClient(socket).use { it.request(mapOf("op" to "capabilities"))["ok"] == true }
                    }.getOrDefault(false)) { ready = true; break }
                Thread.sleep(25)
            }
            if (!ready) {
                process.destroyForcibly()
                fail<Unit>("Host startup timed out: ${Files.readString(log)}")
            }
        }
        fun cli(vararg args: String): Run {
            val output = Files.createTempFile(temporary, "cli-", ".log")
            val child = ProcessBuilder(listOf(javaExecutable, "-cp", classpath, "dev.boundarylab.desktop.cli.MainKt") +
                args.toList() + listOf("--socket", socket.toString()))
                .redirectErrorStream(true).redirectOutput(output.toFile()).start()
            val finished = child.waitFor(40, TimeUnit.SECONDS)
            if (!finished) child.destroyForcibly().waitFor()
            assertTrue(finished, "CLI exceeded timeout")
            val body = Files.readAllBytes(output)
            return Run(child.exitValue(), try { Wire.decode(body) } catch (error: Exception) {
                throw AssertionError("CLI exit ${child.exitValue()} returned non-JSON: ${String(body)}", error)
            })
        }
        override fun close() {
            process.destroy()
            if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly().waitFor()
            directory.toFile().deleteRecursively()
        }
    }

    private fun root(): Path = Files.createDirectory(temporary.resolve("attachments")).also {
        Files.writeString(it.resolve("abc.txt"), "abc")
        Files.write(it.resolve("empty.bin"), byteArrayOf())
    }
    private fun outcomes(run: Run) = (run.body["files"] as List<*>).map { (it as Map<*, *>)["outcome"] }

    @Test fun `separate JVMs create canonical known vectors and verify changes`() {
        val root = root()
        val output = temporary.resolve("pack.json")
        Host(root).use { host ->
            val created = host.cli("create", "--name", "empty.bin", "--name", "abc.txt", "--output", output.toString())
            assertEquals(0, created.exit, created.body.toString())
            assertEquals(host.process.pid(), created.body["hostPid"])
            assertNotEquals(created.body["hostPid"], created.body["cliPid"])
            assertNotEquals(ProcessHandle.current().pid(), created.body["cliPid"])
            assertArrayEquals(manifest(), Files.readAllBytes(output))
            val matched = host.cli("verify", "--manifest", output.toString())
            assertEquals(0, matched.exit)
            assertEquals(listOf("MATCH", "MATCH"), outcomes(matched))
            Files.writeString(root.resolve("abc.txt"), "abd")
            val changed = host.cli("verify", "--manifest", output.toString())
            assertEquals(1, changed.exit)
            assertEquals(listOf("DIGEST_MISMATCH", "MATCH"), outcomes(changed))
            Files.writeString(root.resolve("abc.txt"), "abcd")
            assertEquals(listOf("SIZE_MISMATCH", "MATCH"), outcomes(host.cli("verify", "--manifest", output.toString())))
            Files.writeString(root.resolve("abc.txt"), "a")
            assertEquals(listOf("SIZE_MISMATCH", "MATCH"), outcomes(host.cli("verify", "--manifest", output.toString())))
            Files.delete(root.resolve("abc.txt"))
            assertEquals(listOf("MISSING", "MATCH"), outcomes(host.cli("verify", "--manifest", output.toString())))
        }
    }

    @Test fun `unsafe aliases return unreadable without hanging or publishing`() {
        val root = root()
        val input = temporary.resolve("pack.json")
        Files.write(input, manifest())
        Host(root).use { host ->
            val alias = root.resolve("abc.txt")
            val outside = temporary.resolve("outside.txt")
            Files.writeString(outside, "abc")
            Files.delete(alias)
            Files.createSymbolicLink(alias, outside)
            fun rejected() {
                val result = host.cli("verify", "--manifest", input.toString())
                assertEquals(1, result.exit, result.body.toString())
                assertEquals(listOf("UNREADABLE_OR_UNSUPPORTED", "MATCH"), outcomes(result))
                val output = temporary.resolve("rejected.json")
                assertEquals(2, host.cli("create", "--name", "abc.txt", "--output", output.toString()).exit)
                assertFalse(Files.exists(output))
            }
            rejected()
            Files.delete(alias)
            Files.createLink(alias, outside)
            rejected()
            Files.delete(alias)
            Files.createDirectory(alias)
            rejected()
            Files.delete(alias)
            val mkfifo = ProcessBuilder("mkfifo", alias.toString()).start()
            assertEquals(0, mkfifo.waitFor())
            rejected()
            Files.delete(alias)
            Files.writeString(alias, "abc")
            Files.setPosixFilePermissions(alias, emptySet())
            try { rejected() } finally {
                Files.setPosixFilePermissions(alias, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))
            }
        }
    }

    @Test fun `publication refuses existing targets symlinks and input root`() {
        val root = root()
        Host(root).use { host ->
            val existing = temporary.resolve("existing.json")
            Files.writeString(existing, "preserve")
            assertEquals(2, host.cli("create", "--name", "abc.txt", "--output", existing.toString()).exit)
            assertEquals("preserve", Files.readString(existing))
            val symlink = temporary.resolve("link.json")
            Files.createSymbolicLink(symlink, existing)
            assertEquals(2, host.cli("create", "--name", "abc.txt", "--output", symlink.toString()).exit)
            assertEquals("preserve", Files.readString(existing))
            assertEquals(2, host.cli("create", "--name", "abc.txt", "--output", root.resolve("out.json").toString()).exit)
            assertFalse(Files.exists(root.resolve("out.json")))
            Files.list(temporary).use { paths -> assertFalse(paths.anyMatch { it.fileName.toString().startsWith(".fieldpack-") }) }
        }
    }

    @Test fun `shared strict manifest corpus rejected before socket connect`() {
        val corpusPath = Path.of(System.getProperty("boundarylab.project.root"), "conformance/shared/manifest-v1.json")
        val corpus = ObjectMapper().readValue(Files.readAllBytes(corpusPath), Map::class.java)
        val cases = corpus["cases"] as List<*>
        assertTrue(cases.isNotEmpty())
        for (rawCase in cases) {
            val case = rawCase as Map<*, *>
            val raw = Base64.getDecoder().decode(case["rawBase64"] as String)
            if (case["valid"] == true) {
                val decoded = FieldPackCodec.decode(raw)
                (case["canonicalBase64"] as? String)?.let { assertArrayEquals(Base64.getDecoder().decode(it), FieldPackCodec.encode(decoded), case["id"].toString()) }
            } else {
                assertThrows(Exception::class.java, { FieldPackCodec.decode(raw) }, case["id"].toString())
                val input = temporary.resolve("invalid.json")
                Files.write(input, raw)
                // No server exists: invalid input must fail before trying to connect.
                val output = Files.createTempFile(temporary, "invalid-", ".log")
                val process = ProcessBuilder(javaExecutable, "-cp", classpath, "dev.boundarylab.desktop.cli.MainKt", "verify",
                    "--socket", temporary.resolve("absent.sock").toString(), "--manifest", input.toString())
                    .redirectErrorStream(true).redirectOutput(output.toFile()).start()
                assertTrue(process.waitFor(10, TimeUnit.SECONDS))
                assertEquals(2, process.exitValue(), Files.readString(output))
                val error = Wire.decode(Files.readAllBytes(output))["error"]
                assertTrue(error == "INVALID_INPUT" || error == "MANIFEST_TOO_LARGE", "${case["id"]}: $error")
            }
        }
    }

    @Test fun `generated supports empty vector and file capability absent is explicit`() {
        val input = temporary.resolve("pack.json")
        Files.write(input, manifest())
        Host(null).use { host ->
            val generated = host.cli("generated", "--bytes", "0", "--seed", "1")
            assertEquals(0, generated.exit, generated.body.toString())
            assertEquals(empty, generated.body["sha256"])
            assertEquals(host.process.pid(), generated.body["hostPid"])
            val unsupported = host.cli("verify", "--manifest", input.toString())
            assertEquals(2, unsupported.exit)
            assertEquals("UNSUPPORTED_CAPABILITY", unsupported.body["error"])
            assertEquals(2, host.cli("generated", "--bytes", "134217729", "--seed", "1").exit)
        }
    }

    @Test fun `independent Python oracle agrees on host created manifest`() {
        val root = root()
        val output = temporary.resolve("pack.json")
        Host(root).use { host ->
            assertEquals(0, host.cli("create", "--name", "abc.txt", "--name", "empty.bin", "--output", output.toString()).exit)
        }
        val oracle = ProcessBuilder("python3", Path.of(System.getProperty("boundarylab.project.root"), "examples/fieldpack/fieldpack.py").toString(),
            "verify", "--root", root.toString(), "--manifest", output.toString()).redirectErrorStream(true).start()
        val oracleText = oracle.inputStream.bufferedReader().readText()
        assertTrue(oracle.waitFor(10, TimeUnit.SECONDS))
        assertEquals(0, oracle.exitValue(), oracleText)
        assertEquals(listOf("MATCH", "MATCH"), outcomes(Run(0, Wire.decode(oracleText.toByteArray()))))
    }

    @Test fun `real multichunk binary file agrees with independent Python verification`() {
        val root = root()
        val binary = ByteArray(131073) { it.toByte() }
        Files.write(root.resolve("multichunk.bin"), binary)
        val output = temporary.resolve("multichunk.json")
        Host(root).use { host ->
            val created = host.cli("create", "--name", "multichunk.bin", "--output", output.toString())
            assertEquals(0, created.exit, created.body.toString())
            val entry = FieldPackCodec.decode(Files.readAllBytes(output)).files.single()
            assertEquals("multichunk.bin", entry.name)
            assertEquals(131073L, entry.size)
            val verified = host.cli("verify", "--manifest", output.toString())
            assertEquals(0, verified.exit, verified.body.toString())
            assertEquals(listOf("MATCH"), outcomes(verified))
        }
        val oracle = ProcessBuilder("python3", Path.of(System.getProperty("boundarylab.project.root"), "examples/fieldpack/fieldpack.py").toString(),
            "verify", "--root", root.toString(), "--manifest", output.toString()).redirectErrorStream(true).start()
        val text = oracle.inputStream.bufferedReader().readText()
        assertTrue(oracle.waitFor(10, TimeUnit.SECONDS))
        assertEquals(0, oracle.exitValue(), text)
        assertEquals(listOf("MATCH"), outcomes(Run(0, Wire.decode(text.toByteArray()))))
        assertArrayEquals(binary, Files.readAllBytes(root.resolve("multichunk.bin")))
    }

    @Test fun `per-file and remaining aggregate budget prevent publication`() {
        val root = root()
        val large = root.resolve("large.bin")
        java.io.RandomAccessFile(large.toFile(), "rw").use { it.setLength(134217729) }
        Host(root).use { host ->
            val output = temporary.resolve("oversize.json")
            assertEquals(2, host.cli("create", "--name", "large.bin", "--output", output.toString()).exit)
            assertFalse(Files.exists(output))
            java.io.RandomAccessFile(large.toFile(), "rw").use { it.setLength(134217728) }
            // abc.txt consumes 3 bytes first; the next file exceeds remaining pack budget.
            assertEquals(2, host.cli("create", "--name", "abc.txt", "--name", "large.bin", "--output", output.toString()).exit)
            assertFalse(Files.exists(output))
        }
    }

    @Test fun `generated shared corpus crosses read boundaries and exact byte cap`() {
        val corpusPath = Path.of(System.getProperty("boundarylab.project.root"), "conformance/shared/digest-v1.json")
        val cases = ObjectMapper().readValue(Files.readAllBytes(corpusPath), Map::class.java)["cases"] as List<*>
        Host(null).use { host ->
            for (rawCase in cases) {
                val case = rawCase as Map<*, *>
                val generator = case["generator"] as Map<*, *>
                if (generator["kind"] != "ramp") continue
                val result = host.cli("generated", "--bytes", case["size"].toString(), "--seed", generator["seed"].toString())
                assertEquals(0, result.exit, case["id"].toString())
                assertEquals(case["sha256"], result.body["sha256"], case["id"].toString())
            }
        }
    }

    @Test fun `concurrent publication has exactly one complete winner`() {
        val output = temporary.resolve("contended.json")
        val variants = listOf(manifest(), FieldPackCodec.encode(FieldPackManifest(listOf(FieldPackEntry("empty.bin", 0, empty)))))
        val barrier = java.util.concurrent.CyclicBarrier(2)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val attempts = variants.map { bytes -> pool.submit<Boolean> {
                barrier.await(5, TimeUnit.SECONDS)
                runCatching { publishManifest(output, bytes) }.isSuccess
            } }
            assertEquals(1, attempts.count { it.get(5, TimeUnit.SECONDS) })
            val published = Files.readAllBytes(output)
            assertTrue(variants.any { it.contentEquals(published) })
            FieldPackCodec.decode(published)
            assertEquals(listOf(output), Files.list(temporary).use { it.toList() })
        } finally { pool.shutdownNow() }
    }

    @Test fun `atomic publisher refuses existing completed winner`() {
        val output = temporary.resolve("race.json")
        val winner = "existing".toByteArray()
        Files.write(output, winner)
        assertThrows(Exception::class.java) { publishManifest(output, manifest()) }
        assertArrayEquals(winner, Files.readAllBytes(output))
        assertEquals(listOf(output), Files.list(temporary).use { it.toList() })
    }
}
