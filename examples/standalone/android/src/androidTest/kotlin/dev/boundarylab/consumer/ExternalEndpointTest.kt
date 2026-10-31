package dev.boundarylab.consumer

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.boundarylab.sdk.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Published-artifact consumer: real installed packages and SDK; no fake binding. */
@RunWith(AndroidJUnit4::class)
class ExternalEndpointTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val endpoint = BrokerEndpoint(ComponentName(BuildConfig.BROKER_PACKAGE, BuildConfig.BROKER_SERVICE), BuildConfig.BIND_PERMISSION)
    private fun <T> await(action: ((Result<T>) -> Unit) -> Unit): Result<T> {
        val result = AtomicReference<Result<T>>()
        val latch = CountDownLatch(1)
        action { result.set(it); latch.countDown() }
        assertTrue("SDK callback missing", latch.await(10, TimeUnit.SECONDS))
        return result.get()
    }
    @Test fun sameSignerSeparateUidRunsRealSubmitAndStatus() {
        val brokerUid = context.packageManager.getApplicationInfo(BuildConfig.BROKER_PACKAGE, 0).uid
        assertNotEquals("Broker must be a separately installed UID", context.applicationInfo.uid, brokerUid)
        assertEquals(PackageManager.SIGNATURE_MATCH, context.packageManager.checkSignatures(context.applicationInfo.uid, brokerUid))
        val client = BoundaryClient(context, endpoint)
        try {
            val caps = await<BrokerCapabilities> { client.connect(it) }.getOrThrow()
            assertNotEquals("Broker must execute in another process", android.os.Process.myPid(), caps.brokerPid)
            val handle = await<JobHandle> { client.submitGenerated(0, 1, callback = it) }.getOrThrow()
            var snapshot = await<JobSnapshot> { client.status(handle, it) }.getOrThrow()
            val until = SystemClock.elapsedRealtime() + 10000
            while (!snapshot.isTerminal && SystemClock.elapsedRealtime() < until) {
                SystemClock.sleep(300)
                snapshot = await<JobSnapshot> { client.status(handle, it) }.getOrThrow()
            }
            assertEquals("SUCCEEDED", snapshot.state)
            assertEquals(1L, snapshot.bytesProcessed)
            assertEquals("6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d", snapshot.sha256)
            android.util.Log.i("ExternalEndpointQA", "POSITIVE clientUid=${context.applicationInfo.uid} brokerUid=$brokerUid clientPid=${android.os.Process.myPid()} brokerPid=${caps.brokerPid} sha256=${snapshot.sha256}")
        } finally { client.close() }
    }
    @Test fun originalDefaultConstructorRunsAgainstDefaultInstalledBroker() {
        val client = BoundaryClient(context)
        try {
            await<BrokerCapabilities> { client.connect(it) }.getOrThrow()
            val handle = await<JobHandle> { client.submitGenerated(0, 1, callback = it) }.getOrThrow()
            val snapshot = terminal(client, handle)
            assertEquals("SUCCEEDED", snapshot.state)
            assertEquals("6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d", snapshot.sha256)
        } finally { client.close() }
    }
    @Test fun actualReadOnlyFileDescriptorProducesRecipientVerifiableManifest() {
        val file = File(context.cacheDir, "inspection.txt").apply { writeText("BoundaryLab reusable document fixture\n") }
        val client = BoundaryClient(context, endpoint)
        try {
            await<BrokerCapabilities> { client.connect(it) }.getOrThrow()
            val input = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val handle = await<JobHandle> { client.submitFile(input, "inspection.txt", callback = it) }.getOrThrow()
            assertFalse("SDK consumes its local descriptor", input.fileDescriptor.valid())
            val snapshot = terminal(client, handle)
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals("SUCCEEDED", snapshot.state)
            assertEquals(file.length(), snapshot.bytesProcessed)
            assertEquals(digest, snapshot.sha256)
            val manifest = dev.boundarylab.core.FieldPackManifest(listOf(dev.boundarylab.core.FieldPackEntry("inspection.txt", file.length(), digest)))
            assertEquals(manifest, dev.boundarylab.core.FieldPackCodec.decode(dev.boundarylab.core.FieldPackCodec.encode(manifest)))
            android.util.Log.i("ExternalEndpointQA", "DOCUMENT_OK bytes=${snapshot.bytesProcessed} sha256=$digest")
        } finally { client.close(); file.delete() }
    }
    private fun terminal(client: BoundaryClient, handle: JobHandle): JobSnapshot {
        var snapshot = await<JobSnapshot> { client.status(handle, it) }.getOrThrow()
        val until = SystemClock.elapsedRealtime() + 10000
        while (!snapshot.isTerminal && SystemClock.elapsedRealtime() < until) {
            SystemClock.sleep(300)
            snapshot = await<JobSnapshot> { client.status(handle, it) }.getOrThrow()
        }
        return snapshot
    }
    @Test fun wrongExpectedPermissionIsDeniedAgainstRealInstalledService() {
        val actual = context.packageManager.getServiceInfo(endpoint.component, 0)
        assertEquals(BuildConfig.BIND_PERMISSION, actual.permission)
        denied(endpoint.copy(bindPermission = "android.permission.INTERNET"))
    }
    @Test fun installedWrongSignerBrokerIsDeniedAndDescriptorClosed() {
        org.junit.Assume.assumeTrue("Separate negative phase requires a differently signed broker",
            InstrumentationRegistry.getArguments().getString("expectedWrongSigner") == "true")
        val brokerUid = context.packageManager.getApplicationInfo(BuildConfig.BROKER_PACKAGE, 0).uid
        assertNotEquals(context.applicationInfo.uid, brokerUid)
        assertEquals(PackageManager.SIGNATURE_NO_MATCH, context.packageManager.checkSignatures(context.applicationInfo.uid, brokerUid))
        denied(endpoint)
    }
    private fun denied(target: BrokerEndpoint) {
        val client = BoundaryClient(context, target)
        val file = File.createTempFile("external-denied", ".bin", context.cacheDir)
        try {
            val result = await<BrokerCapabilities> { client.connect(it) }
            assertEquals("DENIED", (result.exceptionOrNull() as? BoundaryException)?.code)
            val input = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            assertTrue(await<JobHandle> { client.submitFile(input, "empty.bin", callback = it) }.isFailure)
            assertFalse(input.fileDescriptor.valid())
            android.util.Log.i("ExternalEndpointQA", "DENIED package=${target.component.packageName} expectedPermission=${target.bindPermission} descriptorClosed=true")
        } finally { client.close(); file.delete() }
    }
}
