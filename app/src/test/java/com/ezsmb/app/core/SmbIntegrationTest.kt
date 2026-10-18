package com.ezsmb.app.core

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID

/** Runs the exact production SMB engine, over TCP, against an explicitly configured test server. */
class SmbIntegrationTest {
    private fun fixture(): Properties {
        val path = System.getenv("EZSMB_TEST_LOGIN")
        assumeTrue("No integration server configured", !path.isNullOrEmpty())
        return Properties().apply { File(path!!).inputStream().use { load(it) } }
    }
    @Test fun authenticationEnumerationUnicodeAndHashRoundTrip() {
        val p = fixture(); val engine = SmbEngine()
        val target = Target(p.getProperty("host"), p.getProperty("port").toInt())
        val credentials = Credentials(p.getProperty("user"), p.getProperty("password"))
        engine.open(target).use { link ->
            assertTrue(link.guid.isNotBlank())
            println("NEGOTIATED: ${link.dialect}; signing required by client")
            link.authenticate(credentials).use { session ->
                val shares = engine.shares(session)
                assertTrue(p.getProperty("share") in shares); assertFalse("IPC$" in shares)
                println("SRVSVC enumeration: disk share returned")
                engine.disk(session, p.getProperty("share")).use { disk ->
                    val list = engine.list(disk, "")
                    for (source in list.filter { !it.directory && !it.name.startsWith("roundtrip-") && !it.name.startsWith(".ezsmb-part-") }) {
                        val name = "roundtrip-${UUID.randomUUID()}-${source.name}"
                        val temporary = ".ezsmb-part-${UUID.randomUUID()}"
                        val readHash = MessageDigest.getInstance("SHA-256")
                        val outputHash = MessageDigest.getInstance("SHA-256")
                        var copied = 0L
                        try {
                            engine.read(disk, source.name).use { input ->
                                engine.createTemp(disk, temporary).use { output ->
                                    val buffer = ByteArray(256 * 1024)
                                    while (copied < source.size) {
                                        val n = input.read(buffer, copied, 0, minOf(buffer.size.toLong(), source.size - copied).toInt())
                                        assertTrue(n > 0); readHash.update(buffer, 0, n)
                                        var offset = 0
                                        while (offset < n) { val written = output.write(buffer, copied, offset, n - offset).toInt(); assertTrue(written > 0); offset += written; copied += written }
                                    }
                                    output.flush(); assertEquals(source.size, output.fileInformation.standardInformation.endOfFile)
                                    output.rename(name, false)
                                }
                            }
                            engine.read(disk, name).use { input ->
                                val buffer = ByteArray(256 * 1024); var count = 0L
                                while (count < copied) { val n = input.read(buffer, count, 0, minOf(buffer.size.toLong(), copied - count).toInt()); assertTrue(n > 0); outputHash.update(buffer, 0, n); count += n }
                            }
                            assertArrayEquals(readHash.digest(), outputHash.digest())
                            println("SHA256 roundtrip verified: ${source.name}; bytes=$copied")
                        } finally { runCatching { disk.rm(name) }; runCatching { disk.rm(temporary) } }
                    }
                    assertThrows(Exception::class.java) { engine.createTemp(disk, "zero.bin").close() }
                    assertEquals(0L, engine.read(disk, "zero.bin").use { it.fileInformation.standardInformation.endOfFile })
                }
            }
        }
    }
    @Test fun badPasswordAndWrongServerIdentityFail() {
        val p = fixture(); val engine = SmbEngine(); val target = Target(p.getProperty("host"), p.getProperty("port").toInt())
        engine.open(target).use { link -> assertThrows(Exception::class.java) { link.authenticate(Credentials(p.getProperty("user"), "deliberately-wrong")) } }
        engine.open(target).use { link -> assertThrows(IllegalStateException::class.java) { link.authenticate(Credentials(p.getProperty("user"), p.getProperty("password")), "different-guid") } }
    }
}
