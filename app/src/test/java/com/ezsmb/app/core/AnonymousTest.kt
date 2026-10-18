package com.ezsmb.app.core

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class AnonymousTest {
    @Test fun anonymousIsExplicitAndHasNoCredentials() {
        assertFalse(Credentials("", "").anonymous)
        assertTrue(Credentials.anonymous().anonymous)
        assertEquals("", Credentials.anonymous().password)
        assertThrows(IllegalArgumentException::class.java) { Credentials("account", "secret", anonymous = true) }
    }
    @Test fun realAnonymousEnumerationAndRead() {
        assumeTrue(System.getenv("EZSMB_ANON_TEST") == "1")
        val engine = SmbEngine()
        val target = Target("127.0.0.1", 1445)
        engine.open(target).use { assertTrue(engine.anonymousAccess(it, target)) }
        engine.open(target).use { assertTrue(engine.anonymousAccess(it, target.copy(share = "EZTEST"))) }
        engine.open(target).use { assertFalse(engine.anonymousAccess(it, target.copy(share = "DOES_NOT_EXIST"))) }
    }
}
