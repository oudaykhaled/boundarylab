package dev.boundarylab.desktop

import dev.boundarylab.core.FieldPackCodec
import dev.boundarylab.core.PositionalSource
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.concurrent.Semaphore

/**
 * A descriptor-pinned root; source content is always read through openat/pread.
 * Name/path metadata revalidation detects observed replacement, not all races or snapshots.
 */
class RootedFileProvider(root: Path) : DesktopFileProvider {
    private val rootPath = root.toAbsolutePath().normalize()
    private var rootFd = -1
    private val sourceSlots = Semaphore(17)
    private val rootIdentity: LongArray
    override val supported = true

    init {
        try {
            rootFd = NativeFiles.openRoot(rootPath.toString())
            rootIdentity = NativeFiles.snapshot(rootFd)
            if (!rootUnchanged()) throw DesktopProtocolException("UNSUPPORTED")
        } catch (error: Throwable) {
            if (rootFd >= 0) {
                val owned = rootFd
                rootFd = -1
                try { NativeFiles.close(owned) } catch (_: IOException) { }
            }
            if (error is VirtualMachineError || error is ThreadDeath) throw error
            throw DesktopProtocolException("UNSUPPORTED")
        }
    }

    @Synchronized
    override fun open(alias: String): PositionalSource {
        if (!FieldPackCodec.validateAlias(alias)) throw DesktopProtocolException("INVALID")
        if (!rootUnchanged()) throw DesktopProtocolException("UNREADABLE_OR_UNSUPPORTED")
        if (!sourceSlots.tryAcquire()) throw DesktopProtocolException("BUSY")
        var transferred = false
        var fd = -1
        try {
            fd = NativeFiles.openAlias(rootFd, alias)
            val before = NativeFiles.snapshot(fd)
            if (!NativeFiles.revalidate(rootFd, alias, fd) || !rootUnchanged()) {
                throw DesktopProtocolException("UNREADABLE_OR_UNSUPPORTED")
            }
            return NativeSource(alias, fd, before).also { fd = -1; transferred = true }
        } catch (error: IOException) {
            throw DesktopProtocolException(if (error.message == "POSIX errno=2") "MISSING" else "UNREADABLE_OR_UNSUPPORTED")
        } finally {
            if (fd >= 0) try { NativeFiles.close(fd) } catch (_: IOException) { }
            if (!transferred) sourceSlots.release()
        }
    }

    @Synchronized
    override fun approveOutput(output: Path) {
        if (!rootUnchanged()) throw DesktopProtocolException("UNREADABLE_OR_UNSUPPORTED")
        try {
            val resolvedRoot = rootPath.toRealPath()
            val target = output.toAbsolutePath().normalize()
            val parent = target.parent?.toRealPath() ?: throw DesktopProtocolException("INVALID")
            if (parent.startsWith(resolvedRoot)) throw DesktopProtocolException("INVALID")
            if (Files.exists(target, NOFOLLOW_LINKS)) throw DesktopProtocolException("INVALID")
            if (!rootUnchanged()) throw DesktopProtocolException("UNREADABLE_OR_UNSUPPORTED")
        } catch (_: IOException) { throw DesktopProtocolException("INVALID") }
    }

    private fun rootUnchanged(): Boolean {
        if (rootFd < 0) return false
        return try {
            val attrs = Files.readAttributes(rootPath, "unix:dev,ino,mode", NOFOLLOW_LINKS)
            val current = NativeFiles.snapshot(rootFd)
            current[0] == rootIdentity[0] && current[1] == rootIdentity[1] &&
                (attrs["dev"] as Number).toLong() == rootIdentity[0] &&
                (attrs["ino"] as Number).toLong() == rootIdentity[1] &&
                ((attrs["mode"] as Number).toInt() and 0xF000) == 0x4000
        } catch (_: IOException) { false }
    }

    @Synchronized
    override fun close() {
        val owned = rootFd
        rootFd = -1
        if (owned >= 0) NativeFiles.close(owned)
    }

    private inner class NativeSource(
        private val alias: String,
        private var fd: Int,
        private val before: LongArray,
    ) : PositionalSource {
        override val size = before[4]

        @Synchronized
        override fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int {
            if (fd < 0) throw IOException("closed source")
            require(offset in 0..FieldPackCodec.MAX_TOTAL_BYTES)
            require(length in 1..65_536 && bufferOffset >= 0 && bufferOffset <= buffer.size && length <= buffer.size-bufferOffset)
            return NativeFiles.pread(fd, buffer, bufferOffset, length, offset)
        }

        @Synchronized
        override fun verifyUnchanged(): Boolean {
            if (fd < 0) return false
            return synchronized(this@RootedFileProvider) {
                try {
                    rootUnchanged() && before.contentEquals(NativeFiles.snapshot(fd)) &&
                        NativeFiles.revalidate(rootFd, alias, fd)
                } catch (_: IOException) { false }
            }
        }

        @Synchronized
        override fun close() {
            val owned = fd
            fd = -1
            if (owned >= 0) try { NativeFiles.close(owned) } finally { sourceSlots.release() }
        }
    }
}
