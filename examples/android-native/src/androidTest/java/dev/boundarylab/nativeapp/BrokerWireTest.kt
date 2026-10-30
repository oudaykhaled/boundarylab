package dev.boundarylab.nativeapp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.boundarylab.ipc.IBoundaryBroker
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Raw cross-package wire checks, deliberately bypassing SDK normalization. */
@RunWith(AndroidJUnit4::class)
class BrokerWireTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun declaredLengthMismatchIsRejectedBeforeAdmission() = connection { broker, epoch, session ->
        val file = File(context.cacheDir, "wire-mismatch.txt").apply { writeText("abc") }
        try {
            val request = UUID.randomUUID().toString()
            val result = submit(broker, epoch, session, request, file, 2)
            assertFalse(result.getBoolean("ok"))
            assertEquals("INVALID", result.getString("error"))
            val lookup = JSONObject(broker.statusByRequest(epoch, session, request))
            assertFalse(lookup.getBoolean("ok"))
            assertEquals("EXPIRED_OR_UNKNOWN", lookup.getString("error"))
        } finally { file.delete() }
    }

    @Test fun duplicateMetadataReturnsRetainedJobAndChangedMetadataConflicts() = connection { broker, epoch, session ->
        val original = File(context.cacheDir, "wire-original.txt").apply { writeText("abc") }
        val different = File(context.cacheDir, "wire-different.txt").apply { writeText("longer") }
        try {
            val request = UUID.randomUUID().toString()
            val first = submit(broker, epoch, session, request, original, 3)
            assertTrue(first.toString(), first.getBoolean("ok"))
            val duplicate = submit(broker, epoch, session, request, different, 3)
            assertTrue(duplicate.toString(), duplicate.getBoolean("ok"))
            assertEquals(first.getString("jobId"), duplicate.getString("jobId"))
            val conflicting = submit(broker, epoch, session, request, different, 6)
            assertFalse(conflicting.getBoolean("ok"))
            assertEquals("CONFLICT", conflicting.getString("error"))
        } finally { original.delete(); different.delete() }
    }

    private fun submit(broker: IBoundaryBroker, epoch: String, session: String, request: String, file: File, declared: Long): JSONObject =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { input ->
            JSONObject(broker.submit(epoch, session, request, "regular-file-sha256-v1", declared, 0, 30_000, "wire.txt", input))
        }

    private fun connection(action: (IBoundaryBroker, String, String) -> Unit) {
        val bound = CountDownLatch(1)
        lateinit var broker: IBoundaryBroker
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                broker = IBoundaryBroker.Stub.asInterface(binder)
                bound.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        assertTrue(context.bindService(Intent().setComponent(ComponentName("dev.boundarylab.broker",
            "dev.boundarylab.broker.BoundaryBrokerService")), connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue("Separate broker did not bind", bound.await(10, TimeUnit.SECONDS))
            val session = JSONObject(broker.openSession(Binder(), 1))
            assertTrue(session.toString(), session.getBoolean("ok"))
            val epoch = session.getString("epoch")
            val sessionId = session.getString("sessionId")
            try { action(broker, epoch, sessionId) }
            finally { broker.closeSession(epoch, sessionId) }
        } finally { context.unbindService(connection) }
    }
}
