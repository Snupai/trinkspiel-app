package com.snupai.trinkspiel.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportRequestTest {

    @Test
    fun supportRequestContainsTechnicalContextWithoutUserContent() {
        val body = SupportRequest.buildBody(
            DiagnosticSnapshot(
                generatedAt = "2026-06-01T12:00:00Z",
                appVersionName = "3.0",
                appVersionCode = 3,
                androidSdk = 36,
                device = "Google Medium Phone",
                totalCards = 128,
                activeCards = 126,
                packCount = 8,
                playerCount = 2,
                mode = "classic",
                intensity = "medium",
                themeMode = "system",
                dynamicColors = false,
                ageGateAccepted = true,
                firstRunSetupCompleted = true,
                latestCrash = CrashSummary(
                    timestamp = "2026-06-01T11:59:00Z",
                    exceptionClass = "java.lang.IllegalStateException",
                    message = "Example crash",
                    stackTrace = "stack trace",
                ),
            )
        )

        assertTrue(body.contains("Hallo Seemops-Support"))
        assertTrue(body.contains("App-Version: 3.0 (3)"))
        assertTrue(body.contains("Karten aktiv/gesamt: 126/128"))
        assertTrue(body.contains("Spieleranzahl: 2"))
        assertTrue(body.contains("Letzter Absturz: java.lang.IllegalStateException"))
        assertTrue(body.contains("keine Kartentexte"))
        assertTrue(body.contains("keine Spielernamen"))
        assertFalse(body.contains("stack trace"))
    }
}
