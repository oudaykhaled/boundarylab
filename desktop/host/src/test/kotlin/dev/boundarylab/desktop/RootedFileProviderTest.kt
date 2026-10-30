package dev.boundarylab.desktop

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.io.RandomAccessFile

class RootedFileProviderTest {
    @TempDir lateinit var temporary: Path
    private fun root(): Path = Files.createDirectory(temporary.resolve("inputs"))

    @Test fun `pinned source supports empty and positional bounded reads`() {
        val dir = root()
        Files.write(dir.resolve("abc.txt"), "abc".toByteArray())
        Files.write(dir.resolve("empty.bin"), byteArrayOf())
        RootedFileProvider(dir).use { provider ->
            provider.open("abc.txt").use { source ->
                assertEquals(3L, source.size)
                val buffer = ByteArray(65_536)
                assertEquals(1, source.readAt(2, buffer, 3, 1)); assertEquals('c'.code.toByte(), buffer[3])
                assertEquals(3, source.readAt(0, buffer, 0, 3)); assertEquals("abc", String(buffer,0,3))
                assertEquals(-1, source.readAt(3,buffer,0,1))
                assertTrue(source.verifyUnchanged())
                assertThrows(IllegalArgumentException::class.java) { source.readAt(0,ByteArray(65_537),0,65_537) }
                assertThrows(IllegalArgumentException::class.java) { source.readAt(-1,buffer,0,1) }
                source.close(); source.close()
                assertFalse(source.verifyUnchanged())
                assertThrows(java.io.IOException::class.java) { source.readAt(0,buffer,0,1) }
            }
            provider.open("empty.bin").use { source ->
                assertEquals(0L,source.size)
                assertEquals(-1,source.readAt(0,ByteArray(1),0,1))
                assertTrue(source.verifyUnchanged())
            }
        }
    }

    @Test fun `unsafe types links aliases and missing files have explicit failures`() {
        val dir = root()
        Files.writeString(dir.resolve("regular.txt"),"abc")
        Files.createSymbolicLink(dir.resolve("symbolic.txt"),dir.resolve("regular.txt"))
        Files.createLink(dir.resolve("hard.txt"),dir.resolve("regular.txt"))
        Files.createDirectory(dir.resolve("directory"))
        val fifo = ProcessBuilder("mkfifo",dir.resolve("fifo").toString()).start()
        assertEquals(0,fifo.waitFor())
        RootedFileProvider(dir).use { provider ->
            for (name in listOf("symbolic.txt","hard.txt","regular.txt","directory","fifo")) {
                assertEquals("UNREADABLE_OR_UNSUPPORTED",assertThrows(DesktopProtocolException::class.java) { provider.open(name) }.code,name)
            }
            assertEquals("MISSING",assertThrows(DesktopProtocolException::class.java) { provider.open("missing") }.code)
            for (name in listOf("../escape","A.txt","con.txt","trailing.","a/b","nul")) {
                assertEquals("INVALID",assertThrows(DesktopProtocolException::class.java) { provider.open(name) }.code)
            }
        }
    }

    @Test fun `mutation growth shrink alias replacement and root replacement reject success`() {
        val dir = root()
        val file = dir.resolve("input.bin")
        Files.writeString(file,"abc")
        RootedFileProvider(dir).use { provider ->
            provider.open("input.bin").use { source ->
                Files.writeString(file,"x",StandardOpenOption.APPEND)
                assertFalse(source.verifyUnchanged())
            }
            provider.open("input.bin").use { source ->
                Files.writeString(file,"a",StandardOpenOption.TRUNCATE_EXISTING)
                assertFalse(source.verifyUnchanged())
            }
            provider.open("input.bin").use { source ->
                Files.move(file,dir.resolve("old.bin"))
                Files.writeString(file,"z")
                assertFalse(source.verifyUnchanged())
                val old = ByteArray(1)
                assertEquals(1,source.readAt(0,old,0,1)); assertEquals('a'.code.toByte(),old[0])
            }
            provider.open("input.bin").use { source ->
                Files.move(dir,temporary.resolve("moved"))
                Files.createDirectory(dir)
                Files.writeString(dir.resolve("input.bin"),"outside")
                assertFalse(source.verifyUnchanged())
                assertEquals("UNREADABLE_OR_UNSUPPORTED",assertThrows(DesktopProtocolException::class.java) { provider.open("input.bin") }.code)
            }
        }
    }

    @Test fun `oversized regular file rejected before content read and exact cap supported`() {
        val dir = root()
        val file = dir.resolve("large.bin")
        RandomAccessFile(file.toFile(),"rw").use { it.setLength(134_217_729) }
        RootedFileProvider(dir).use { provider ->
            assertEquals("UNREADABLE_OR_UNSUPPORTED",assertThrows(DesktopProtocolException::class.java) { provider.open("large.bin") }.code)
            RandomAccessFile(file.toFile(),"rw").use { it.setLength(134_217_728) }
            provider.open("large.bin").use { source ->
                assertEquals(134_217_728L,source.size)
                assertEquals(-1,source.readAt(134_217_728,ByteArray(1),0,1))
                assertTrue(source.verifyUnchanged())
            }
        }
    }

    @Test fun `output authorization is outside root and rejects existing targets`() {
        val dir = root()
        RootedFileProvider(dir).use { provider ->
            provider.approveOutput(temporary.resolve("manifest.json"))
            assertThrows(DesktopProtocolException::class.java) { provider.approveOutput(dir.resolve("manifest.json")) }
            Files.createSymbolicLink(temporary.resolve("alias"),dir)
            assertThrows(DesktopProtocolException::class.java) { provider.approveOutput(temporary.resolve("alias/manifest.json")) }
            Files.writeString(temporary.resolve("existing.json"),"unchanged")
            assertThrows(DesktopProtocolException::class.java) { provider.approveOutput(temporary.resolve("existing.json")) }
        }
    }

    @Test fun `one hundred success rejection and closure cycles recover descriptors`() {
        val dir = root()
        Files.writeString(dir.resolve("good.txt"),"abc")
        Files.createSymbolicLink(dir.resolve("bad.txt"),dir.resolve("good.txt"))
        fun descriptors() = Files.list(Path.of("/dev/fd")).use { it.count() }
        val before = descriptors()
        repeat(100) {
            RootedFileProvider(dir).use { provider ->
                provider.open("good.txt").use { assertTrue(it.verifyUnchanged()) }
                assertThrows(DesktopProtocolException::class.java) { provider.open("bad.txt") }
            }
        }
        assertTrue(descriptors() <= before + 2,"native descriptors did not recover")
    }

    @Test fun `seventeen total input slots bound opened and pending admission descriptors`() {
        val dir = root()
        Files.writeString(dir.resolve("good.txt"), "abc")
        RootedFileProvider(dir).use { provider ->
            val sources = (1..17).map { provider.open("good.txt") }
            try {
                assertEquals("BUSY", assertThrows(DesktopProtocolException::class.java) { provider.open("good.txt") }.code)
                sources.first().close()
                provider.open("good.txt").use { assertTrue(it.verifyUnchanged()) }
            } finally { sources.forEach { it.close() } }
            provider.open("good.txt").use { assertTrue(it.verifyUnchanged()) }
        }
    }

    @Test fun `native ABI rejects invalid array bounds and alias traversal`() {
        val dir = root()
        Files.writeString(dir.resolve("good.txt"),"abc")
        val root = NativeFiles.openRoot(dir.toString())
        try {
            assertThrows(java.io.IOException::class.java) { NativeFiles.openAlias(root,"../escape") }
            assertThrows(java.io.IOException::class.java) { NativeFiles.openAlias(root,"con.txt") }
            val file = NativeFiles.openAlias(root,"good.txt")
            try {
                assertThrows(java.io.IOException::class.java) { NativeFiles.pread(file,ByteArray(1),Int.MAX_VALUE,1,0) }
                assertThrows(java.io.IOException::class.java) { NativeFiles.pread(file,ByteArray(1),0,65_537,0) }
                assertThrows(java.io.IOException::class.java) { NativeFiles.pread(file,ByteArray(1),0,1,Long.MAX_VALUE) }
            } finally { NativeFiles.close(file) }
        } finally { NativeFiles.close(root) }
    }
}
