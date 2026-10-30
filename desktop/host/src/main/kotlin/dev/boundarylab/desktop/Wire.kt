package dev.boundarylab.desktop

import java.io.EOFException
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

class DesktopProtocolException(val code: String) : IOException(code)

/** Bounded, strict JSON and framing shared by the two separate JVM processes. */
object Wire {
    const val MAX_FRAME_BYTES = 16_384
    const val FRAME_TIMEOUT_MILLIS = 5_000L

    fun encode(value: Map<String, Any?>): ByteArray {
        val bytes = json(value).toByteArray(StandardCharsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > MAX_FRAME_BYTES) throw DesktopProtocolException("INVALID")
        return bytes
    }

    fun decode(bytes: ByteArray): Map<String, Any?> {
        if (bytes.isEmpty() || bytes.size > MAX_FRAME_BYTES) throw DesktopProtocolException("INVALID")
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val input = try { decoder.decode(ByteBuffer.wrap(bytes)).toString() }
        catch (_: java.nio.charset.CharacterCodingException) { throw DesktopProtocolException("INVALID") }
        val parsed = Parser(input).parse()
        if (parsed !is Map<*, *>) throw DesktopProtocolException("INVALID")
        @Suppress("UNCHECKED_CAST")
        return parsed as Map<String, Any?>
    }

    /** Absolute deadline covers header plus body, including a stalled partial frame. */
    fun read(channel: SocketChannel, timeoutMillis: Long = FRAME_TIMEOUT_MILLIS): Map<String, Any?> {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        val header = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        transfer(channel, header, false, deadline)
        header.flip()
        val length = header.int
        // Check before allocating an attacker-controlled body length.
        if (length !in 1..MAX_FRAME_BYTES) throw DesktopProtocolException("INVALID")
        val payload = ByteBuffer.allocate(length)
        transfer(channel, payload, false, deadline)
        return decode(payload.array())
    }

    fun write(channel: SocketChannel, value: Map<String, Any?>, timeoutMillis: Long = FRAME_TIMEOUT_MILLIS) {
        val payload = encode(value)
        val framed = ByteBuffer.allocate(4 + payload.size).order(ByteOrder.BIG_ENDIAN)
        framed.putInt(payload.size).put(payload).flip()
        transfer(channel, framed, true, System.nanoTime() + timeoutMillis * 1_000_000)
    }

    private fun transfer(channel: SocketChannel, data: ByteBuffer, write: Boolean, deadline: Long) {
        require(!channel.isBlocking) { "Framing requires a nonblocking channel" }
        Selector.open().use { selector ->
            channel.register(selector, if (write) SelectionKey.OP_WRITE else SelectionKey.OP_READ)
            while (data.hasRemaining()) {
                if (System.nanoTime() >= deadline) throw SocketTimeoutException("Frame deadline elapsed")
                val count = if (write) channel.write(data) else channel.read(data)
                if (count < 0) throw EOFException("Connection closed before a complete frame")
                if (count == 0) {
                    val remaining = deadline - System.nanoTime()
                    if (remaining <= 0) throw SocketTimeoutException("Frame deadline elapsed")
                    selector.select(((remaining + 999_999) / 1_000_000).coerceAtLeast(1))
                    selector.selectedKeys().clear()
                }
            }
        }
    }

    private fun json(value: Any?): String = when (value) {
        null -> "null"
        is Boolean, is Byte, is Short, is Int, is Long -> value.toString()
        is String -> buildString {
            append('"')
            for (c in value) when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ' || c.isSurrogate()) append("\\u%04x".format(c.code)) else append(c)
            }
            append('"')
        }
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") {
            if (it.key !is String) throw DesktopProtocolException("INVALID")
            json(it.key) + ":" + json(it.value)
        }
        is List<*> -> value.joinToString(",", "[", "]") { json(it) }
        else -> throw DesktopProtocolException("INVALID")
    }

    private class Parser(private val text: String) {
        private var offset = 0
        fun parse(): Any? {
            val result = value(0)
            space()
            if (offset != text.length) invalid()
            return result
        }
        private fun value(depth: Int): Any? {
            if (depth > 16) invalid()
            space()
            return when (peek()) {
                '{' -> obj(depth + 1)
                '[' -> array(depth + 1)
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                '-', in '0'..'9' -> number()
                else -> invalid()
            }
        }
        private fun obj(depth: Int): Map<String, Any?> {
            take('{')
            val result = linkedMapOf<String, Any?>()
            space()
            if (peek() == '}') { offset++; return result }
            while (true) {
                space()
                val key = string()
                if (result.containsKey(key)) invalid()
                space(); take(':')
                result[key] = value(depth)
                space()
                if (peek() == '}') { offset++; return result }
                take(',')
            }
        }
        private fun array(depth: Int): List<Any?> {
            take('[')
            val result = arrayListOf<Any?>()
            space()
            if (peek() == ']') { offset++; return result }
            while (true) {
                result.add(value(depth))
                space()
                if (peek() == ']') { offset++; return result }
                take(',')
            }
        }
        private fun string(): String {
            take('"')
            val result = StringBuilder()
            while (true) {
                if (offset == text.length) invalid()
                val char = text[offset++]
                if (char == '"') break
                if (char < ' ') invalid()
                if (char != '\\') { result.append(char); continue }
                if (offset == text.length) invalid()
                result.append(when (val escaped = text[offset++]) {
                    '"', '\\', '/' -> escaped
                    'b' -> '\b'; 'f' -> '\u000C'; 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'
                    'u' -> {
                        if (offset + 4 > text.length) invalid()
                        val digits = text.substring(offset, offset + 4)
                        if (!digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) invalid()
                        offset += 4
                        digits.toInt(16).toChar()
                    }
                    else -> invalid()
                })
            }
            var index = 0
            while (index < result.length) {
                val c = result[index++]
                if (c.isHighSurrogate()) {
                    if (index == result.length || !result[index++].isLowSurrogate()) invalid()
                } else if (c.isLowSurrogate()) invalid()
            }
            return result.toString()
        }
        private fun number(): Long {
            val begin = offset
            if (peek() == '-') offset++
            if (peek() == '0') offset++ else {
                if (peek() !in '1'..'9') invalid()
                while (peek() in '0'..'9') offset++
            }
            return text.substring(begin, offset).toLongOrNull() ?: invalid()
        }
        private fun literal(token: String, result: Any?): Any? {
            if (!text.startsWith(token, offset)) invalid()
            offset += token.length
            return result
        }
        private fun space() { while (peek() in listOf(' ', '\n', '\r', '\t')) offset++ }
        private fun peek(): Char = text.getOrNull(offset) ?: '\u0000'
        private fun take(char: Char) { if (peek() != char) invalid(); offset++ }
        private fun invalid(): Nothing = throw DesktopProtocolException("INVALID")
    }
}
