package dev.boundarylab.fieldpack_flutter

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.MethodChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Requires separately installed matching-signed BoundaryLab broker APK. */
@RunWith(AndroidJUnit4::class)
class EngineLifecycleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun disposingOneEngineDoesNotCloseAnotherEngineSession() {
        val first = createEngine()
        val second = createEngine()
        var firstDestroyed = false
        try {
            assertEquals(EMPTY_SHA, generated(first, 0))
            assertEquals(EMPTY_SHA, generated(second, 0))
            instrumentation.runOnMainSync { first.engine.destroy() }
            firstDestroyed = true
            assertEquals(THREE_SHA, generated(second, 3))
        } finally {
            instrumentation.runOnMainSync {
                if (!firstDestroyed) first.engine.destroy()
                second.engine.destroy()
            }
        }
    }

    @Test fun destroyingEngineWithOutstandingWorkLeavesFreshEngineUsable() {
        val first = createEngine()
        instrumentation.runOnMainSync {
            first.channel.invokeMethod("run", mapOf("bytes" to 134217728))
            first.engine.destroy()
        }
        val next = createEngine()
        try { assertEquals(THREE_SHA, generated(next, 3)) }
        finally { instrumentation.runOnMainSync { next.engine.destroy() } }
    }

    @Test fun rawChannelRejectsDoubleAndNaNWithoutAdmission() {
        val probe = createEngine()
        try {
            listOf(1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { value ->
                val done = CountDownLatch(1)
                var failure: String? = null
                instrumentation.runOnMainSync {
                    probe.channel.invokeMethod("rawGenerated", mapOf("seed" to value, "bytes" to 0), object : MethodChannel.Result {
                        override fun success(result: Any?) { done.countDown() }
                        override fun error(code: String, message: String?, details: Any?) { failure = code; done.countDown() }
                        override fun notImplemented() { failure = "NOT_IMPLEMENTED"; done.countDown() }
                    })
                }
                check(done.await(30, TimeUnit.SECONDS)) { "Raw channel did not respond" }
                assertEquals("INVALID", failure)
            }
            assertEquals(EMPTY_SHA, generated(probe, 0))
        } finally { instrumentation.runOnMainSync { probe.engine.destroy() } }
    }

    private fun createEngine(): Probe {
        val ready = CountDownLatch(1)
        lateinit var probe: Probe
        instrumentation.runOnMainSync {
            val engine = FlutterEngine(instrumentation.targetContext)
            val channel = MethodChannel(engine.dartExecutor.binaryMessenger, "dev.boundarylab/engine_probe")
            channel.setMethodCallHandler { call, result ->
                if (call.method == "ready") { ready.countDown(); result.success(null) }
                else result.notImplemented()
            }
            val path = FlutterInjector.instance().flutterLoader().findAppBundlePath()
            engine.dartExecutor.executeDartEntrypoint(DartExecutor.DartEntrypoint(path, "engineProbe"))
            probe = Probe(engine, channel)
        }
        check(ready.await(30, TimeUnit.SECONDS)) { "Flutter engine did not initialize" }
        return probe
    }

    private fun generated(probe: Probe, bytes: Int): String {
        val done = CountDownLatch(1)
        var digest: String? = null
        var failure: String? = null
        instrumentation.runOnMainSync {
            probe.channel.invokeMethod("run", mapOf("bytes" to bytes), object : MethodChannel.Result {
                override fun success(result: Any?) { digest = result as? String; done.countDown() }
                override fun error(code: String, message: String?, details: Any?) { failure = code; done.countDown() }
                override fun notImplemented() { failure = "NOT_IMPLEMENTED"; done.countDown() }
            })
        }
        check(done.await(40, TimeUnit.SECONDS)) { "Real broker job did not finish" }
        assertEquals("Broker failure", null, failure)
        assertNotNull(digest)
        return digest!!
    }

    private data class Probe(val engine: FlutterEngine, val channel: MethodChannel)
    companion object {
        private const val EMPTY_SHA = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        private const val THREE_SHA = "ae4b3280e56e2faf83f414a6e3dabe9d5fbe18976544c05fed121accb85b53fc"
    }
}
