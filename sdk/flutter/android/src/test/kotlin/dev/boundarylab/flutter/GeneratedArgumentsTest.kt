package dev.boundarylab.flutter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeneratedArgumentsTest {
    @Test fun acceptsOnlySignedIntegerSeedsAndBoundedIntegerBytes() {
        assertEquals(GeneratedArguments(Long.MIN_VALUE, 0), GeneratedArguments.decode(mapOf("seed" to Long.MIN_VALUE, "bytes" to 0)))
        assertEquals(GeneratedArguments(Long.MAX_VALUE, 134217728), GeneratedArguments.decode(mapOf("seed" to Long.MAX_VALUE, "bytes" to 134217728L)))
    }
    @Test fun rejectsFloatingBooleanStringMissingAndOverflowValues() {
        val invalid = listOf(1.0, 1.0f, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, true, "1", null, java.math.BigInteger("9223372036854775808"))
        invalid.forEach { value ->
            assertNull("invalid seed: $value", GeneratedArguments.decode(mapOf("seed" to value, "bytes" to 0)))
            assertNull("invalid bytes: $value", GeneratedArguments.decode(mapOf("seed" to 0, "bytes" to value)))
        }
        assertNull(GeneratedArguments.decode(mapOf("seed" to 0, "bytes" to -1)))
        assertNull(GeneratedArguments.decode(mapOf("seed" to 0, "bytes" to 134217729L)))
        assertNull(GeneratedArguments.decode(mapOf("seed" to 0)))
        assertNull(GeneratedArguments.decode(mapOf("seed" to 0, "bytes" to 0, "deadline" to 0)))
    }
}
