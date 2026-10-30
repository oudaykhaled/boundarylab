package dev.boundarylab.core

/**
 * The adapter has validated an opened read-only, regular, seekable whole file.
 * [size] is cached metadata: its getter must be bounded and nonblocking, with no
 * operating-system I/O or path-based query. Reads never use a shared cursor.
 * Ownership transfers to CoreEngine.submit on every outcome, including rejection.
 */
interface PositionalSource : AutoCloseable {
    val size: Long
    fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int
    /** Same-handle metadata and any required root/alias checks; false rejects success. */
    fun verifyUnchanged(): Boolean
    override fun close()
}
