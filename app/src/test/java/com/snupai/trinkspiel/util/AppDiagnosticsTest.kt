package com.snupai.trinkspiel.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDiagnosticsTest {

    @Test
    fun diagnosticReportContainsTechnicalStateWithoutUserContent() {
        val report = AppDiagnostics.toJson(
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
                    message = "Example",
                    stackTrace = "stack",
                ),
            )
        )

        val json = JSONObject(report)
        val crash = json.getJSONObject("latestCrash")

        assertEquals("seemops.diagnostics", json.getString("type"))
        assertEquals(128, json.getInt("totalCards"))
        assertEquals(2, json.getInt("playerCount"))
        assertTrue(json.getBoolean("ageGateAccepted"))
        assertTrue(json.getBoolean("firstRunSetupCompleted"))
        assertEquals("java.lang.IllegalStateException", crash.getString("exceptionClass"))
        assertEquals("Example", crash.getString("message"))
    }

    @Test
    fun lastIssueReportIsFocusedOnLatestCrash() {
        val report = AppDiagnostics.lastIssueToJson(
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
                intensity = "low",
                themeMode = "system",
                dynamicColors = false,
                ageGateAccepted = true,
                firstRunSetupCompleted = true,
                latestCrash = CrashSummary(
                    timestamp = "2026-06-01T11:59:00Z",
                    exceptionClass = "java.lang.IllegalStateException",
                    message = "Example",
                    stackTrace = "stack",
                ),
            )
        )

        val json = JSONObject(requireNotNull(report))
        val crash = json.getJSONObject("latestCrash")

        assertEquals("seemops.last_issue", json.getString("type"))
        assertEquals("seemops_last_issue.json", AppDiagnostics.lastIssueFileName())
        assertEquals("low", json.getString("intensity"))
        assertEquals("stack", crash.getString("stackTrace"))
    }

    @Test
    fun lastIssueReportIsAbsentWithoutCrash() {
        val report = AppDiagnostics.lastIssueToJson(
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
                intensity = "low",
                themeMode = "system",
                dynamicColors = false,
                ageGateAccepted = true,
                firstRunSetupCompleted = true,
                latestCrash = null,
            )
        )

        assertEquals(null, report)
    }
}
