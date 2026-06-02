package com.snupai.trinkspiel.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteSyncConfigTest {
    @Test
    fun normalizedTrimsEndpointTokenAndKnownRole() {
        val config = RemoteSyncConfig(
            endpointUrl = " https://sync.example.test/api/ ",
            accessToken = " token ",
            role = " WRITE ",
        ).normalized()

        assertEquals("https://sync.example.test/api", config.endpointUrl)
        assertEquals("token", config.accessToken)
        assertEquals("write", config.role)
    }

    @Test
    fun normalizedDropsUnknownRole() {
        assertEquals(
            "",
            RemoteSyncConfig(role = "owner").normalized().role,
        )
    }
}
