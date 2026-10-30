package dev.boundarylab.broker

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.boundarylab.ipc.IBoundaryBroker
import dev.boundarylab.sdk.BoundaryClient
import dev.boundarylab.sdk.BoundaryException
import dev.boundarylab.sdk.JobHandle
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Deterministic SDK control-flow tests with fake transport, not real Binder boundary evidence. */
@RunWith(AndroidJUnit4::class)
class SdkLifecycleTest {
    private class FakeBroker : IBoundaryBroker.Stub() {
        val epoch = UUID.randomUUID().toString()
        var session = UUID.randomUUID().toString()
        val job = UUID.randomUUID().toString()
        var request = UUID.randomUUID().toString()
        @Volatile var failStatus = false
        @Volatile var statusGate: CountDownLatch? = null
        @Volatile var submitGate: CountDownLatch? = null
        val statusEntered = CountDownLatch(1)
        val submitEntered = CountDownLatch(1)
        val submits = AtomicInteger()
        val lookups = AtomicInteger()
        val cancels = AtomicInteger()
        val statusTimes = Collections.synchronizedList(mutableListOf<Long>())
        private fun ok() = JSONObject().put("ok", true)
        private fun jobReply() = ok().put("epoch", epoch).put("sessionId", session)
            .put("jobId", job).put("requestId", request).put("state", "RUNNING")
            .put("bytesProcessed", 0).put("sha256", JSONObject.NULL).put("error", JSONObject.NULL)
            .put("cancelRequested", false).toString()
        override fun capabilities() = ok().put("epoch", epoch).put("protocolMajor", 1)
            .put("protocolMinor", 0).put("operations", JSONArray(listOf("generated-sha256-v1")))
            .put("maxBytes", 134217728).put("brokerPid", android.os.Process.myPid()).toString()
        override fun openSession(clientToken: IBinder?, protocolMajor: Int): String {
            session = UUID.randomUUID().toString()
            return ok().put("epoch", epoch).put("sessionId", session).toString()
        }
        override fun submit(epoch: String?, sessionId: String?, requestId: String?, operation: String?,
                            totalBytes: Long, seed: Long, deadlineMillis: Long, alias: String?, input: ParcelFileDescriptor?): String {
            request = requireNotNull(requestId)
            submits.incrementAndGet(); submitEntered.countDown()
            submitGate?.await(10, TimeUnit.SECONDS)
            return jobReply()
        }
        override fun status(epoch: String?, sessionId: String?, jobId: String?): String {
            statusTimes += SystemClock.elapsedRealtime()
            statusEntered.countDown()
            statusGate?.await(10, TimeUnit.SECONDS)
            if (failStatus) throw RemoteException("Injected transport loss")
            return jobReply()
        }
        override fun statusByRequest(epoch: String?, sessionId: String?, requestId: String?): String {
            lookups.incrementAndGet(); return jobReply()
        }
        override fun cancel(epoch: String?, sessionId: String?, jobId: String?): String {
            cancels.incrementAndGet(); return jobReply()
        }
        override fun closeSession(epoch: String?, sessionId: String?) = ok().toString()
    }

    private class FakeBindingContext(base: Context, val broker: FakeBroker) : ContextWrapper(base) {
        val binds = AtomicInteger()
        override fun getApplicationContext(): Context = this
        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
            binds.incrementAndGet()
            Handler(Looper.getMainLooper()).post { conn.onServiceConnected(requireNotNull(service.component), broker) }
            return true
        }
        override fun unbindService(conn: ServiceConnection) = Unit
    }

    private fun client(broker: FakeBroker): BoundaryClient {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = BoundaryClient(FakeBindingContext(context, broker))
        awaitResult<dev.boundarylab.sdk.BrokerCapabilities> { client.connect(it) }.getOrThrow()
        return client
    }

    private fun <T> awaitResult(action: ((Result<T>) -> Unit) -> Unit): Result<T> {
        val result = AtomicReference<Result<T>>()
        val latch = CountDownLatch(1)
        action { result.set(it); latch.countDown() }
        assertTrue("SDK callback missing", latch.await(8, TimeUnit.SECONDS))
        return result.get()
    }

    private fun submitted(client: BoundaryClient): JobHandle = awaitResult<JobHandle> {
        client.submitGenerated(0, 3, callback = it)
    }.getOrThrow()

    @Test fun remoteFailureSettlesOnceAndDelayedMainReceivesLossBeforeRecovery() {
        val broker = FakeBroker()
        val client = client(broker)
        val releaseMain = CountDownLatch(1)
        try {
            val handle = submitted(client)
            val events = Collections.synchronizedList(mutableListOf<String>())
            val observed = CountDownLatch(3)
            client.onLoss = { events += "loss"; observed.countDown() }
            val mainBlocked = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post { mainBlocked.countDown(); releaseMain.await(3, TimeUnit.SECONDS) }
            assertTrue(mainBlocked.await(2, TimeUnit.SECONDS))
            broker.failStatus = true
            val calls = AtomicInteger()
            client.status(handle) {
                assertEquals("LOST_OR_UNKNOWN", (it.exceptionOrNull() as BoundaryException).code)
                calls.incrementAndGet(); events += "failed"; observed.countDown()
            }
            assertTrue(broker.statusEntered.await(2, TimeUnit.SECONDS))
            Thread.sleep(450) // Let retry change generation while Android main is deliberately blocked.
            client.connect { assertTrue(it.isSuccess); events += "ready"; observed.countDown() }
            releaseMain.countDown()
            assertTrue(observed.await(8, TimeUnit.SECONDS))
            assertEquals(1, calls.get())
            assertTrue(events.indexOf("loss") < events.indexOf("ready"))
        } finally { releaseMain.countDown(); client.close() }
    }

    @Test fun blockedTransportBoundsPublicIngressAndSpacesSubsequentPolls() {
        val broker = FakeBroker()
        val client = client(broker)
        val gate = CountDownLatch(1)
        try {
            val handle = submitted(client)
            broker.statusGate = gate
            val completed = CountDownLatch(101)
            client.status(handle) { completed.countDown() }
            assertTrue(broker.statusEntered.await(2, TimeUnit.SECONDS))
            repeat(100) { client.status(handle) { completed.countDown() } }
            val pending = BoundaryClient::class.java.getDeclaredField("pending").apply { isAccessible = true }
                .get(client) as AtomicInteger
            assertTrue(pending.get() <= 32)
            gate.countDown()
            assertTrue(completed.await(8, TimeUnit.SECONDS))
            assertEquals(0, pending.get())
            val times = broker.statusTimes.toList()
            assertTrue(times.size >= 2)
            times.zipWithNext().forEach { (first, second) -> assertTrue(second - first >= 250) }
        } finally { gate.countDown(); client.close() }
    }

    @Test fun oldGenerationPollCleanupCannotRemoveNewGenerationSameRequestSlot() {
        val broker = FakeBroker()
        val client = client(broker)
        val release = CountDownLatch(1)
        try {
            val original = submitted(client)
            awaitResult<dev.boundarylab.sdk.JobSnapshot> { client.status(original, it) }.getOrThrow()
            val worker = BoundaryClient::class.java.getDeclaredField("worker").apply { isAccessible = true }
                .get(client) as java.util.concurrent.ScheduledThreadPoolExecutor
            val blocked = CountDownLatch(1)
            worker.execute { blocked.countDown(); release.await(3, TimeUnit.SECONDS) }
            assertTrue(blocked.await(2, TimeUnit.SECONDS))
            val oldResult = AtomicReference<Result<dev.boundarylab.sdk.JobSnapshot>>()
            val oldDone = CountDownLatch(1)
            client.status(original) { oldResult.set(it); oldDone.countDown() }
            val installed = CountDownLatch(1)
            worker.execute {
                // Model a recovered generation with an outstanding poll using the same UUID.
                // Queue ordering makes this happen after old admission and before its delayed IPC.
                val generation = BoundaryClient::class.java.getDeclaredField("generation").apply { isAccessible = true }
                generation.setLong(client, original.generation + 1)
                @Suppress("UNCHECKED_CAST")
                val polling = BoundaryClient::class.java.getDeclaredField("polling").apply { isAccessible = true }
                    .get(client) as MutableSet<String>
                polling.clear()
                polling.add(original.requestId)
                installed.countDown()
            }
            release.countDown()
            assertTrue(installed.await(3, TimeUnit.SECONDS))
            assertTrue(oldDone.await(5, TimeUnit.SECONDS))
            assertEquals("LOST_OR_UNKNOWN", (oldResult.get().exceptionOrNull() as BoundaryException).code)
            val fresh = original.copy(generation = original.generation + 1)
            val concurrent = awaitResult<dev.boundarylab.sdk.JobSnapshot> { client.status(fresh, it) }
            assertEquals("BUSY", (concurrent.exceptionOrNull() as BoundaryException).code)
            assertEquals(1, broker.statusTimes.size)
        } finally { release.countDown(); client.close() }
    }

    @Test fun realPackageManagerSignerMismatchPreventsBindingAndDescriptorDelegation() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val platformUid = base.packageManager.getApplicationInfo("android", 0).uid
        val brokerUid = base.packageManager.getApplicationInfo("dev.boundarylab.broker", 0).uid
        assertEquals(android.content.pm.PackageManager.SIGNATURE_NO_MATCH,
            base.packageManager.checkSignatures(platformUid, brokerUid))
        val binds = AtomicInteger()
        // Controlled client identity wrapper; installed PackageManager performs real signature
        // comparison. This is an outgoing guard regression, not an installed rogue-broker test.
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getApplicationInfo() = android.content.pm.ApplicationInfo(base.applicationInfo).apply { uid = platformUid }
            override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
                binds.incrementAndGet(); return false
            }
            override fun unbindService(conn: ServiceConnection) = Unit
        }
        val client = BoundaryClient(context)
        val file = java.io.File.createTempFile("signer-rejected", ".bin", base.cacheDir)
        try {
            val connected = awaitResult<dev.boundarylab.sdk.BrokerCapabilities> { client.connect(it) }
            assertEquals("DENIED", (connected.exceptionOrNull() as BoundaryException).code)
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val submitted = awaitResult<JobHandle> { client.submitFile(descriptor, "empty.bin", callback = it) }
            assertTrue(submitted.isFailure)
            assertFalse(descriptor.fileDescriptor.valid())
            assertEquals(0, binds.get())
        } finally { client.close(); file.delete() }
    }

    @Test fun malformedMetadataIsRejectedBeforeTransportWithoutReconnectAndClosesFile() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val broker = FakeBroker()
        val context = FakeBindingContext(base, broker)
        val client = BoundaryClient(context)
        val file = java.io.File.createTempFile("invalid-metadata", ".bin", base.cacheDir)
        try {
            awaitResult<dev.boundarylab.sdk.BrokerCapabilities> { client.connect(it) }.getOrThrow()
            val generation = BoundaryClient::class.java.getDeclaredField("generation").apply { isAccessible = true }
            val initialGeneration = generation.getLong(client)
            val huge = "x".repeat(2_000_000)
            fun invalid(result: Result<*>) = assertEquals("INVALID", (result.exceptionOrNull() as BoundaryException).code)
            invalid(awaitResult<JobHandle> { client.submitGenerated(0, 3, requestId = huge, callback = it) })
            invalid(awaitResult<JobHandle> { client.submitGenerated(0, -1, callback = it) })
            invalid(awaitResult<JobHandle> { client.submitGenerated(0, 3, deadlineMs = 0, callback = it) })
            for (alias in listOf(huge, "con.txt", "UPPER.txt", "../a")) {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                invalid(awaitResult<JobHandle> { client.submitFile(pfd, alias, callback = it) })
                assertFalse(pfd.fileDescriptor.valid())
            }
            invalid(awaitResult<JobHandle> { client.statusByRequest(huge, it) })
            assertEquals(0, broker.submits.get())
            assertEquals(0, broker.lookups.get())
            val valid = submitted(client)
            for (malformed in listOf(valid.copy(epoch = huge), valid.copy(sessionId = huge),
                valid.copy(jobId = huge), valid.copy(requestId = huge))) {
                invalid(awaitResult<dev.boundarylab.sdk.JobSnapshot> { client.status(malformed, it) })
                invalid(awaitResult<dev.boundarylab.sdk.JobSnapshot> { client.cancel(malformed, it) })
            }
            assertEquals(1, broker.submits.get())
            assertEquals(0, broker.statusTimes.size)
            assertEquals(0, broker.cancels.get())
            assertEquals(initialGeneration, generation.getLong(client))
            assertEquals(1, context.binds.get())
            val pending = BoundaryClient::class.java.getDeclaredField("pending").apply { isAccessible = true }
                .get(client) as AtomicInteger
            assertEquals(0, pending.get())
        } finally { client.close(); file.delete() }
    }

    @Test fun submitTimeoutCompletesOnceWithoutReplayAndOriginalRequestRemainsQueryable() {
        val broker = FakeBroker()
        val client = client(broker)
        val gate = CountDownLatch(1)
        try {
            broker.submitGate = gate
            val request = UUID.randomUUID().toString()
            val calls = AtomicInteger()
            val reply = awaitResult<JobHandle> { callback ->
                client.submitGenerated(0, 3, requestId = request) { calls.incrementAndGet(); callback(it) }
            }
            assertEquals("TIMEOUT_UNKNOWN", (reply.exceptionOrNull() as BoundaryException).code)
            assertEquals(1, broker.submits.get())
            gate.countDown()
            val found = awaitResult<JobHandle> { client.statusByRequest(request, it) }.getOrThrow()
            assertEquals(request, found.requestId)
            assertEquals(1, broker.submits.get())
            assertEquals(1, calls.get())
        } finally { gate.countDown(); client.close() }
    }
}
