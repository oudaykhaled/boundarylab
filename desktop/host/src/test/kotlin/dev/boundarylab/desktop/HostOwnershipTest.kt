package dev.boundarylab.desktop

import dev.boundarylab.core.BoundaryException
import dev.boundarylab.core.CoreEngine
import dev.boundarylab.core.ErrorCode
import dev.boundarylab.core.FILE_OPERATION
import dev.boundarylab.core.JobRequest
import dev.boundarylab.core.JobState
import dev.boundarylab.core.PositionalSource
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** Actual native handles cross the ownership-transfer seam into the production core. */
class HostOwnershipTest {
    @TempDir lateinit var temporary: Path

    @Test fun `duplicates policy rejections terminal and session cleanup release native slots`() {
        Files.writeString(temporary.resolve("abc.txt"), "abc")
        RootedFileProvider(temporary).use { provider ->
            CoreEngine(enableFileOperation = true, autoStart = false).use { engine ->
                val owner = "test-transport-owner"
                val session = engine.openSession(owner)
                val request = request()
                val original = provider.open("abc.txt")
                val accepted = engine.submit(owner, session.sessionId, session.epoch, request, original)
                assertEquals(1, engine.metrics().ownedSources)
                assertAvailableSlots(provider, 16)

                val duplicate = provider.open("abc.txt")
                assertEquals(accepted.jobId, engine.submit(owner, session.sessionId, session.epoch, request, duplicate).jobId)
                assertClosed(duplicate)
                assertAvailableSlots(provider, 16)

                val conflict = provider.open("abc.txt")
                assertEquals(ErrorCode.CONFLICT, assertThrows(BoundaryException::class.java) {
                    engine.submit(owner, session.sessionId, session.epoch, request.copy(totalBytes = 2), conflict)
                }.code)
                assertClosed(conflict)

                val invalid = provider.open("abc.txt")
                assertEquals(ErrorCode.INVALID, assertThrows(BoundaryException::class.java) {
                    engine.submit(owner, session.sessionId, session.epoch, request().copy(totalBytes = -1), invalid)
                }.code)
                assertClosed(invalid)

                val denied = provider.open("abc.txt")
                assertEquals(ErrorCode.DENIED, assertThrows(BoundaryException::class.java) {
                    engine.submit("different-transport-owner", session.sessionId, session.epoch, request(), denied)
                }.code)
                assertClosed(denied)
                assertAvailableSlots(provider, 16)

                assertTrue(engine.runNext())
                val complete = engine.status(owner, session.sessionId, session.epoch, accepted.jobId)
                assertEquals(JobState.SUCCEEDED, complete.state)
                assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", complete.digest)
                assertClosed(original)
                assertAvailableSlots(provider, 17)

                val cancelledSource = provider.open("abc.txt")
                val queued = engine.submit(owner, session.sessionId, session.epoch, request(), cancelledSource)
                assertEquals(JobState.CANCELLED, engine.cancel(owner, session.sessionId, session.epoch, queued.jobId).state)
                assertClosed(cancelledSource)
                assertAvailableSlots(provider, 17)

                val dyingSource = provider.open("abc.txt")
                engine.submit(owner, session.sessionId, session.epoch, request(), dyingSource)
                engine.closeSession(owner, session.sessionId, session.epoch)
                assertClosed(dyingSource)
                assertEquals(0, engine.metrics().ownedSources)
                assertAvailableSlots(provider, 17)
            }
        }
    }

    @Test fun `queue saturation closes the seventeenth incoming source and shutdown recovers all slots`() {
        Files.writeString(temporary.resolve("abc.txt"), "abc")
        RootedFileProvider(temporary).use { provider ->
            CoreEngine(enableFileOperation = true, autoStart = false).use { engine ->
                val owner = "test-transport-owner"
                val sessions = List(5) { engine.openSession(owner) }
                val sources = mutableListOf<PositionalSource>()
                sessions.take(4).forEach { session ->
                    repeat(4) {
                        val source = provider.open("abc.txt")
                        sources += source
                        engine.submit(owner, session.sessionId, session.epoch, request(), source)
                    }
                }
                assertEquals(16, engine.metrics().queuedJobs)
                assertEquals(16, engine.metrics().ownedSources)
                val pending = provider.open("abc.txt")
                val last = sessions.last()
                assertEquals(ErrorCode.BUSY, assertThrows(BoundaryException::class.java) {
                    engine.submit(owner, last.sessionId, last.epoch, request(), pending)
                }.code)
                assertClosed(pending)
                assertAvailableSlots(provider, 1)
                engine.close()
                sources.forEach(::assertClosed)
                assertEquals(0, engine.metrics().ownedSources)
                assertAvailableSlots(provider, 17)
            }
        }
    }

    private fun request() = JobRequest(UUID.randomUUID().toString(), FILE_OPERATION, 3, alias = "abc.txt")
    private fun assertClosed(source: PositionalSource) {
        assertThrows(IOException::class.java) { source.readAt(0, ByteArray(1), 0, 1) }
    }
    private fun assertAvailableSlots(provider: RootedFileProvider, count: Int) {
        val sources = mutableListOf<PositionalSource>()
        try {
            repeat(count) { sources += provider.open("abc.txt") }
            assertEquals("BUSY", assertThrows(DesktopProtocolException::class.java) { provider.open("abc.txt") }.code)
        } finally { sources.forEach { it.close() } }
    }
}
