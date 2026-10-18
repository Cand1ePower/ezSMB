package com.ezsmb.app.core

import org.junit.Assert.*
import org.junit.Test

class PreviewAndNameTest {
    private fun response(group: Boolean = false): ByteArray {
        val packet = ByteArray(43)
        packet[0] = 0x12; packet[1] = 0x34; packet[2] = 0x84.toByte(); packet[7] = 1
        packet[12] = 0xc0.toByte(); packet[13] = 12; packet[15] = 33; packet[17] = 1
        packet[23] = 19; packet[24] = 1
        "MY-PC          ".toByteArray().copyInto(packet, 25)
        packet[40] = 32; if (group) packet[41] = 0x80.toByte()
        return packet
    }
    @Test fun parsesComputerNameAndRejectsWorkgroups() {
        assertEquals("MY-PC", NetbiosName.parse(response(), 0x1234))
        assertNull(NetbiosName.parse(response(true), 0x1234))
    }
    @Test fun rejectsTruncatedAndUnrelatedPackets() {
        val packet = response()
        for (size in 0 until packet.size) assertNull(NetbiosName.parse(packet.copyOf(size), 0x1234))
        assertNull(NetbiosName.parse(packet, 7))
    }
    @Test fun wildcardQueryIsNodeStatusNotNameRegistration() {
        val packet = NetbiosName.query(0x1234)
        assertEquals(50, packet.size); assertEquals(33, packet[47].toInt()); assertEquals(0, packet[2].toInt())
    }
    @Test fun identifiesMediaWithoutTreatingEveryFileAsImage() {
        assertEquals(PreviewKind.IMAGE, previewKind("中文.JPG"))
        assertEquals(PreviewKind.VIDEO, previewKind("movie.MP4"))
        assertNull(previewKind("backup.zip")); assertNull(previewKind("jpg"))
    }
    @Test fun limitsDecodedImageDimensions() {
        assertEquals(1, previewSample(320, 240))
        val sample = previewSample(16000, 12000)
        assertTrue(16000 / sample <= 768)
        assertThrows(IllegalArgumentException::class.java) { previewSample(-1, -1) }
    }
}
