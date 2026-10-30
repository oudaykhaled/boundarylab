package dev.boundarylab.broker

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.boundarylab.core.BoundaryException
import dev.boundarylab.core.CoreEngine
import dev.boundarylab.core.ErrorCode
import dev.boundarylab.core.FILE_OPERATION
import dev.boundarylab.core.JobRequest
import dev.boundarylab.core.JobState
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Actual Android descriptors; these tests do not claim cross-package Binder authorization proof. */
@RunWith(AndroidJUnit4::class)
class AndroidFileSourceTest {
    private fun fixture(bytes: ByteArray = "abc".toByteArray()): File = File.createTempFile(
        "boundary-source", ".bin", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
    ).apply { writeBytes(bytes) }

    private fun readOnly(file: File) = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

    @Test fun positionalReadIgnoresAndPreservesSharedCursor() {
        val file = fixture()
        try {
            readOnly(file).use { original ->
                val duplicated = ParcelFileDescriptor.dup(original.fileDescriptor)
                AndroidFileSource.take(duplicated).use { source ->
                    Os.lseek(original.fileDescriptor, 2, OsConstants.SEEK_SET)
                    val buffer = ByteArray(3)
                    assertEquals(3, source.readAt(0, buffer, 0, 3))
                    assertArrayEquals("abc".toByteArray(), buffer)
                    assertEquals(2L, Os.lseek(original.fileDescriptor, 0, OsConstants.SEEK_CUR))
                    assertEquals(-1, source.readAt(3, buffer, 0, 3))
                    assertTrue(source.verifyUnchanged())
                }
                assertTrue(original.fileDescriptor.valid())
            }
        } finally { file.delete() }
    }

    @Test fun rejectsWritablePipeAndDirectoryWithoutReading() {
        val file = fixture()
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).use { rejected(it) }
            val pipe = ParcelFileDescriptor.createPipe()
            try { rejected(pipe[0]); rejected(pipe[1]) } finally { pipe.forEach { it.close() } }
            val directory = Os.open(file.parentFile!!.absolutePath, OsConstants.O_RDONLY, 0)
            try { ParcelFileDescriptor.dup(directory).use { rejected(it) } } finally { Os.close(directory) }
        } finally { file.delete() }
    }

    @Test fun rejectsAlreadyUnlinkedOpenDescriptor() {
        val file = fixture()
        try {
            readOnly(file).use { descriptor ->
                assertTrue(file.delete())
                assertEquals(0L, Os.fstat(descriptor.fileDescriptor).st_nlink)
                rejected(descriptor)
            }
        } finally { file.delete() }
    }

    @Test fun rejectsHardlinkWhenTargetPolicyPermitsCreatingFixture() {
        val file = fixture()
        val link = File(file.parentFile, file.name + ".link")
        try {
            try { Os.link(file.absolutePath, link.absolutePath) }
            catch (failure: ErrnoException) {
                if (failure.errno != OsConstants.EACCES && failure.errno != OsConstants.EPERM) throw failure
                Assume.assumeNoException("Target policy denies owned hardlink fixture; nlink=2 case unverified", failure)
            }
            readOnly(file).use { descriptor ->
                assertEquals(2L, Os.fstat(descriptor.fileDescriptor).st_nlink)
                rejected(descriptor)
            }
        } finally { link.delete(); file.delete() }
    }

    private fun rejected(pfd: ParcelFileDescriptor) {
        try { AndroidFileSource.take(pfd).close(); fail("Unsupported descriptor admitted") }
        catch (_: IllegalArgumentException) { assertTrue(pfd.fileDescriptor.valid()) }
    }

    @Test fun metadataDetectsGrowthShrinkAndUnlink() {
        val file = fixture()
        try {
            AndroidFileSource.take(readOnly(file)).use { source ->
                file.appendText("d")
                assertFalse(source.verifyUnchanged())
            }
            AndroidFileSource.take(readOnly(file)).use { source ->
                file.writeText("a")
                assertFalse(source.verifyUnchanged())
            }
            AndroidFileSource.take(readOnly(file)).use { source ->
                assertTrue(file.delete())
                assertFalse(source.verifyUnchanged())
            }
        } finally { file.delete() }
    }

    @Test fun duplicateRetainsJobClosesIncomingAndDoesNotReadChangedDescriptor() {
        val file = fixture()
        val other = fixture("different size".toByteArray())
        try {
            CoreEngine(enableFileOperation = true, autoStart = false).use { engine ->
                val session = engine.openSession("instrumentation")
                val request = JobRequest(UUID.randomUUID().toString(), FILE_OPERATION, 3, alias = "abc.txt")
                val original = readOnly(file)
                val first = engine.submit("instrumentation", session.sessionId, session.epoch, request, AndroidFileSource.take(original))
                val duplicate = readOnly(other)
                val retained = engine.submit("instrumentation", session.sessionId, session.epoch, request, AndroidFileSource.take(duplicate))
                assertEquals(first.jobId, retained.jobId)
                assertFalse(duplicate.fileDescriptor.valid())
                assertTrue(original.fileDescriptor.valid())
                val cancelled = engine.cancel("instrumentation", session.sessionId, session.epoch, first.jobId)
                assertEquals(JobState.CANCELLED, cancelled.state)
                assertFalse(original.fileDescriptor.valid())
            }
        } finally { file.delete(); other.delete() }
    }

    @Test fun declaredSizeMismatchRejectsAndCloses() {
        val file = fixture()
        try {
            CoreEngine(enableFileOperation = true, autoStart = false).use { engine ->
                val session = engine.openSession("instrumentation")
                val pfd = readOnly(file)
                try {
                    engine.submit("instrumentation", session.sessionId, session.epoch,
                        JobRequest(UUID.randomUUID().toString(), FILE_OPERATION, 2, alias = "abc.txt"), AndroidFileSource.take(pfd))
                    fail("Length mismatch admitted")
                } catch (failure: BoundaryException) { assertEquals(ErrorCode.INVALID, failure.code) }
                assertFalse(pfd.fileDescriptor.valid())
            }
        } finally { file.delete() }
    }

    @Test fun hundredAdmissionRejectionCancellationCyclesRecoverDescriptors() {
        val file = fixture()
        try {
            val baseline = File("/proc/self/fd").list()!!.size
            CoreEngine(enableFileOperation = true, autoStart = false).use { engine ->
                repeat(100) {
                    val session = engine.openSession("instrumentation")
                    val descriptor = readOnly(file)
                    val job = engine.submit("instrumentation", session.sessionId, session.epoch,
                        JobRequest(UUID.randomUUID().toString(), FILE_OPERATION, 3, alias = "abc.txt"), AndroidFileSource.take(descriptor))
                    engine.closeSession("instrumentation", session.sessionId, session.epoch)
                    assertFalse("Cycle $it leaked ${job.jobId}", descriptor.fileDescriptor.valid())
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).use { rejected(it) }
                }
            }
            assertTrue("FD baseline did not recover", File("/proc/self/fd").list()!!.size <= baseline)
        } finally { file.delete() }
    }
}
