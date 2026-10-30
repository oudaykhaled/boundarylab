package dev.boundarylab.deniedclient

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

/** Real cross-package bind; no SDK/session method is called before identity authorization. */
@RunWith(AndroidJUnit4::class)
class SignatureDenialTest {
    @Test
    fun differentlySignedClientIsDeniedBeforeObtainingBrokerBinder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val brokerPackage = "dev.boundarylab.broker"
        val permission = "dev.boundarylab.permission.BIND_BROKER"
        val component = ComponentName(brokerPackage, "$brokerPackage.BoundaryBrokerService")
        val manager = context.packageManager
        @Suppress("DEPRECATION")
        val service = manager.getServiceInfo(component, 0)
        assertTrue("Broker service must be installed and enabled", service.enabled && service.applicationInfo.enabled)
        assertTrue("Broker service must be exported", service.exported)
        assertEquals(permission, service.permission)
        assertEquals("Probe must have a different signer", PackageManager.SIGNATURE_NO_MATCH,
            manager.checkSignatures(context.packageName, brokerPackage))
        assertEquals("Signature permission must not be granted", PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(permission))
        val connected = AtomicBoolean(false)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) { connected.set(true) }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        var bound = false
        try {
            try {
                bound = context.bindService(Intent().setComponent(component), connection, Context.BIND_AUTO_CREATE)
                fail("Expected SecurityException from signature-protected bind; returned $bound")
            } catch (_: SecurityException) {
                // Android rejected the cross-package bind before exposing the broker Binder.
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertFalse("Denied caller must never receive a broker Binder", connected.get())
        } finally {
            if (bound) context.unbindService(connection)
        }
    }
}
