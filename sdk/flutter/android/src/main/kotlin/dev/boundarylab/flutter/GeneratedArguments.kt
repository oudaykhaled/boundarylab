package dev.boundarylab.flutter

/** StandardMessageCodec integers are Int or Long; floating point never coerces. */
internal data class GeneratedArguments(val seed: Long, val bytes: Long) {
    companion object {
        fun decode(arguments: Any?): GeneratedArguments? {
            val values = arguments as? Map<*, *> ?: return null
            if (values.keys != setOf("seed", "bytes")) return null
            val seed = integer(values["seed"]) ?: return null
            val bytes = integer(values["bytes"]) ?: return null
            if (bytes !in 0L..134217728L) return null
            return GeneratedArguments(seed, bytes)
        }
        private fun integer(value: Any?): Long? = when (value) {
            is Int -> value.toLong()
            is Long -> value
            else -> null
        }
    }
}
