package dev.boundarylab.broker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.boundarylab.ipc.IBoundaryBroker
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Requires separately installed, same-signer native debug APK. Real distinct UID session. */
@RunWith(AndroidJUnit4::class)
class CrossOwnerTest {
    @Test fun nativeOwnedSessionCannotBeReadOrCancelledByBrokerUid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = binding(context, ComponentName("dev.boundarylab.nativeclient", "dev.boundarylab.nativeapp.OwnerFixtureService"))
        val broker = binding(context, ComponentName("dev.boundarylab.broker", "dev.boundarylab.broker.BoundaryBrokerService"))
        try {
            val result = exchange(Messenger(fixture.first), 1)
            assertTrue("Native owner fixture failed", result.getBoolean("ok"))
            val nativeUid = context.packageManager.getApplicationInfo("dev.boundarylab.nativeclient", 0).uid
            assertNotEquals(context.applicationInfo.uid, nativeUid)
            val service = IBoundaryBroker.Stub.asInterface(broker.first)
            val epoch = requireNotNull(result.getString("epoch"))
            val session = requireNotNull(result.getString("sessionId"))
            val job = requireNotNull(result.getString("jobId"))
            val request = requireNotNull(result.getString("requestId"))
            for (reply in listOf(service.status(epoch, session, job), service.cancel(epoch, session, job),
                service.statusByRequest(epoch, session, request), service.closeSession(epoch, session))) {
                val decoded = JSONObject(reply)
                assertFalse(decoded.getBoolean("ok"))
                assertEquals("DENIED", decoded.getString("error"))
            }
        } finally {
            exchange(Messenger(fixture.first), 2)
            context.unbindService(fixture.second)
            context.unbindService(broker.second)
        }
    }

    @Test fun clientProcessDeathRevokesNativeTokenSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = binding(context, ComponentName("dev.boundarylab.nativeclient", "dev.boundarylab.nativeapp.OwnerFixtureService"))
        val broker = binding(context, ComponentName("dev.boundarylab.broker", "dev.boundarylab.broker.BoundaryBrokerService"))
        try {
            val result = exchange(Messenger(fixture.first), 1)
            assertTrue(result.getBoolean("ok"))
            val service = IBoundaryBroker.Stub.asInterface(broker.first)
            val epoch = requireNotNull(result.getString("epoch"))
            val session = requireNotNull(result.getString("sessionId"))
            val job = requireNotNull(result.getString("jobId"))
            assertEquals("DENIED", JSONObject(service.status(epoch, session, job)).getString("error"))
            val died = CountDownLatch(1)
            fixture.first.linkToDeath({ died.countDown() }, 0)
            Messenger(fixture.first).send(Message.obtain(null, 3))
            assertTrue("Native fixture process did not die", died.await(8, TimeUnit.SECONDS))
            val until = android.os.SystemClock.elapsedRealtime() + 5_000
            var code: String
            do {
                code = JSONObject(service.status(epoch, session, job)).getString("error")
                if (code != "EXPIRED_OR_UNKNOWN") Thread.sleep(50)
            } while (code != "EXPIRED_OR_UNKNOWN" && android.os.SystemClock.elapsedRealtime() < until)
            assertEquals("EXPIRED_OR_UNKNOWN", code)
        } finally {
            context.unbindService(fixture.second)
            context.unbindService(broker.second)
        }
    }

    private fun binding(context: Context, component: ComponentName): Pair<IBinder, ServiceConnection> {
        val connected = CountDownLatch(1)
        val result = AtomicReference<IBinder>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                result.set(service); connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        assertTrue(context.bindService(Intent().setComponent(component), connection, Context.BIND_AUTO_CREATE))
        assertTrue("Timed out binding fixture", connected.await(8, TimeUnit.SECONDS))
        return result.get() to connection
    }

    private fun exchange(target: Messenger, code: Int): Bundle {
        val response = AtomicReference<Bundle>()
        val latch = CountDownLatch(1)
        val reply = Messenger(Handler(Looper.getMainLooper()) { response.set(it.data); latch.countDown(); true })
        target.send(Message.obtain(null, code).apply { replyTo = reply })
        assertTrue("Fixture did not reply", latch.await(8, TimeUnit.SECONDS))
        return response.get()
    }
}
