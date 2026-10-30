package dev.boundarylab.desktop

import java.io.Closeable
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.nio.file.Path

/** One connection owns all its sessions. A lost connection is never replayed. */
class DesktopClient(socket: Path) : Closeable {
    private val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
    private var lost = false

    init {
        try {
            channel.configureBlocking(false)
            channel.connect(UnixDomainSocketAddress.of(socket))
            Selector.open().use { selector ->
                channel.register(selector, SelectionKey.OP_CONNECT)
                val deadline = System.nanoTime() + Wire.FRAME_TIMEOUT_MILLIS * 1_000_000
                while (!channel.finishConnect()) {
                    val remaining = deadline - System.nanoTime()
                    if (remaining <= 0) throw IOException("Connection deadline elapsed")
                    selector.select(((remaining + 999_999) / 1_000_000).coerceAtLeast(1))
                    selector.selectedKeys().clear()
                }
            }
        } catch (failure: Throwable) { channel.close(); throw failure }
    }

    @Synchronized
    fun request(request: Map<String, Any?>): Map<String, Any?> {
        if (lost) throw DesktopProtocolException("LOST_OR_UNKNOWN")
        // Local rejection happens before any bytes leave; it is not unknown admission.
        Wire.encode(request)
        try {
            Wire.write(channel, request)
            return Wire.read(channel)
        } catch (_: IOException) {
            lost = true
            channel.close()
            throw DesktopProtocolException("LOST_OR_UNKNOWN")
        }
    }

    @Synchronized
    override fun close() { lost = true; channel.close() }
}
