import dev.boundarylab.core.CoreEngine
import dev.boundarylab.core.FieldPackCodec
import dev.boundarylab.core.FieldPackEntry
import dev.boundarylab.core.FieldPackManifest
import dev.boundarylab.core.GENERATED_OPERATION
import dev.boundarylab.core.JobRequest
import dev.boundarylab.core.JobState
import java.security.MessageDigest
import java.util.UUID

fun main() {
    val owner = "standalone-jvm"
    val byteCount = 65_536
    val seed = 17L
    val expected = MessageDigest.getInstance("SHA-256")
        .digest(ByteArray(byteCount) { ((seed + it) and 255).toByte() })
        .joinToString("") { "%02x".format(it) }
    CoreEngine(autoStart = false).use { engine ->
        val session = engine.openSession(owner)
        val admitted = engine.submit(owner, session.sessionId, session.epoch,
            JobRequest(UUID.randomUUID().toString(), GENERATED_OPERATION, byteCount.toLong(), seed))
        check(engine.runNext()) { "No generated workload admitted" }
        val completed = engine.status(owner, session.sessionId, session.epoch, admitted.jobId)
        check(completed.state == JobState.SUCCEEDED && completed.bytesProcessed == byteCount.toLong())
        check(completed.digest == expected) { "Digest disagrees with independent SHA-256 calculation" }
        val manifest = FieldPackManifest(listOf(FieldPackEntry("generated.bin", byteCount.toLong(), expected)))
        val encoded = FieldPackCodec.encode(manifest)
        check(FieldPackCodec.decode(encoded) == manifest)
        println("PUBLISHED_CORE_OK bytes=${completed.bytesProcessed} sha256=$expected manifestBytes=${encoded.size}")
    }
}
