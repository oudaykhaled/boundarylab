package dev.boundarylab.broker

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.test.mock.MockPackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.boundarylab.sdk.BoundaryClient
import dev.boundarylab.sdk.BoundaryException
import dev.boundarylab.sdk.BrokerCapabilities
import dev.boundarylab.sdk.BrokerEndpoint
import dev.boundarylab.sdk.JobHandle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** SDK identity regressions. Controlled transports below are not cross-package Binder evidence. */
@RunWith(AndroidJUnit4::class)
class BrokerEndpointTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val installed = BrokerEndpoint(
        ComponentName(base.packageName, "dev.boundarylab.broker.BoundaryBrokerService"),
        BuildConfig.BROKER_BIND_PERMISSION
    )

    @Test fun defaultValuesAndOriginalJvmConstructorRemainAvailable() {
        assertEquals(ComponentName("dev.boundarylab.broker", "dev.boundarylab.broker.BoundaryBrokerService"),
            BrokerEndpoint.DEFAULT.component)
        assertEquals("dev.boundarylab.permission.BIND_BROKER", BrokerEndpoint.DEFAULT.bindPermission)
        val client = BoundaryClient::class.java.getConstructor(Context::class.java).newInstance(base)
        client.close()
    }

    @Test fun explicitInstalledEndpointConnectsUsingStableServiceClass() {
        val client = BoundaryClient(base, installed)
        try {
            val result = awaitResult<BrokerCapabilities> { client.connect(it) }
            assertTrue(result.toString(), result.isSuccess)
            assertTrue(result.getOrThrow().operations.contains("generated-sha256-v1"))
        } finally { client.close() }
    }

    @Test fun malformedEndpointNamesAreRejectedBeforeClientCreation() {
        for (endpoint in listOf(
            Triple("", "dev.example.Broker", "dev.example.BIND"),
            Triple("dev.example", ".Broker", "dev.example.BIND"),
            Triple("dev.example", "dev.example.Broker", ""),
            Triple("dev.example bad", "dev.example.Broker", "dev.example.BIND"),
            Triple("dev.example", "dev.example.Broker", "dev.example.BIND\"permission")
        )) {
            try {
                BrokerEndpoint(ComponentName(endpoint.first, endpoint.second), endpoint.third)
                fail("Malformed endpoint was accepted: $endpoint")
            } catch (_: IllegalArgumentException) { /* rejected before bind */ }
        }
    }

    @Test fun missingIdentityIsDeniedBeforeBindingAndTransferredDescriptorIsClosed() {
        // ADR-B07: inability to establish identity is DENIED, including a missing service.
        val endpoint = installed.copy(component = ComponentName(base.packageName, "dev.boundarylab.broker.MissingService"))
        assertDeniedBeforeBinding(endpoint)
    }

    @Test fun mismatchedExpectedPermissionIsDeniedBeforeBindingAndDescriptorDelegation() {
        assertDeniedBeforeBinding(installed.copy(bindPermission = "android.permission.INTERNET"))
    }

    @Test fun missingExpectedPermissionIsDeniedBeforeBinding() {
        assertDeniedBeforeBinding(installed.copy(bindPermission = "dev.boundarylab.missing.BIND_BROKER"))
    }

    @Test fun permissionOwnedByAnotherPackageIsDeniedBeforeBinding() {
        assertMetadataDenied(permissionChange = { packageName = "dev.boundarylab.foreign" })
    }

    @Test fun normalProtectionPermissionIsDeniedBeforeBinding() {
        assertMetadataDenied(permissionChange = { protectionLevel = PermissionInfo.PROTECTION_NORMAL })
    }

    @Test fun disabledServiceIsDeniedBeforeBinding() {
        assertMetadataDenied(serviceChange = { enabled = false })
    }

    @Test fun disabledApplicationIsDeniedBeforeBinding() {
        assertMetadataDenied(serviceChange = { applicationInfo.enabled = false })
    }

    @Test fun unexpectedCallbackComponentIsDeniedBeforeHandshake() {
        val context = object : RecordingContext(base) {
            override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
                binds.incrementAndGet()
                Handler(Looper.getMainLooper()).post {
                    conn.onServiceConnected(ComponentName(base.packageName, "dev.boundarylab.broker.UnexpectedService"), Binder())
                }
                return true
            }
        }
        val client = BoundaryClient(context, installed)
        try {
            assertDenied(awaitResult<BrokerCapabilities> { client.connect(it) })
            assertEquals(1, context.binds.get())
        } finally { client.close() }
    }

    @Test fun identityIsRecheckedAfterBindingBeforeAnyHandshakeTransaction() {
        val identityChecks = AtomicInteger()
        val transactions = AtomicInteger()
        val binder = object : Binder() {
            override fun onTransact(code: Int, data: android.os.Parcel, reply: android.os.Parcel?, flags: Int): Boolean {
                transactions.incrementAndGet()
                return super.onTransact(code, data, reply, flags)
            }
        }
        val context = object : RecordingContext(base) {
            override fun getPackageManager(): PackageManager {
                if (identityChecks.incrementAndGet() > 1) throw SecurityException("Identity unavailable after bind")
                return base.packageManager
            }
            override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
                binds.incrementAndGet()
                Handler(Looper.getMainLooper()).post { conn.onServiceConnected(requireNotNull(service.component), binder) }
                return true
            }
        }
        val client = BoundaryClient(context, installed)
        try {
            assertDenied(awaitResult<BrokerCapabilities> { client.connect(it) })
            assertEquals(2, identityChecks.get())
            assertEquals(1, context.binds.get())
            assertEquals("Identity must be checked before capabilities/session IPC", 0, transactions.get())
        } finally { client.close() }
    }

    private open class RecordingContext(base: Context) : ContextWrapper(base) {
        val binds = AtomicInteger()
        override fun getApplicationContext(): Context = this
        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
            binds.incrementAndGet()
            return false
        }
        override fun unbindService(conn: ServiceConnection) = Unit
    }

    /**
     * Isolated SDK predicate fixtures, not installed package/boundary evidence. Each starts from
     * real, authorized metadata and changes exactly one value; signer comparison still delegates
     * to the installed PackageManager.
     */
    @Suppress("DEPRECATION")
    private fun assertMetadataDenied(
        serviceChange: ServiceInfo.() -> Unit = {},
        permissionChange: PermissionInfo.() -> Unit = {}
    ) {
        val manager = base.packageManager
        val service = ServiceInfo(manager.getServiceInfo(installed.component, 0)).apply {
            applicationInfo = ApplicationInfo(applicationInfo)
        }
        val permission = PermissionInfo(manager.getPermissionInfo(installed.bindPermission, 0))
        // Establish that no unchanged value could cause an earlier failure.
        assertTrue(service.enabled && service.applicationInfo.enabled && service.exported)
        assertEquals(installed.component, ComponentName(service.packageName, service.name))
        assertEquals(installed.component.packageName, service.applicationInfo.packageName)
        assertEquals(installed.bindPermission, service.permission)
        assertEquals(installed.component.packageName, permission.packageName)
        assertEquals(PermissionInfo.PROTECTION_SIGNATURE, permission.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE)
        assertEquals(PackageManager.SIGNATURE_MATCH,
            manager.checkSignatures(base.applicationInfo.uid, service.applicationInfo.uid))
        service.serviceChange()
        permission.permissionChange()
        val fixture = object : MockPackageManager() {
            override fun getServiceInfo(component: ComponentName, flags: Int): ServiceInfo {
                assertEquals(installed.component, component)
                return service
            }
            override fun getPermissionInfo(name: String, flags: Int): PermissionInfo {
                assertEquals(installed.bindPermission, name)
                return permission
            }
            override fun checkSignatures(uid1: Int, uid2: Int): Int = manager.checkSignatures(uid1, uid2)
        }
        val context = object : RecordingContext(base) {
            override fun getPackageManager(): PackageManager = fixture
        }
        assertDeniedBeforeBinding(installed, context)
    }

    private fun assertDeniedBeforeBinding(endpoint: BrokerEndpoint, context: RecordingContext = RecordingContext(base)) {
        val client = BoundaryClient(context, endpoint)
        val file = File.createTempFile("endpoint-denied", ".bin", base.cacheDir)
        try {
            assertDenied(awaitResult<BrokerCapabilities> { client.connect(it) })
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val submitted = awaitResult<JobHandle> { client.submitFile(descriptor, "empty.bin", callback = it) }
            assertTrue(submitted.isFailure)
            assertFalse("Rejected submission must close its owned local PFD", descriptor.fileDescriptor.valid())
            assertEquals("Identity denial must precede bind", 0, context.binds.get())
        } finally { client.close(); file.delete() }
    }

    private fun assertDenied(result: Result<*>) {
        assertEquals("DENIED", (result.exceptionOrNull() as? BoundaryException)?.code)
    }

    private fun <T> awaitResult(action: ((Result<T>) -> Unit) -> Unit): Result<T> {
        val result = AtomicReference<Result<T>>()
        val latch = CountDownLatch(1)
        action { result.set(it); latch.countDown() }
        assertTrue("SDK callback missing", latch.await(8, TimeUnit.SECONDS))
        return result.get()
    }
}
