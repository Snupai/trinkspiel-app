package com.snupai.trinkspiel.util

object SupportRequest {
    const val SUBJECT = "Seemops Trinkspiel Support"

    fun buildBody(snapshot: DiagnosticSnapshot): String = buildString {
        appendLine("Hallo Seemops-Support,")
        appendLine()
        appendLine("Beschreibe hier kurz, was passiert ist:")
        appendLine()
        appendLine()
        appendLine("---")
        appendLine("Technische Infos")
        appendLine("App-Version: ${snapshot.appVersionName} (${snapshot.appVersionCode})")
        appendLine("Android SDK: ${snapshot.androidSdk}")
        appendLine("Geraet: ${snapshot.device}")
        appendLine("Karten aktiv/gesamt: ${snapshot.activeCards}/${snapshot.totalCards}")
        appendLine("Packs: ${snapshot.packCount}")
        appendLine("Spieleranzahl: ${snapshot.playerCount}")
        appendLine("Modus/Intensitaet: ${snapshot.mode}/${snapshot.intensity}")
        appendLine("Theme: ${snapshot.themeMode}")
        appendLine("Systemfarben: ${yesNo(snapshot.dynamicColors)}")
        appendLine("18+ bestaetigt: ${yesNo(snapshot.ageGateAccepted)}")
        appendLine("Setup abgeschlossen: ${yesNo(snapshot.firstRunSetupCompleted)}")
        appendLine("Generiert: ${snapshot.generatedAt}")
        snapshot.latestCrash?.let { crash ->
            appendLine("Letzter Absturz: ${crash.exceptionClass} um ${crash.timestamp}")
            if (crash.message.isNotBlank()) {
                appendLine("Absturzmeldung: ${crash.message.take(180)}")
            }
        } ?: appendLine("Letzter Absturz: keiner gespeichert")
        appendLine()
        appendLine("Hinweis: Diese Anfrage enthaelt keine Kartentexte und keine Spielernamen.")
        appendLine("Falls noetig, kannst du den Diagnosebericht separat aus den Einstellungen exportieren.")
    }

    private fun yesNo(value: Boolean): String = if (value) "ja" else "nein"
}
