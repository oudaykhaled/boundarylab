package dev.boundarylab.desktop

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets.UTF_8

class WireTest {
    @Test fun `strict JSON round trip preserves long integers and Unicode`() {
        val input = linkedMapOf<String, Any?>("n" to Long.MIN_VALUE, "m" to Long.MAX_VALUE,
            "s" to "a\n\"😀", "b" to true, "nil" to null, "list" to listOf(1L, "a"))
        assertEquals(input, Wire.decode(Wire.encode(input)))
    }

    @Test fun `duplicate keys invalid UTF8 BOM nesting and noninteger JSON are rejected`() {
        val invalid = listOf("{\"op\":null,\"op\":1}", "{\"op\":1,\"\\u006fp\":2}",
            "{\"n\":1.0}", "{\"n\":1e2}", "{\"n\":01}", "{\"n\":9223372036854775808}",
            "{\"s\":\"\\ud800\"}", "{\"s\":\"\\udc00\"}", "\ufeff{}", "{}false", "[]", "",
            "{\"a\":" + "[".repeat(20) + "0" + "]".repeat(20) + "}")
        invalid.forEach { value -> assertThrows(DesktopProtocolException::class.java, { Wire.decode(value.toByteArray(UTF_8)) }, value) }
        assertThrows(DesktopProtocolException::class.java) { Wire.decode(byteArrayOf(123, 34, -64, -81, 34, 58, 49, 125)) }
        assertThrows(DesktopProtocolException::class.java) { Wire.decode(ByteArray(Wire.MAX_FRAME_BYTES + 1)) }
    }

    @Test fun `exact frame byte cap is accepted and cap plus one rejected`() {
        val exact = mapOf("x" to "a".repeat(Wire.MAX_FRAME_BYTES - 8))
        assertEquals(Wire.MAX_FRAME_BYTES, Wire.encode(exact).size)
        assertEquals(exact, Wire.decode(Wire.encode(exact)))
        assertThrows(DesktopProtocolException::class.java) { Wire.encode(mapOf("x" to "a".repeat(Wire.MAX_FRAME_BYTES - 7))) }
    }
}
