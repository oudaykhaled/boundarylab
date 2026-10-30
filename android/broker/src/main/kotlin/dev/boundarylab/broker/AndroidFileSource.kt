package dev.boundarylab.broker

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import dev.boundarylab.core.PositionalSource
import java.util.concurrent.atomic.AtomicBoolean

/** Owns one received PFD. Never opens a URI/path and never changes its shared cursor. */
class AndroidFileSource private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val initial: StructStat,
    private val onClose: () -> Unit,
) : PositionalSource {
    private val closed = AtomicBoolean(false)
    override val size: Long = initial.st_size

    override fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int {
        check(!closed.get())
        require(offset >= 0 && length in 0..65_536)
        val count = Os.pread(descriptor.fileDescriptor, buffer, bufferOffset, length, offset)
        return if (count == 0 && length > 0) -1 else count
    }

    override fun verifyUnchanged(): Boolean {
        val current = Os.fstat(descriptor.fileDescriptor)
        return current.st_dev == initial.st_dev && current.st_ino == initial.st_ino &&
            current.st_size == initial.st_size && current.st_nlink == 1L &&
            current.st_mtim.tv_sec == initial.st_mtim.tv_sec &&
            current.st_mtim.tv_nsec == initial.st_mtim.tv_nsec &&
            current.st_ctim.tv_sec == initial.st_ctim.tv_sec &&
            current.st_ctim.tv_nsec == initial.st_ctim.tv_nsec
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try { descriptor.close() } finally { onClose() }
        }
    }

    companion object {
        /** Ownership transfers only on success; caller closes failures. Metadata only, no reads. */
        fun take(descriptor: ParcelFileDescriptor, onClose: () -> Unit = {}): AndroidFileSource {
            val fd = descriptor.fileDescriptor
            val flags = Os.fcntlInt(fd, OsConstants.F_GETFL, 0)
            require(flags and OsConstants.O_ACCMODE == OsConstants.O_RDONLY) { "READ_ONLY_REQUIRED" }
            val stat = Os.fstat(fd)
            require(OsConstants.S_ISREG(stat.st_mode)) { "REGULAR_FILE_REQUIRED" }
            require(stat.st_nlink == 1L) { "SINGLE_LINK_REQUIRED" }
            require(stat.st_size in 0L..134_217_728L) { "FILE_SIZE_LIMIT" }
            require(Os.lseek(fd, 0, OsConstants.SEEK_CUR) >= 0) { "SEEKABLE_REQUIRED" }
            return AndroidFileSource(descriptor, stat, onClose)
        }
    }
}
