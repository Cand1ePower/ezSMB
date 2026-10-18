package com.ezsmb.app.core

import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    @Test fun excludesVpnEvenWhenItAdvertisesUnderlyingWifi() {
        assertFalse(isPhysicalLan(wifi = true, ethernet = false, vpn = true))
        assertFalse(isPhysicalLan(wifi = false, ethernet = true, vpn = true))
        assertFalse(isPhysicalLan(wifi = false, ethernet = false, vpn = false))
        assertTrue(isPhysicalLan(wifi = true, ethernet = false, vpn = false))
        assertTrue(isPhysicalLan(wifi = false, ethernet = true, vpn = false))
    }
    @Test fun parsesUnicodePathsAndNonstandardPort() {
        val t = Target.parse("smb://nas.local:1445/共享/资料%20一/😀")
        assertEquals("nas.local", t.host); assertEquals(1445, t.port); assertEquals("共享", t.share); assertEquals("资料 一\\😀", t.path)
    }
    @Test fun preservesPlusInNames() { assertEquals("a+b", Target.parse("smb://host/share/a+b").path) }
    @Test fun rejectsCredentialsInUri() { assertThrows(IllegalArgumentException::class.java) { Target.parse("smb://user:password@host/share") } }
    @Test fun rejectsTraversal() { listOf("../a", "a/../b", "a/%2e%2e/b").forEach { assertThrows(IllegalArgumentException::class.java) { Target.parse("smb://host/share/$it") } } }
    @Test fun rejectsPortOutsideRange() { assertThrows(IllegalArgumentException::class.java) { Target.parse("host:65536") } }
    @Test fun boundsLargeSubnetsAndExcludesSelf() { val hosts = scanCandidates("10.3.7.32", 16); assertTrue(hosts.size <= 256); assertFalse("10.3.7.32" in hosts); assertTrue(hosts.all { it.startsWith("10.3.7.") }) }
    @Test fun respectsSmallSubnetBoundary() { assertEquals(listOf("192.168.1.2"), scanCandidates("192.168.1.1", 30)) }
    @Test fun neverScansPublicOrLinkLocalAddresses() { assertTrue(scanCandidates("8.8.8.8", 24).isEmpty()); assertTrue(scanCandidates("169.254.1.3", 16).isEmpty()) }
    @Test fun excludesNetworkAndBroadcastIn24() { val hosts = scanCandidates("192.168.2.33", 24); assertEquals(253, hosts.size); assertFalse("192.168.2.0" in hosts); assertFalse("192.168.2.255" in hosts) }
    @Test fun normalizesPathsAndRejectsAlternateStreams() { assertEquals("中文\\a", remotePath("/中文/a")); assertThrows(IllegalArgumentException::class.java) { childPath("", "file:secret") }; assertThrows(IllegalArgumentException::class.java) { childPath("", "../file") } }
    @Test fun renamePreservesExtension() { assertEquals("report (2).pdf", renamed("report.pdf", 2)); assertEquals(".hidden (1)", renamed(".hidden", 1)) }
    @Test fun naturalOrderHandlesLargeNumbers() { assertEquals(listOf("a2", "a10", "a999999999999999999999"), listOf("a10", "a999999999999999999999", "a2").sortedWith(naturalNames)) }
    @Test fun scopesIdentityToNetworkAndGuid() { assertNotEquals(Device("192.168.1.2", guid = "A", network = "one").identity, Device("192.168.1.2", guid = "B", network = "two").identity) }
    @Test fun errorsAreActionable() { assertTrue(friendlyError(Exception("STATUS_LOGON_FAILURE")).contains("密码")); assertTrue(friendlyError(Exception("STATUS_DISK_FULL")).contains("空间")) }
    @Test fun credentialsNeverUseDataClassToString() { assertFalse(Credentials("private", "secret").toString().contains("secret")) }
}
