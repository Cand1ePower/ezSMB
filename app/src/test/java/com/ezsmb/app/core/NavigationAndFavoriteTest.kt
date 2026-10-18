package com.ezsmb.app.core

import org.junit.Assert.*
import org.junit.Test

class NavigationAndFavoriteTest {
    @Test fun backWalksDirectoryHierarchyIncludingDirectFavorites() {
        assertEquals("browser" to "照片\\旅行", parentLocation("browser", "照片/旅行/2026"))
        assertEquals("browser" to "", parentLocation("browser", "照片"))
        assertEquals("shares" to "", parentLocation("browser", ""))
        assertEquals("home" to "", parentLocation("shares", "stale\\path"))
    }
    @Test fun favoriteIdentitySurvivesDhcpButNeverMatchesDifferentServer() {
        val f = Favorite("id", "server-a", "lan", "192.168.1.2", 445, "PC", "Photos", "旅行\\2026")
        val moved = Login(Target("192.168.1.77"), Credentials("user", "secret"), "server-a", "lan")
        assertTrue(f.matches(moved, "Photos", "旅行/2026"))
        assertFalse(f.matches(moved.copy(guid = "server-b"), "Photos", f.path))
        assertFalse(f.matches(moved.copy(target = Target("192.168.1.77", 1445)), "Photos", f.path))
        assertFalse(f.matches(moved, "Other", f.path))
        assertEquals("2026", f.title)
    }
    @Test fun relocationRequiresGuidAndPortNotIpOrName() {
        assertTrue(sameServer(Device("new-ip", guid = "a"), "a", 445))
        assertFalse(sameServer(Device("old-ip", guid = "b"), "a", 445))
        assertFalse(sameServer(Device("old-ip"), "", 445))
        assertFalse(sameServer(Device("new-ip", port = 1445, guid = "a"), "a", 445))
    }
}
