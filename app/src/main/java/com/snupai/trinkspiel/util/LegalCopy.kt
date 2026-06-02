package com.snupai.trinkspiel.util

object LegalCopy {
    const val PRIVACY_POLICY_TITLE = "Datenschutzrichtlinie"

    val privacyPolicyText: String = """
        Seemops Trinkspiel Datenschutzrichtlinie
        Letzte Aktualisierung: 2026-06-02

        Seemops Trinkspiel ist ein offline spielbares Partyspiel und benoetigt fuer das lokale Spiel kein Konto. Die App zeigt keine Werbung und nutzt keine Analyse- oder Tracking-SDKs. Spieldaten bleiben lokal, ausser du loest selbst Export, Teilen, Import oder den optionalen Backend-Sync aus.

        Lokal gespeicherte Daten
        Die App speichert Karten, importierte Packs, lokale Bibliotheks- und Beitraeger-Profilnamen/-IDs, Spielernamen, Scores, Teams, Spieleinstellungen, Erstsetup-Status, Alters- und Sicherheitshinweise, Rundenstatus und die letzte lokale Absturzzusammenfassung, falls ein Absturz auftritt.

        Backups, Exporte und Importe
        Die App kann JSON-Dateien exportieren, importieren, oeffnen oder teilen, wenn du selbst eine Datei oder ein Android-Teilen-Ziel waehlst. Diese Dateien koennen eigene Karten, lokale Bibliotheks- und Beitraeger-Profilnamen/-IDs, Spielernamen, Scores, Teams und Einstellungen enthalten. Das Transferpaket fuer den Geraetewechsel wird als temporaerer lokaler JSON-Anhang erstellt und nutzt dasselbe Vollbackup-Format. Wenn ein JSON-Backup mit Seemops geoeffnet oder an Seemops geteilt wird, zeigt die App vor dem Uebernehmen eine Vorschau. Du entscheidest, wo exportierte Dateien gespeichert oder geteilt werden.

        Optionaler Backend-Sync
        In den Einstellungen kannst du eine Backend-URL und einen Sync-Token hinterlegen. Wenn du den Backend-Sync manuell startest, sendet und empfaengt die App Karten-Snapshots deiner aktuellen Kartenbibliothek inklusive Kartentext, Stufe, Schluckanzahl, Kategorie, Pack, Review-Status, Besitzer und Beitraeger. Backend-URL und Token werden lokal auf dem Geraet gespeichert und nicht in Backups oder Geraetewechsel-Transferpakete geschrieben. Wenn du eine remote-backed Review-Karte mit Admin-Backend-Token ablehnst, kann die App die ausgewaehlte Remote-Karten-ID an die von dir konfigurierte Backend-URL senden, damit die abgelehnte Karte beim naechsten Sync nicht wiederkommt. Wenn du ein Backend-Invite ausdruecklich teilst, enthaelt dieses Invite die Backend-URL, den Sync-Token, die Bibliotheks-/Beitraeger-Angaben und die Token-Rolle, damit eine andere Person die geteilte Bibliothek verbinden kann. Wenn du ein Contributor-Invite erstellst, Backend-Mitglieder pruefst oder ein generiertes Invite widerrufst, kontaktiert die App die von dir konfigurierte Backend-URL mit dem hinterlegten Token und tauscht nur Invite- oder Mitgliedschafts-Metadaten wie Beitraegername, Beitraeger-ID, Mitgliedschafts-ID, Rolle, Quelle und Erstellzeit aus.

        Diagnose und Support
        Die Einstellungen koennen einen Diagnosebericht erstellen, wenn du ihn selbst teilst oder exportierst, oder eine fokussierte Letzter-Fehler-Datei, wenn ein lokaler Absturz gespeichert ist. Diese Dateien enthalten technische App-Daten wie App-Version, Android-Version, Geraetemodell, Spielzaehler, Einstellungen und den letzten Absturz-Stacktrace, falls vorhanden. Sie enthalten keine Kartentexte und keine Spielernamen.

        Die Support-Anfrage enthaelt nur eine kurze technische Zusammenfassung wie App-Version, Android-Version, Geraetemodell, Spielzaehler, Einstellungen und die letzte Absturzklasse oder -meldung, falls vorhanden. Sie enthaelt keine Kartentexte, keine Spielernamen und keinen Absturz-Stacktrace.

        Datenteilung
        Die App verkauft, vermietet, uebertraegt oder teilt keine personenbezogenen Daten. Wenn du einen Export, ein Backup, ein Transferpaket, ein Backend-Invite, einen Diagnosebericht, eine Letzter-Fehler-Datei, eine Support-Anfrage oder den optionalen Backend-Sync ausloest, passiert das nur durch deine Aktion.

        Sicherheit
        Die App ist nur fuer Erwachsene im gesetzlichen Trinkalter gedacht. Spieler sollten verantwortungsvoll trinken, Wasser trinken, Pausen machen und jederzeit aufhoeren koennen.

        Kontakt
        Fuer Datenschutzfragen nutze den Support-Kontakt auf der App-Store-Seite.
    """.trimIndent()
}
