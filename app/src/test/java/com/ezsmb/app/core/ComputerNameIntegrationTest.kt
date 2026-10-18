package com.ezsmb.app.core

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class ComputerNameIntegrationTest {
    @Test fun readsNameFromSmbChallengeWithoutCredentials() {
        val host = System.getenv("EZSMB_NAME_HOST")
        val expected = System.getenv("EZSMB_EXPECTED_NAME")
        assumeTrue(!host.isNullOrBlank() && !expected.isNullOrBlank())
        SmbEngine().open(Target(host!!)).use { link -> assertEquals(expected, link.probeName()) }
    }
}
