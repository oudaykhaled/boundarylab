package dev.boundarylab.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.util.Base64

class FieldPackTest {
    private val empty = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    private val abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    private fun valid() = """{"format":"fieldpack","version":1,"algorithm":"sha256","files":[{"name":"abc.txt","size":3,"sha256":"$abc"},{"name":"empty.bin","size":0,"sha256":"$empty"}]}"""
    private fun reject(text: String) { assertThrows(BoundaryException::class.java) { FieldPackCodec.decode(text.toByteArray()) } }

    @TestFactory fun `shared cross implementation manifest corpus`(): List<DynamicTest> {
        val candidates = listOf(File("../conformance/shared/manifest-v1.json"), File("conformance/shared/manifest-v1.json"))
        val corpus = candidates.firstOrNull { it.isFile }?.readText() ?: error("Shared manifest corpus missing")
        // The trusted test envelope has flat fields and base64 payloads. Parsing the manifest
        // itself is always through the production codec; no schema normalization occurs here.
        val rows = Regex("\\{[^{}]*\\\"rawBase64\\\"[^{}]*}").findAll(corpus).toList()
        assertTrue(rows.isNotEmpty())
        assertEquals(Regex("\\\"rawBase64\\\"\\s*:").findAll(corpus).count(), rows.size)
        return rows.map { row ->
            fun field(name: String): String = Regex("\\\"$name\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
                .find(row.value)?.groupValues?.get(1) ?: error("Missing corpus $name")
            val valid = Regex("\\\"valid\\\"\\s*:\\s*(true|false)").find(row.value)?.groupValues?.get(1)
                ?: error("Missing valid flag")
            DynamicTest.dynamicTest(field("id")) {
                val raw = Base64.getDecoder().decode(field("rawBase64"))
                if (valid == "true") {
                    val canonical = FieldPackCodec.encode(FieldPackCodec.decode(raw))
                    assertArrayEquals(Base64.getDecoder().decode(field("canonicalBase64")), canonical)
                } else {
                    assertThrows(BoundaryException::class.java) { FieldPackCodec.decode(raw) }
                }
            }
        }
    }

    @Test fun `canonical output sorts and exactly matches shared two file vector`() {
        val manifest = FieldPackManifest(listOf(FieldPackEntry("empty.bin", 0, empty), FieldPackEntry("abc.txt", 3, abc)))
        assertEquals(valid() + "\n", FieldPackCodec.encode(manifest).toString(Charsets.UTF_8))
        assertEquals(manifest.files.sortedBy { it.name }, FieldPackCodec.decode(valid().toByteArray()).files)
    }

    @Test fun `rejects duplicate keys even when escaped and unknown fields`() {
        reject(valid().replace("\"version\":1", "\"version\":1,\"version\":1"))
        reject(valid().replace("\"version\":1", "\"version\":1,\"\\u0076ersion\":1"))
        reject(valid().replace("\"size\":3", "\"size\":3,\"size\":3"))
        reject(valid().replace("\"size\":3", "\"size\":3,\"privatePath\":\"secret\""))
    }

    @Test fun `integers are strict and ranges checked before accumulation`() {
        for (token in listOf("true", "false", "1.0", "1e0", "null", "\"1\"", "NaN", "Infinity", "9223372036854775808")) {
            reject(valid().replace("\"version\":1", "\"version\":$token"))
            reject(valid().replace("\"size\":3", "\"size\":$token"))
        }
        reject(valid().replace("\"size\":3", "\"size\":-1"))
        reject(valid().replace("\"size\":3", "\"size\":134217729"))
        reject(valid().replace("\"size\":3", "\"size\":134217728").replace("\"size\":0", "\"size\":1"))
        assertEquals(134217728L, FieldPackCodec.decode(valid().replace("\"size\":3", "\"size\":134217728").toByteArray()).files.first().size)
    }

    @Test fun `alias corpus rejects traversal reserved names nonascii and uppercase`() {
        for (name in listOf("", ".", "..", "../x", "a/b", "a\\b", "a:b", "a%2fb", "a b", "Ä", "ABC", "con", "con.txt", "prn", "nul", "aux", "com1.log", "lpt9", "a.", "a".repeat(65))) {
            assertFalse(FieldPackCodec.validateAlias(name), name)
        }
        for (name in listOf("a", "abc.txt", "company.txt", "com0", "a".repeat(64))) assertTrue(FieldPackCodec.validateAlias(name), name)
    }

    @Test fun `strict complete schema encoding sorting and byte cap`() {
        reject(valid().replace("\"fieldpack\"", "\"other\""))
        reject(valid().replace("\"sha256\",\"files\"", "\"sha512\",\"files\""))
        reject(valid().replace("\"abc.txt\"", "\"empty.bin\""))
        reject(valid().replace("\"abc.txt\"", "\"z.txt\""))
        reject(valid().replace(abc, abc.uppercase()))
        reject(valid().replace(abc, abc.dropLast(1)))
        reject("\uFEFF" + valid())
        reject(valid() + " trailing")
        reject(valid().replace("\"files\":[", "\"files\":[],\"x\":["))
        assertThrows(BoundaryException::class.java) { FieldPackCodec.decode(byteArrayOf(0xc3.toByte(), 0x28)) }
        val exact = valid() + " ".repeat(16384 - valid().toByteArray().size)
        assertEquals(2, FieldPackCodec.decode(exact.toByteArray()).files.size)
        reject(exact + " ")
    }

    @Test fun `entry count bounds and writer validation`() {
        val entries = (0 until 32).map { FieldPackEntry("f%02d.bin".format(it), 0, empty) }
        assertEquals(32, FieldPackCodec.decode(FieldPackCodec.encode(FieldPackManifest(entries))).files.size)
        for (invalid in listOf(emptyList(), entries + FieldPackEntry("z.bin", 0, empty), entries + entries.first(), listOf(FieldPackEntry("con", 0, empty)))) {
            assertThrows(BoundaryException::class.java) { FieldPackCodec.encode(FieldPackManifest(invalid)) }
        }
    }

    @Test fun `reader accepts whitespace and reordered object keys but rejects malformed JSON`() {
        val reordered = """{ "files": [{"sha256":"$empty","size":0,"name":"empty.bin"}], "algorithm":"sha256", "version":1,"format":"fieldpack" }"""
        assertEquals(0L, FieldPackCodec.decode(reordered.toByteArray()).files.single().size)
        for (malformed in listOf("", "[]", "{}", valid().dropLast(1), valid().replace("\"size\":3", "\"size\":03"), valid().replace("\"size\":3", "\"size\":3."), valid().replace("\"size\":3", "\"size\":3e+"), valid().replace("\"abc.txt\"", "\"abc\n.txt\""))) reject(malformed)
    }
}
