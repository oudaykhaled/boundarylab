package dev.boundarylab.nativeapp

import org.junit.Assert.*
import org.junit.Test

class PackAttemptTest {
    private val digest = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    @Test fun serializesSortedCompleteAttempt() {
        val attempt = PackAttempt(listOf("b.bin", "a.bin"))
        attempt.succeed("b.bin", 3, digest)
        assertFalse(attempt.complete)
        attempt.succeed("a.bin", 0, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        assertTrue(attempt.complete)
        val text = attempt.manifest().toString(Charsets.UTF_8)
        assertTrue(text.indexOf("a.bin") < text.indexOf("b.bin"))
        assertTrue(text.endsWith("\n"))
    }
    @Test fun cancellationDestroysEarlierResults() {
        val attempt = PackAttempt(listOf("a", "b"))
        attempt.succeed("a", 3, digest)
        attempt.invalidate()
        assertFalse(attempt.complete)
        assertEquals(0, attempt.count)
        assertThrows(IllegalStateException::class.java) { attempt.manifest() }
    }
    @Test fun aggregateLimitBlocksExport() {
        val attempt = PackAttempt(listOf("a", "b"))
        attempt.succeed("a", PackAttempt.MAX_BYTES, digest)
        assertThrows(IllegalArgumentException::class.java) { attempt.succeed("b", 1, digest) }
        assertFalse(attempt.complete)
    }
    @Test fun rejectsUnsafeAndDuplicateAliases() {
        listOf("../a", "CON", "con.txt", "a.", "lpt9.txt", "a/b", "a%20b").forEach {
            assertFalse(it, PackAttempt.safeAlias(it))
        }
        assertThrows(IllegalArgumentException::class.java) { PackAttempt(listOf("a", "a")) }
        assertThrows(IllegalArgumentException::class.java) { PackAttempt(List(33) { PackAttempt.alias(it) }) }
    }
    @Test fun aNewAttemptCannotReusePartialResults() {
        val old = PackAttempt(listOf("a", "b"))
        old.succeed("a", 3, digest)
        old.invalidate()
        val fresh = PackAttempt(listOf("a", "b"))
        assertEquals(0, fresh.count)
        assertThrows(IllegalStateException::class.java) { fresh.manifest() }
    }
}
