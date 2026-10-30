package dev.boundarylab.core

import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class CoreEngineTest {
    private class Clock(var time: Long = 0) : MonotonicClock {
        override fun nowMillis() = time
    }

    private open class Source(override val size: Long = 3) : PositionalSource {
        val closes = AtomicInteger()
        val reads = mutableListOf<Pair<Long, Int>>()
        var unchanged = true
        override fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int {
            reads.add(offset to length)
            if (offset >= size) return -1
            val count = minOf(length.toLong(), size - offset).toInt()
            for (i in 0 until count) buffer[bufferOffset + i] = ('a'.code + ((offset + i) % 26)).toByte()
            return count
        }
        override fun verifyUnchanged() = unchanged
        override fun close() { closes.incrementAndGet() }
    }

    private fun request(size: Long = 0, seed: Long = 0, deadline: Long = 30_000) =
        JobRequest(UUID.randomUUID().toString(), GENERATED_OPERATION, size, seed, deadline)

    private fun fileRequest(size: Long = 3) = request(size).copy(operation = FILE_OPERATION, alias = "a.bin")
    private fun CoreEngine.submit(session: SessionHandle, req: JobRequest = request(), source: PositionalSource? = null) =
        submit("owner", session.sessionId, session.epoch, req, source)
    private fun CoreEngine.status(session: SessionHandle, job: JobSnapshot) =
        status("owner", session.sessionId, session.epoch, job.jobId)
    private fun CoreEngine.cancel(session: SessionHandle, job: JobSnapshot) =
        cancel("owner", session.sessionId, session.epoch, job.jobId)
    private fun expect(code: ErrorCode, block: () -> Unit) = assertEquals(code, assertFailsWith<BoundaryException>(block = block).code)

    @Test fun `all independent Python digest vectors agree`() {
        val corpus = File("../conformance/shared/digest-v1.json").readText()
        val pattern = Regex(""""id"\s*:\s*"([^"]+)"\s*,\s*"size"\s*:\s*(\d+)\s*,\s*"generator"\s*:\s*\{(.*?)\}\s*,\s*"sha256"\s*:\s*"([a-f0-9]{64})"""", RegexOption.DOT_MATCHES_ALL)
        val cases = pattern.findAll(corpus).toList()
        assertEquals(25, cases.size)
        CoreEngine(enableFileOperation = true, autoStart = false).use { engine ->
            val session = engine.openSession("owner")
            for (case in cases) {
                val size = case.groupValues[2].toLong()
                val generator = case.groupValues[3]
                val seed = Regex(""""seed"\s*:\s*(-?\d+)""").find(generator)?.groupValues?.get(1)?.toLong()
                val job = if (seed != null) engine.submit(session, request(size, seed)) else {
                    val encoded = Regex(""""data"\s*:\s*"([^"]*)"""").find(generator)!!.groupValues[1]
                    val bytes = Base64.getDecoder().decode(encoded)
                    engine.submit(session, fileRequest(size), object : Source(size) {
                        override fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int {
                            if (offset >= size) return -1
                            bytes.copyInto(buffer, bufferOffset, offset.toInt(), offset.toInt() + length)
                            return length
                        }
                    })
                }
                assertTrue(engine.runNext())
                val result = engine.status(session, job)
                assertEquals(JobState.SUCCEEDED, result.state, case.groupValues[1])
                assertEquals(case.groupValues[4], result.digest, case.groupValues[1])
                assertEquals(size, result.bytesProcessed)
            }
        }
    }

    @Test fun `source reads are positional bounded and closed once`() {
        CoreEngine(true, false).use { engine ->
            val session = engine.openSession("owner")
            val source = Source(65_537)
            val job = engine.submit(session, fileRequest(source.size), source)
            engine.runNext()
            assertEquals(JobState.SUCCEEDED, engine.status(session, job).state)
            assertEquals(listOf(0L to 65_536, 65_536L to 1, 65_537L to 1), source.reads)
            assertEquals(1, source.closes.get())
            assertEquals(0, engine.metrics().ownedSources)
        }
    }

    @Test fun `invalid lengths and overflowing input reject before reads`() {
        CoreEngine(true, false).use { engine ->
            val session = engine.openSession("owner")
            for (size in listOf(-1L, 134_217_729L, Long.MAX_VALUE, Long.MIN_VALUE)) {
                val source = Source(size)
                expect(ErrorCode.INVALID) { engine.submit(session, fileRequest(size), source) }
                assertTrue(source.reads.isEmpty())
                assertEquals(1, source.closes.get())
            }
            assertEquals(0, engine.metrics().queuedJobs)
        }
    }

    @Test fun `session cap accepts sixteen and rejects seventeenth`() {
        CoreEngine(autoStart = false).use { engine ->
            repeat(15) { engine.openSession("owner") }
            assertEquals(15, engine.metrics().sessions)
            val last = engine.openSession("owner")
            assertEquals(16, engine.metrics().sessions)
            expect(ErrorCode.BUSY) { engine.openSession("owner") }
            engine.closeSession("owner", last.sessionId, last.epoch)
            engine.openSession("owner")
        }
    }

    @Test fun `outstanding and global queue caps include exact boundaries`() {
        CoreEngine(autoStart = false).use { engine ->
            val sessions = List(5) { engine.openSession("owner") }
            sessions.take(4).forEach { s ->
                repeat(3) { engine.submit(s) }
                engine.submit(s)
                expect(ErrorCode.BUSY) { engine.submit(s) }
            }
            assertEquals(16, engine.metrics().queuedJobs)
            expect(ErrorCode.BUSY) { engine.submit(sessions.last()) }
            engine.runNext()
            engine.submit(sessions.last())
            assertEquals(16, engine.metrics().queuedJobs)
        }
    }

    @Test fun `round robin selects one queued job per active session turn`() {
        CoreEngine(autoStart = false).use { engine ->
            val a = engine.openSession("owner")
            val b = engine.openSession("owner")
            val a1 = engine.submit(a)
            val a2 = engine.submit(a)
            val b1 = engine.submit(b)
            val b2 = engine.submit(b)
            for ((session, job) in listOf(a to a1, b to b1, a to a2, b to b2)) {
                assertEquals(JobState.ACCEPTED, engine.status(session, job).state)
                assertTrue(engine.runNext())
                assertEquals(JobState.SUCCEEDED, engine.status(session, job).state)
            }
            assertFalse(engine.runNext())
        }
    }

    @Test fun `owner session and epoch bind every handle`() {
        CoreEngine(autoStart = false).use { engine ->
            val a = engine.openSession("owner")
            val b = engine.openSession("other")
            val job = engine.submit(a)
            expect(ErrorCode.DENIED) { engine.status("other", a.sessionId, a.epoch, job.jobId) }
            expect(ErrorCode.DENIED) { engine.cancel("other", a.sessionId, a.epoch, job.jobId) }
            expect(ErrorCode.DENIED) { engine.status("other", b.sessionId, b.epoch, job.jobId) }
            expect(ErrorCode.DENIED) { engine.closeSession("other", a.sessionId, a.epoch) }
            expect(ErrorCode.STALE_EPOCH) { engine.status("owner", a.sessionId, UUID.randomUUID().toString(), job.jobId) }
            expect(ErrorCode.DENIED) { engine.statusByRequest("other", a.sessionId, a.epoch, job.requestId) }
            assertEquals(JobState.ACCEPTED, engine.status(a, job).state)
        }
    }

    @Test fun `dedup closes incoming descriptor without reading and conflict preserves original`() {
        CoreEngine(true, false).use { engine ->
            val session = engine.openSession("owner")
            val req = fileRequest()
            val first = Source()
            val job = engine.submit(session, req, first)
            val duplicate = Source()
            assertEquals(job.jobId, engine.submit(session, req, duplicate).jobId)
            assertTrue(duplicate.reads.isEmpty())
            assertEquals(1, duplicate.closes.get())
            val conflict = Source()
            expect(ErrorCode.CONFLICT) { engine.submit(session, req.copy(alias = "b.bin"), conflict) }
            assertEquals(1, conflict.closes.get())
            engine.runNext()
            val terminalDuplicate = Source()
            assertEquals(job.jobId, engine.submit(session, req, terminalDuplicate).jobId)
            assertTrue(terminalDuplicate.reads.isEmpty())
            assertEquals(1, terminalDuplicate.closes.get())
            assertEquals(1, first.closes.get())
        }
    }

    @Test fun `queued cancellation performs zero reads and remains terminal`() {
        CoreEngine(true, false).use { engine ->
            val session = engine.openSession("owner")
            val source = Source()
            val job = engine.submit(session, fileRequest(), source)
            assertEquals(JobState.CANCELLED, engine.cancel(session, job).state)
            assertEquals(JobState.CANCELLED, engine.cancel(session, job).state)
            assertFalse(engine.runNext())
            assertTrue(source.reads.isEmpty())
            assertEquals(1, source.closes.get())
            assertEquals(0, engine.metrics().ownedSources)
        }
    }

    @Test fun `completion committed first wins against later cancellation`() {
        CoreEngine(autoStart = false).use { engine ->
            val session = engine.openSession("owner")
            val job = engine.submit(session)
            engine.runNext()
            assertEquals(JobState.SUCCEEDED, engine.cancel(session, job).state)
            assertFalse(engine.status(session, job).cancelRequested)
        }
    }

    @Test fun `running cancellation retains physical worker and source until blocked read exits`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        CoreEngine(true, false).use { engine ->
            val session = engine.openSession("owner")
            val source = object : Source() {
                override fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int {
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    return super.readAt(offset, buffer, bufferOffset, length)
                }
            }
            val job = engine.submit(session, fileRequest(), source)
            val worker = thread { engine.runNext() }
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                val cancel = engine.cancel(session, job)
                assertEquals(JobState.RUNNING, cancel.state)
                assertTrue(cancel.cancelRequested)
                assertEquals(1, engine.metrics().runningJobs)
                assertEquals(1, engine.metrics().ownedSources)
                assertFalse(engine.runNext())
                assertEquals(0, source.closes.get())
            } finally { release.countDown(); worker.join(10_000) }
            assertFalse(worker.isAlive)
            assertEquals(JobState.CANCELLED, engine.status(session, job).state)
            assertEquals(1, source.closes.get())
            assertEquals(0, engine.metrics().runningJobs)
            assertEquals(0, engine.metrics().ownedSources)
        }
    }

    @Test fun `queued deadline does no reads and running deadline waits for cooperative cleanup`() {
        val clock = Clock()
        CoreEngine(true, false, clock).use { engine ->
            val session = engine.openSession("owner")
            val source = Source()
            val job = engine.submit(session, fileRequest().copy(deadlineMillis = 1), source)
            clock.time = 1
            engine.runNext()
            assertEquals("DEADLINE_EXCEEDED", engine.status(session, job).errorCode)
            assertTrue(source.reads.isEmpty())
            assertEquals(1, source.closes.get())
            val runningSource = object : Source() {
                override fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int {
                    clock.time += 10
                    return super.readAt(offset, buffer, bufferOffset, length)
                }
            }
            val runningJob = engine.submit(session, fileRequest().copy(deadlineMillis = 10), runningSource)
            engine.runNext()
            assertEquals("DEADLINE_EXCEEDED", engine.status(session, runningJob).errorCode)
            assertEquals(1, runningSource.closes.get())
        }
    }

    @Test fun `TTL expiration removes request dedup and never restarts work on query`() {
        val clock = Clock()
        CoreEngine(autoStart = false, clock = clock).use { engine ->
            val session = engine.openSession("owner")
            val req = request()
            val job = engine.submit(session, req)
            engine.runNext()
            clock.time = 59_999
            assertEquals(JobState.SUCCEEDED, engine.status(session, job).state)
            clock.time = 60_000
            expect(ErrorCode.EXPIRED_OR_UNKNOWN) { engine.status(session, job) }
            expect(ErrorCode.EXPIRED_OR_UNKNOWN) { engine.statusByRequest("owner", session.sessionId, session.epoch, req.requestId) }
            assertFalse(engine.runNext())
            assertNotEquals(job.jobId, engine.submit(session, req).jobId)
        }
    }

    @Test fun `retention evicts oldest at entry cap`() {
        CoreEngine(autoStart = false).use { engine ->
            val session = engine.openSession("owner")
            val jobs = (1..65).map {
                val job = engine.submit(session)
                engine.runNext()
                if (it == 63 || it == 64) assertEquals(it, engine.metrics().retainedEntries)
                job
            }
            assertEquals(64, engine.metrics().retainedEntries)
            expect(ErrorCode.EXPIRED_OR_UNKNOWN) { engine.status(session, jobs.first()) }
            assertEquals(JobState.SUCCEEDED, engine.status(session, jobs[1]).state)
            assertTrue(engine.metrics().retainedBytes <= 1_048_576)
        }
    }

    @Test fun `retention bytes evict independently of count and reject indivisible record`() {
        var recordBytes = 0L
        CoreEngine(autoStart = false).use { engine ->
            val session = engine.openSession("owner")
            engine.submit(session)
            engine.runNext()
            recordBytes = engine.metrics().retainedBytes
        }
        CoreEngine(autoStart = false, limits = EngineLimits(maxRetainedBytes = recordBytes * 2)).use { engine ->
            val session = engine.openSession("owner")
            val jobs = List(3) { val job = engine.submit(session); engine.runNext(); job }
            assertEquals(2, engine.metrics().retainedEntries)
            assertEquals(recordBytes * 2, engine.metrics().retainedBytes)
            expect(ErrorCode.EXPIRED_OR_UNKNOWN) { engine.status(session, jobs.first()) }
        }
        CoreEngine(autoStart = false, limits = EngineLimits(maxRetainedBytes = recordBytes - 1)).use { engine ->
            expect(ErrorCode.INVALID) { engine.submit(engine.openSession("owner")) }
        }
    }

    @Test fun `file failures never yield a digest and always close`() {
        val faults = listOf<(Long, ByteArray, Int, Int) -> Int>(
            { _, _, _, _ -> -1 }, { _, _, _, _ -> 0 }, { _, _, _, count -> count + 1 },
            { _, _, _, _ -> throw IOException("private path must not escape") },
        )
        faults.forEach { fault ->
            CoreEngine(true, false).use { engine ->
                val session = engine.openSession("owner")
                val source = object : Source() {
                    override fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int) =
                        fault(offset, buffer, bufferOffset, length)
                }
                val job = engine.submit(session, fileRequest(), source)
                engine.runNext()
                val result = engine.status(session, job)
                assertEquals(JobState.FAILED, result.state)
                assertEquals("IO_ERROR", result.errorCode)
                assertEquals(null, result.digest)
                assertEquals(1, source.closes.get())
            }
        }
    }

    @Test fun `positive partial reads hash complete source and premature EOF still fails`() {
        CoreEngine(true, false).use { engine ->
            val session = engine.openSession("owner")
            val source = object : Source() {
                override fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int =
                    super.readAt(offset, buffer, bufferOffset, minOf(length, 1))
            }
            val job = engine.submit(session, fileRequest(), source)
            engine.runNext()
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", engine.status(session, job).digest)
            assertEquals(listOf(0L to 1, 1L to 1, 2L to 1, 3L to 1), source.reads)
            val incomplete = object : Source() {
                override fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int =
                    if (offset > 0) -1 else super.readAt(offset, buffer, bufferOffset, 1)
            }
            val failed = engine.submit(session, fileRequest(), incomplete)
            engine.runNext()
            assertEquals(JobState.FAILED, engine.status(session, failed).state)
            assertEquals("IO_ERROR", engine.status(session, failed).errorCode)
            assertEquals(1, incomplete.closes.get())
        }
    }

    @Test fun `growth metadata change and close errors reject success`() {
        val sources = listOf(
            object : Source() {
                override fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int =
                    if (offset == size) 1 else super.readAt(offset, buffer, bufferOffset, length)
            } to "SOURCE_CHANGED",
            Source().apply { unchanged = false } to "SOURCE_CHANGED",
            object : Source() { override fun close() { super.close(); throw IOException() } } to "IO_ERROR",
        )
        sources.forEach { (source, error) ->
            CoreEngine(true, false).use { engine ->
                val session = engine.openSession("owner")
                val job = engine.submit(session, fileRequest(), source)
                engine.runNext()
                assertEquals(error, engine.status(session, job).errorCode)
                assertEquals(null, engine.status(session, job).digest)
                assertEquals(1, source.closes.get())
            }
        }
    }

    @Test fun `session close revokes results and closes queued resources`() {
        CoreEngine(true, false).use { engine ->
            val session = engine.openSession("owner")
            val source = Source()
            val job = engine.submit(session, fileRequest(), source)
            engine.closeSession("owner", session.sessionId, session.epoch)
            expect(ErrorCode.EXPIRED_OR_UNKNOWN) { engine.status(session, job) }
            assertEquals(EngineMetrics(0, 0, 0, 0, 0, 0), engine.metrics())
            assertEquals(1, source.closes.get())
            assertTrue(source.reads.isEmpty())
        }
    }

    @Test fun `session loss revokes running handle without closing blocked source early`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        CoreEngine(true, false).use { engine ->
            val session = engine.openSession("owner")
            val source = object : Source() {
                override fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int {
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    return super.readAt(offset, buffer, bufferOffset, length)
                }
            }
            val job = engine.submit(session, fileRequest(), source)
            val worker = thread { engine.runNext() }
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                engine.closeSession("owner", session.sessionId, session.epoch)
                expect(ErrorCode.EXPIRED_OR_UNKNOWN) { engine.status(session, job) }
                assertEquals(1, engine.metrics().runningJobs)
                assertEquals(0, source.closes.get())
            } finally { release.countDown(); worker.join(10_000) }
            assertFalse(worker.isAlive)
            assertEquals(EngineMetrics(0, 0, 0, 0, 0, 0), engine.metrics())
            assertEquals(1, source.closes.get())
        }
    }

    @Test fun `capabilities explicit opt in and protocol rejects before admission`() {
        CoreEngine(autoStart = false).use { engine ->
            val session = engine.openSession("owner")
            assertEquals(setOf(GENERATED_OPERATION), engine.capabilities().operations)
            val source = Source()
            expect(ErrorCode.UNSUPPORTED) { engine.submit(session, fileRequest(), source) }
            assertEquals(1, source.closes.get())
            expect(ErrorCode.INCOMPATIBLE) { engine.submit(session, request().copy(protocolMajor = 2)) }
            expect(ErrorCode.INVALID) { engine.submit(session, request().copy(requestId = "NONCANONICAL")) }
            expect(ErrorCode.INVALID) { engine.submit(session, request(deadline = 0)) }
            expect(ErrorCode.INVALID) { engine.submit(session, request(deadline = 120_001)) }
            engine.submit(session, request(deadline = 120_000))
            assertEquals(1, engine.metrics().queuedJobs)
        }
    }

    @Test fun `new process incarnation gets fresh epoch`() {
        CoreEngine(autoStart = false).use { first ->
            CoreEngine(autoStart = false).use { second ->
                val session = first.openSession("owner")
                assertNotEquals(first.epoch, second.epoch)
                expect(ErrorCode.STALE_EPOCH) { second.submit(session) }
            }
        }
    }

    @Test fun `automatic worker completes normal job`() {
        CoreEngine().use { engine ->
            val session = engine.openSession("owner")
            val job = engine.submit(session, request(65_537))
            val limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            var result = engine.status(session, job)
            while (result.state == JobState.ACCEPTED || result.state == JobState.RUNNING) {
                check(System.nanoTime() < limit)
                Thread.yield()
                result = engine.status(session, job)
            }
            assertEquals(JobState.SUCCEEDED, result.state)
            assertNotNull(result.digest)
        }
    }

    @Test fun `one running and sixteen queued inputs preserve physical and per-session caps`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        CoreEngine(true, false).use { engine ->
            val sessions = List(5) { engine.openSession("owner") }
            val runningSource = object : Source() {
                override fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int {
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    return super.readAt(offset, buffer, bufferOffset, length)
                }
            }
            engine.submit(sessions.first(), fileRequest(), runningSource)
            val worker = thread { engine.runNext() }
            val queuedSources = mutableListOf<Source>()
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                repeat(3) {
                    val source = Source().also(queuedSources::add)
                    engine.submit(sessions.first(), fileRequest(), source)
                }
                val rejectedPerSession = Source()
                expect(ErrorCode.BUSY) { engine.submit(sessions.first(), fileRequest(), rejectedPerSession) }
                assertEquals(1, rejectedPerSession.closes.get())
                sessions.drop(1).take(3).forEach { session ->
                    repeat(4) { engine.submit(session, fileRequest(), Source().also(queuedSources::add)) }
                }
                assertEquals(15, engine.metrics().queuedJobs)
                engine.submit(sessions.last(), fileRequest(), Source().also(queuedSources::add))
                assertEquals(EngineMetrics(5, 16, 1, 0, 0, 17), engine.metrics())
                val rejectedGlobal = Source()
                expect(ErrorCode.BUSY) { engine.submit(sessions.last(), fileRequest(), rejectedGlobal) }
                assertEquals(1, rejectedGlobal.closes.get())
                assertTrue(rejectedGlobal.reads.isEmpty())
            } finally { release.countDown(); worker.join(10_000) }
            assertFalse(worker.isAlive)
            assertEquals(16, engine.metrics().ownedSources)
            engine.close()
            assertEquals(0, engine.metrics().ownedSources)
            assertTrue(queuedSources.all { it.closes.get() == 1 && it.reads.isEmpty() })
        }
    }

    @Test fun `seeded event sequences retain immutable terminal results and bounded resources`() {
        val clock = Clock()
        val random = Random(1742)
        CoreEngine(autoStart = false, clock = clock).use { engine ->
            val sessions = List(4) { engine.openSession("owner") }
            val admitted = mutableListOf<Pair<SessionHandle, JobSnapshot>>()
            val terminals = mutableMapOf<String, JobSnapshot>()
            repeat(500) {
                when (random.nextInt(4)) {
                    0 -> {
                        val session = sessions[random.nextInt(sessions.size)]
                        try { admitted += session to engine.submit(session, request(random.nextLong(128), deadline = 10)) }
                        catch (error: BoundaryException) { assertEquals(ErrorCode.BUSY, error.code) }
                    }
                    1 -> if (admitted.isNotEmpty()) {
                        val (session, job) = admitted[random.nextInt(admitted.size)]
                        try { engine.cancel(session, job) }
                        catch (error: BoundaryException) { assertEquals(ErrorCode.EXPIRED_OR_UNKNOWN, error.code) }
                    }
                    2 -> engine.runNext()
                    3 -> clock.time += random.nextLong(20)
                }
                val metrics = engine.metrics()
                assertTrue(metrics.queuedJobs in 0..16)
                assertEquals(0, metrics.runningJobs)
                assertTrue(metrics.retainedEntries in 0..64)
                assertTrue(metrics.retainedBytes in 0..1_048_576)
                for ((session, job) in admitted) {
                    val current = try { engine.status(session, job) } catch (error: BoundaryException) {
                        assertEquals(ErrorCode.EXPIRED_OR_UNKNOWN, error.code)
                        continue
                    }
                    terminals[current.jobId]?.let { assertEquals(it, current) }
                    if (current.state !in setOf(JobState.ACCEPTED, JobState.RUNNING)) terminals[current.jobId] = current
                }
            }
            engine.close()
            assertEquals(EngineMetrics(0, 0, 0, 0, 0, 0), engine.metrics())
        }
    }
}
