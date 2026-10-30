package dev.boundarylab.broker

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.boundarylab.sdk.BoundaryClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class SdkOwnershipTest {
    @Test fun closedClientRejectsAndClosesTransferredDescriptorExactlyOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("sdk-close", ".bin", context.cacheDir)
        val client = BoundaryClient(context)
        try {
            client.close()
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val latch = CountDownLatch(1)
            val calls = AtomicInteger()
            client.submitFile(descriptor, "empty.bin") {
                assertTrue(it.isFailure)
                calls.incrementAndGet()
                latch.countDown()
            }
            assertTrue(latch.await(5, TimeUnit.SECONDS))
            assertFalse(descriptor.fileDescriptor.valid())
            assertEquals(1, calls.get())
        } finally { client.close(); file.delete() }
    }

    @Test fun rejectedExecutorClosesTransferredDescriptorAndSettlesCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("sdk-rejected", ".bin", context.cacheDir)
        val client = BoundaryClient(context)
        try {
            // Deliberately force the otherwise timing-dependent close/enqueue rejection branch.
            val worker = BoundaryClient::class.java.getDeclaredField("worker").apply { isAccessible = true }
                .get(client) as java.util.concurrent.ScheduledThreadPoolExecutor
            worker.shutdown()
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val latch = CountDownLatch(1)
            val calls = AtomicInteger()
            client.submitFile(descriptor, "empty.bin") {
                assertTrue(it.isFailure)
                calls.incrementAndGet(); latch.countDown()
            }
            assertTrue(latch.await(5, TimeUnit.SECONDS))
            assertFalse(descriptor.fileDescriptor.valid())
            assertEquals(1, calls.get())
            val pending = BoundaryClient::class.java.getDeclaredField("pending").apply { isAccessible = true }
                .get(client) as AtomicInteger
            assertEquals(0, pending.get())
        } finally { client.close(); file.delete() }
    }

    @Test fun concurrentCloseAndSubmissionNeverLeaksOwnedDescriptorsOrCallbacks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("sdk-race", ".bin", context.cacheDir)
        try {
            repeat(30) {
                val client = BoundaryClient(context)
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val barrier = CountDownLatch(1)
                val callback = CountDownLatch(1)
                val calls = AtomicInteger()
                val submitted = Thread {
                    barrier.await()
                    client.submitFile(descriptor, "empty.bin") {
                        calls.incrementAndGet()
                        callback.countDown()
                    }
                }
                val closing = Thread { barrier.await(); client.close() }
                submitted.start(); closing.start(); barrier.countDown()
                submitted.join(5_000); closing.join(5_000)
                assertFalse(submitted.isAlive)
                assertFalse(closing.isAlive)
                assertTrue("Submission callback missing", callback.await(6, TimeUnit.SECONDS))
                assertFalse("Transferred descriptor leaked", descriptor.fileDescriptor.valid())
                assertEquals(1, calls.get())
            }
        } finally { file.delete() }
    }
}
