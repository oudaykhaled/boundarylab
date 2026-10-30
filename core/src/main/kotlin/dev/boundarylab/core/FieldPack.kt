package dev.boundarylab.core

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

data class FieldPackEntry(val name: String, val size: Long, val sha256: String)
data class FieldPackManifest(val files: List<FieldPackEntry>)

/** Strict, bounded FieldPack v1 codec. No attachment I/O occurs here. */
object FieldPackCodec {
    const val MAX_MANIFEST_BYTES = 16_384
    const val MAX_FILES = 32
    const val MAX_TOTAL_BYTES = 134_217_728L
    private val aliasPattern = Regex("[a-z0-9][a-z0-9._-]{0,63}")
    private val digestPattern = Regex("[0-9a-f]{64}")
    private val reserved = setOf("con", "prn", "aux", "nul") +
        (1..9).flatMap { listOf("com$it", "lpt$it") }

    fun validateAlias(alias: String): Boolean = aliasPattern.matches(alias) &&
        !alias.endsWith('.') && alias.substringBefore('.') !in reserved

    fun decode(bytes: ByteArray): FieldPackManifest {
        invalidIf(bytes.isEmpty() || bytes.size > MAX_MANIFEST_BYTES)
        val source = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) { invalid() }
        val top = Parser(source).parse() as? Map<*, *> ?: invalid()
        invalidIf(top.keys != setOf("format", "version", "algorithm", "files"))
        invalidIf(top["format"] != "fieldpack" || top["version"] != 1L || top["algorithm"] != "sha256")
        val rows = top["files"] as? List<*> ?: invalid()
        invalidIf(rows.isEmpty() || rows.size > MAX_FILES)
        val entries = rows.map { row ->
            val entry = row as? Map<*, *> ?: invalid()
            invalidIf(entry.keys != setOf("name", "size", "sha256"))
            FieldPackEntry(entry["name"] as? String ?: invalid(),
                entry["size"] as? Long ?: invalid(), entry["sha256"] as? String ?: invalid())
        }
        validateEntries(entries, requireSorted = true)
        return FieldPackManifest(entries)
    }

    fun encode(manifest: FieldPackManifest): ByteArray {
        val entries = manifest.files.sortedBy { it.name }
        validateEntries(entries, requireSorted = true)
        // Valid aliases and hexadecimal digests need no JSON escaping.
        val rows = entries.joinToString(",") {
            "{\"name\":\"${it.name}\",\"size\":${it.size},\"sha256\":\"${it.sha256}\"}"
        }
        val bytes = "{\"format\":\"fieldpack\",\"version\":1,\"algorithm\":\"sha256\",\"files\":[$rows]}\n"
            .toByteArray(Charsets.UTF_8)
        invalidIf(bytes.size > MAX_MANIFEST_BYTES)
        return bytes
    }

    private fun validateEntries(entries: List<FieldPackEntry>, requireSorted: Boolean) {
        invalidIf(entries.isEmpty() || entries.size > MAX_FILES)
        var total = 0L
        var previous: String? = null
        for (entry in entries) {
            invalidIf(!validateAlias(entry.name) || !digestPattern.matches(entry.sha256))
            invalidIf(entry.size < 0 || entry.size > MAX_TOTAL_BYTES)
            invalidIf(requireSorted && previous != null && previous >= entry.name)
            invalidIf(entry.size > MAX_TOTAL_BYTES - total)
            total += entry.size
            previous = entry.name
        }
    }

    private fun invalidIf(condition: Boolean) { if (condition) invalid() }
    private fun invalid(): Nothing = throw BoundaryException(ErrorCode.INVALID)

    /** Preserves JSON number types and rejects duplicates before map conversion. */
    private class Parser(private val source: String) {
        private var cursor = 0
        private data class NonInteger(val token: String)
        fun parse(): Any? {
            val result = value(0)
            whitespace()
            invalidIf(cursor != source.length)
            return result
        }
        private fun value(depth: Int): Any? {
            invalidIf(depth > 8)
            whitespace()
            invalidIf(cursor >= source.length)
            return when (source[cursor]) {
                '{' -> objectValue(depth + 1)
                '[' -> arrayValue(depth + 1)
                '"' -> stringValue()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                '-', in '0'..'9' -> numberValue()
                else -> invalid()
            }
        }
        private fun objectValue(depth: Int): Map<String, Any?> {
            cursor++
            val result = linkedMapOf<String, Any?>()
            whitespace()
            if (take('}')) return result
            do {
                whitespace()
                invalidIf(cursor >= source.length || source[cursor] != '"')
                val key = stringValue()
                invalidIf(result.containsKey(key))
                whitespace()
                invalidIf(!take(':'))
                result[key] = value(depth)
                whitespace()
                if (take('}')) return result
                invalidIf(!take(','))
            } while (true)
        }
        private fun arrayValue(depth: Int): List<Any?> {
            cursor++
            val result = mutableListOf<Any?>()
            whitespace()
            if (take(']')) return result
            do {
                result.add(value(depth))
                whitespace()
                if (take(']')) return result
                invalidIf(!take(','))
            } while (true)
        }
        private fun stringValue(): String {
            invalidIf(!take('"'))
            val text = StringBuilder()
            while (cursor < source.length) {
                val character = source[cursor++]
                when {
                    character == '"' -> return text.toString()
                    character < ' ' -> invalid()
                    character == '\\' -> {
                        invalidIf(cursor >= source.length)
                        when (val escape = source[cursor++]) {
                            '"', '\\', '/' -> text.append(escape)
                            'b' -> text.append('\b')
                            'f' -> text.append('\u000c')
                            'n' -> text.append('\n')
                            'r' -> text.append('\r')
                            't' -> text.append('\t')
                            'u' -> {
                                invalidIf(cursor + 4 > source.length)
                                val digits = source.substring(cursor, cursor + 4)
                                invalidIf(digits.any { it !in "0123456789abcdefABCDEF" })
                                text.append(digits.toInt(16).toChar())
                                cursor += 4
                            }
                            else -> invalid()
                        }
                    }
                    else -> text.append(character)
                }
            }
            invalid()
        }
        private fun numberValue(): Any {
            val start = cursor
            take('-')
            invalidIf(cursor >= source.length)
            if (take('0')) {
                invalidIf(cursor < source.length && source[cursor] in '0'..'9')
            } else {
                invalidIf(cursor >= source.length || source[cursor] !in '1'..'9')
                while (cursor < source.length && source[cursor] in '0'..'9') cursor++
            }
            var integer = true
            if (take('.')) {
                integer = false
                digits()
            }
            if (cursor < source.length && source[cursor] in "eE") {
                integer = false
                cursor++
                if (cursor < source.length && source[cursor] in "+-") cursor++
                digits()
            }
            val token = source.substring(start, cursor)
            return if (integer) token.toLongOrNull() ?: invalid() else NonInteger(token)
        }
        private fun digits() {
            val start = cursor
            while (cursor < source.length && source[cursor] in '0'..'9') cursor++
            invalidIf(start == cursor)
        }
        private fun literal(token: String, result: Any?): Any? {
            invalidIf(!source.startsWith(token, cursor))
            cursor += token.length
            return result
        }
        private fun take(character: Char): Boolean {
            if (cursor >= source.length || source[cursor] != character) return false
            cursor++
            return true
        }
        private fun whitespace() {
            while (cursor < source.length && source[cursor] in " \t\n\r") cursor++
        }
    }
}
