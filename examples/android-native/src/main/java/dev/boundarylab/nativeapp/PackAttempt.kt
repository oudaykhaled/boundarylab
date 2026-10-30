package dev.boundarylab.nativeapp

import dev.boundarylab.core.FieldPackCodec
import dev.boundarylab.core.FieldPackEntry
import dev.boundarylab.core.FieldPackManifest

/** Current-attempt results only. Any interruption destroys export eligibility. */
class PackAttempt(private val aliases: List<String>) {
    data class Entry(val name: String, val size: Long, val sha256: String)
    private val entries = mutableListOf<Entry>()
    private var blocked = false
    val complete: Boolean get() = !blocked && entries.size == aliases.size
    val count: Int get() = entries.size

    init {
        require(aliases.size in 1..32 && aliases.distinct().size == aliases.size)
        require(aliases.all(::safeAlias))
    }

    fun succeed(name: String, size: Long, sha256: String) {
        check(!blocked && entries.size < aliases.size)
        require(name == aliases[entries.size])
        require(size in 0..MAX_BYTES)
        require(sha256.matches(Regex("[a-f0-9]{64}")))
        require(entries.sumOf { it.size } <= MAX_BYTES - size)
        entries += Entry(name, size, sha256)
    }

    fun invalidate() {
        blocked = true
        entries.clear()
    }

    fun manifest(): ByteArray {
        check(complete) { "Every attachment must succeed in this attempt" }
        return FieldPackCodec.encode(FieldPackManifest(entries.map {
            FieldPackEntry(it.name, it.size, it.sha256)
        }))
    }

    companion object {
        const val MAX_BYTES = 134_217_728L
        fun alias(index: Int) = "attachment-${(index + 1).toString().padStart(3, '0')}.bin"
        fun safeAlias(value: String): Boolean = FieldPackCodec.validateAlias(value)
    }
}
