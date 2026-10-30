package dev.boundarylab.nativeapp

import android.net.Uri
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.boundarylab.core.FieldPackCodec
import dev.boundarylab.sdk.BoundaryClient
import dev.boundarylab.sdk.BoundaryException
import dev.boundarylab.sdk.JobHandle
import dev.boundarylab.sdk.JobSnapshot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Requires the separately installed, same-signed broker. No engine runs in this test client. */
@RunWith(AndroidJUnit4::class)
class FieldPackFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun generatedDigestTraversesSeparateBrokerProcess() {
        val client = BoundaryClient(context)
        try {
            val caps = await { client.connect(it) }
            assertNotEquals(Process.myPid(), caps.brokerPid)
            assertTrue(caps.operations.contains("generated-sha256-v1"))
            Log.i("BoundaryLabEvidence", "client=dev.boundarylab.nativeclient pid=${Process.myPid()} broker=dev.boundarylab.broker pid=${caps.brokerPid}")
            val handle = await<JobHandle> { client.submitGenerated(0, 65_537, callback = it) }
            val result = terminal(client, handle)
            val oracle = MessageDigest.getInstance("SHA-256").digest(ByteArray(65_537) { it.toByte() })
                .joinToString("") { "%02x".format(it) }
            assertEquals("SUCCEEDED", result.state)
            assertEquals(oracle, result.sha256)
            assertEquals(65_537L, result.bytesProcessed)
        } finally { client.close() }
    }

    @Test fun twoActualFilesProduceCompleteManifest() {
        val abc = fixture("abc.txt", "abc")
        val empty = fixture("empty.bin", "")
        val controller = controller()
        try {
            onMain { controller.select(listOf(Uri.fromFile(abc), Uri.fromFile(empty))); controller.prepare() }
            waitUntil { !controller.busy }
            onMain {
                assertTrue(controller.message, controller.exportReady)
                val entries = FieldPackCodec.decode(controller.manifest()).files
                assertEquals(listOf("attachment-001.bin", "attachment-002.bin"), entries.map { it.name })
                assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", entries[0].sha256)
                assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", entries[1].sha256)
            }
        } finally { onMain { controller.close() }; abc.delete(); empty.delete() }
    }

    @Test fun secondFileFailureBlocksExportAndRetryRehashesEveryFile() {
        val abc = fixture("first.txt", "abc")
        val missing = File(context.cacheDir, "missing-native-fixture").also { it.delete() }
        val controller = controller()
        try {
            onMain { controller.select(listOf(Uri.fromFile(abc), Uri.fromFile(missing))); controller.prepare() }
            waitUntil { !controller.busy }
            onMain { assertFalse(controller.exportReady); assertEquals(0, controller.completed) }
            abc.writeText("abd")
            missing.writeText("")
            onMain { controller.prepare(); assertFalse(controller.exportReady); assertEquals(0, controller.completed) }
            waitUntil { !controller.busy }
            onMain {
                assertTrue(controller.message, controller.exportReady)
                val changedDigest = FieldPackCodec.decode(controller.manifest()).files.first().sha256
                assertNotEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", changedDigest)
            }
        } finally { onMain { controller.close() }; abc.delete(); missing.delete() }
    }

    @Test fun cancellationInvalidatesExportBeforeAcknowledgement() {
        val file = fixture("cancel.txt", "abc")
        val controller = controller()
        try {
            onMain {
                controller.select(listOf(Uri.fromFile(file)))
                controller.prepare()
                controller.cancel()
                assertTrue(controller.busy)
                assertFalse(controller.exportReady)
                assertTrue(controller.message.startsWith("Cancellation requested"))
            }
            waitUntil { !controller.busy }
            onMain { assertFalse(controller.exportReady); assertTrue(controller.message.startsWith("Cancelled")) }
        } finally { onMain { controller.close() }; file.delete() }
    }

    @Test fun handlesCannotBeReusedByANewClientSession() {
        val first = BoundaryClient(context)
        val second = BoundaryClient(context)
        try {
            await { first.connect(it) }
            val handle = await<JobHandle> { first.submitGenerated(0, 1, callback = it) }
            await { second.connect(it) }
            val response = awaitResult<JobSnapshot> { second.status(handle, it) }
            assertTrue(response.isFailure)
            assertEquals("LOST_OR_UNKNOWN", (response.exceptionOrNull() as BoundaryException).code)
        } finally { first.close(); second.close() }
    }

    @Test fun disposalClosesDescriptorAlreadyQueuedForMainDelivery() {
        val uri = Uri.parse("content://dev.boundarylab.nativeclient.test.delayed/fixture")
        val resolver = context.contentResolver
        resolver.call(uri, "reset", null, null)
        val controller = controller()
        val mainEntered = CountDownLatch(1)
        val allowClose = CountDownLatch(1)
        val closed = CountDownLatch(1)
        try {
            onMain { controller.select(listOf(uri)); controller.prepare() }
            val openUntil = SystemClock.elapsedRealtime() + 10_000
            while (resolver.call(uri, "state", null, null)?.getBoolean("opened") != true) {
                check(SystemClock.elapsedRealtime() < openUntil) { "Test provider was not opened" }
                SystemClock.sleep(25)
            }
            val baseline = descriptorsFor("delayed-owned-fixture.txt")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                mainEntered.countDown()
                check(allowClose.await(10, TimeUnit.SECONDS))
                controller.close()
                closed.countDown()
            }
            assertTrue(mainEntered.await(5, TimeUnit.SECONDS))
            resolver.call(uri, "release", null, null)
            val deliveredUntil = SystemClock.elapsedRealtime() + 5_000
            while (descriptorsFor("delayed-owned-fixture.txt") <= baseline) {
                check(SystemClock.elapsedRealtime() < deliveredUntil) { "Descriptor did not reach client process" }
                SystemClock.sleep(10)
            }
            // The main queue is blocked: give IO time to enqueue the owning delivery.
            SystemClock.sleep(150)
            allowClose.countDown()
            assertTrue(closed.await(5, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            assertEquals("Stale delivery must close its descriptor", baseline, descriptorsFor("delayed-owned-fixture.txt"))
        } finally {
            allowClose.countDown()
            resolver.call(uri, "release", null, null)
            onMain { controller.close() }
        }
    }

    @Test fun brokerDeathRequiresFreshEpochAndExplicitNewSubmission() {
        val client = BoundaryClient(context)
        val loss = CountDownLatch(1)
        client.onLoss = { code -> if (code == "LOST_OR_UNKNOWN") loss.countDown() }
        try {
            val before = await { client.connect(it) }
            val old = await<JobHandle> { client.submitGenerated(0, 134_217_728, callback = it) }
            // QA serializes this test with other broker users; stop only our owned package.
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("am force-stop dev.boundarylab.broker")
            ).use { stream -> stream.read(ByteArray(1024)) }
            assertTrue("SDK did not report broker connection loss", loss.await(10, TimeUnit.SECONDS))
            val stale = awaitResult<JobSnapshot> { client.status(old, it) }
            assertTrue(stale.isFailure)
            assertEquals("LOST_OR_UNKNOWN", (stale.exceptionOrNull() as BoundaryException).code)
            val after = await { client.connect(it) }
            assertNotEquals("Restarted broker must create a new epoch", before.epoch, after.epoch)
            val replay = awaitResult<JobHandle> { client.statusByRequest(old.requestId, it) }
            assertTrue("The old request must not be replayed into a fresh epoch", replay.isFailure)
            assertEquals("EXPIRED_OR_UNKNOWN", (replay.exceptionOrNull() as BoundaryException).code)
            val fresh = await<JobHandle> { client.submitGenerated(0, 1, callback = it) }
            assertNotEquals(old.requestId, fresh.requestId)
            val result = terminal(client, fresh)
            assertEquals("SUCCEEDED", result.state)
            val oracle = MessageDigest.getInstance("SHA-256").digest(byteArrayOf(0)).joinToString("") { "%02x".format(it) }
            assertEquals(oracle, result.sha256)
        } finally { client.close() }
    }

    private fun descriptorsFor(suffix: String): Int = File("/proc/self/fd").listFiles().orEmpty().count {
        runCatching { android.system.Os.readlink(it.path).endsWith(suffix) }.getOrDefault(false)
    }

    private fun fixture(name: String, content: String) = File(context.cacheDir, name).apply { writeText(content) }
    private fun controller(): PackController {
        lateinit var value: PackController
        onMain { value = PackController(context) {} }
        return value
    }
    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun waitUntil(condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < until) {
            var done = false
            onMain { done = condition() }
            if (done) return
            SystemClock.sleep(50)
        }
        fail("Native workflow did not complete within test observation window")
    }
    private fun terminal(client: BoundaryClient, handle: JobHandle): JobSnapshot {
        val until = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < until) {
            val result = await<JobSnapshot> { client.status(handle, it) }
            if (result.isTerminal) return result
            SystemClock.sleep(300)
        }
        error("Broker job did not reach terminal state within test observation window")
    }
    private fun <T> await(call: ((Result<T>) -> Unit) -> Unit): T = awaitResult(call).getOrThrow()
    private fun <T> awaitResult(call: ((Result<T>) -> Unit) -> Unit): Result<T> {
        val latch = CountDownLatch(1)
        var result: Result<T>? = null
        call { result = it; latch.countDown() }
        check(latch.await(20, TimeUnit.SECONDS)) { "SDK callback observation timed out" }
        return checkNotNull(result)
    }
}
