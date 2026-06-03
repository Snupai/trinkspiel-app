package com.snupai.trinkspiel

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.compose.ui.semantics.SemanticsProperties
import com.snupai.trinkspiel.data.AppDatabase
import com.snupai.trinkspiel.data.BuiltInPacks
import com.snupai.trinkspiel.data.DrinkRepository
import com.snupai.trinkspiel.data.GamePreferences
import com.snupai.trinkspiel.viewmodel.DrinkViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TrinkspielAppSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = composeRule.activity.applicationContext
        context
            .getSharedPreferences("trinkspiel_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context
            .getSharedPreferences("seemops_diagnostics", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val vm = DrinkViewModel(
            repo = DrinkRepository(db.drinkEntryDao()),
            preferences = GamePreferences(context),
        )
        composeRule.setContent {
            TrinkspielApp(vm)
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun firstRunSetupBlocksGameplayUntilAgeAndSafetyAreConfirmed() {
        composeRule.onNodeWithText("Bereit machen").assertIsDisplayed()
        composeRule.onNodeWithText("Ohne Karten starten").performScrollTo().assertIsNotEnabled()

        composeRule.onNodeWithContentDescription("18 plus bestätigen").performScrollTo().performClick()
        composeRule.onNodeWithText("Ohne Karten starten").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Sicher spielen bestätigen").performScrollTo().performClick()
        composeRule.onNodeWithText("Mit Classic Starter starten").performScrollTo().assertIsEnabled().performClick()

        composeRule.waitForPlayableDeck()
        composeRule.onNodeWithText("Classic / Locker").assertIsDisplayed()
        composeRule.onNodeWithText("Karte ziehen").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun gameLoadsStarterPackAndDrawsCard() {
        completeFirstRunSetupWithoutCards()

        composeRule.onNodeWithText("Seemops Trinkspiel").assertIsDisplayed()
        composeRule.onNodeWithText("Classic Starter laden").performScrollTo().performClick()

        composeRule.waitForPlayableDeck()
        composeRule.onNodeWithText("Karte ziehen").performScrollTo().performClick()
        composeRule.onNodeWithText("Erledigt, nächster Zug").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Karte überspringen").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Rückblick").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Letzte Karten").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun gameplaySkipCompleteAndUndoWork() {
        completeFirstRunSetupWithoutCards()

        composeRule.onNode(hasSetTextAction()).performScrollTo().performTextInput("Jordan")
        composeRule.onNodeWithText("Hinzufügen").performScrollTo().assertIsEnabled().performClick()
        composeRule.waitForText("Jordan")

        composeRule.onNodeWithText("Classic Starter laden").performScrollTo().performClick()
        composeRule.waitForPlayableDeck()

        composeRule.onNodeWithText("Karte ziehen").performScrollTo().performClick()
        composeRule.waitForText("Erledigt, nächster Zug")
        composeRule.onNodeWithText("Karte überspringen").performScrollTo().performClick()
        composeRule.waitForText("Karte ziehen")
        composeRule.waitForNoText("Erledigt, nächster Zug")
        composeRule.waitForText("Rückblick")

        composeRule.onNodeWithText("Karte ziehen").performScrollTo().performClick()
        composeRule.waitForText("Erledigt, nächster Zug")
        composeRule.onNodeWithText("Erledigt, nächster Zug").performScrollTo().performClick()

        composeRule.waitForText("Letzten Zug zurücknehmen")
        composeRule.waitForText("1 P / 1 Schluck")
        composeRule.onNodeWithText("Letzten Zug zurücknehmen").performScrollTo().performClick()

        composeRule.waitForText("Erledigt, nächster Zug")
        composeRule.waitForText("0 P / 0 Schlucke")
    }

    @Test
    fun changingActiveCardLibraryClearsVisibleCardAndScopesDeck() {
        completeFirstRunSetupWithoutCards()
        updateCardUserSettings(ownerName = "WG Bibliothek", contributorName = "WG Bibliothek")

        composeRule.onNodeWithText("Classic Starter laden").performScrollTo().performClick()
        composeRule.waitForPlayableDeck()
        composeRule.onNodeWithText("Karte ziehen").performScrollTo().performClick()
        composeRule.waitForText("Erledigt, nächster Zug")

        updateCardUserSettings(ownerName = "Andere Bibliothek", contributorName = "WG Bibliothek")

        composeRule.waitForNoText("Erledigt, nächster Zug")
        composeRule
            .onNodeWithText("Noch keine Karten in dieser Bibliothek")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("16 Karten gehören zu einer anderen Bibliothek.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Karte ziehen").performScrollTo().assertIsNotEnabled()

        updateCardUserSettings(ownerName = "Andere Bibliothek", contributorName = "Andere Bibliothek")
        composeRule.onNodeWithText("Classic Starter laden").performScrollTo().performClick()
        composeRule.waitForEnabledText("Karte ziehen")
        composeRule.onNodeWithText("Karte ziehen").performScrollTo().assertIsEnabled()
    }

    @Test
    fun firstRunSetupCanChooseAStarterPack() {
        composeRule.onNodeWithText("Preparty Pack").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("18 plus bestätigen").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Sicher spielen bestätigen").performScrollTo().performClick()
        composeRule.onNodeWithText("Mit Preparty Pack starten").performScrollTo().assertIsEnabled().performClick()

        composeRule.waitForPlayableDeck()
        composeRule.onNodeWithContentDescription("Einträge").performClick()
        composeRule.waitForText("Einträge (16/16)")
    }

    @Test
    fun entryManagerLoadsAllStandardPacks() {
        completeFirstRunSetupWithoutCards()
        val totalBuiltInCards = BuiltInPacks.all.sumOf { it.entries.size }

        composeRule.onNodeWithContentDescription("Einträge").performClick()
        composeRule.onNodeWithText("Einträge (0/0)").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Mehr").performClick()
        composeRule.onNodeWithText("Alle Standardpacks laden").performClick()

        composeRule.waitForText("Einträge ($totalBuiltInCards/$totalBuiltInCards)")
        composeRule.onNode(hasSetTextAction()).performTextInput("Preparty")
        composeRule.waitForText("Einträge (16/$totalBuiltInCards)")
    }

    @Test
    fun entryManagerCreatesPackTemplateDrafts() {
        completeFirstRunSetupWithoutCards()

        composeRule.onNodeWithContentDescription("Einträge").performClick()
        composeRule.waitForText("Einträge (0/0)")
        composeRule
            .onNodeWithTag("entry_empty_pack_templates_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.waitForText("Vorlagen legen pausierte", substring = true)
        composeRule.onNodeWithText("WG Warmup Vorlage").assertIsDisplayed()
        composeRule.onNodeWithTag("pack_template_create_wg_warmup").performScrollTo().performClick()

        composeRule.waitForText("Einträge (8/8)")
        composeRule.waitForText("WG Warmup Vorlage", substring = true)
        composeRule.waitForText("pausiert", substring = true)
    }

    @Test
    fun entryManagerAddsPausesEditsAndDeletesCustomEntry() {
        completeFirstRunSetupWithoutCards()

        composeRule.onNodeWithContentDescription("Einträge").performClick()
        composeRule.onNodeWithContentDescription("Hinzufügen").performClick()

        composeRule.onNodeWithText("Neuer Eintrag").assertIsDisplayed()
        composeRule.onNodeWithTag("entry_text_field").performTextInput("QA Karte")
        composeRule.onNodeWithTag("entry_drinks_field").performTextInput("2")
        composeRule.onNodeWithTag("entry_pack_field").performTextReplacement("QA Pack")
        composeRule.onNodeWithText("Speichern").performClick()

        composeRule.waitForText("Einträge (1/1)")
        composeRule.onNodeWithText("QA Karte").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Karte pausieren: QA Karte").performScrollTo().performClick()
        composeRule.waitForContentDescription("Karte aktivieren: QA Karte")
        composeRule.onNodeWithContentDescription("Karte aktivieren: QA Karte").performScrollTo().performClick()

        composeRule.onNodeWithContentDescription("Karte bearbeiten: QA Karte").performScrollTo().performClick()
        composeRule.onNodeWithText("Eintrag bearbeiten").assertIsDisplayed()
        composeRule.onNodeWithTag("entry_text_field").performTextReplacement("QA Karte bearbeitet")
        composeRule.onNodeWithText("Speichern").performClick()

        composeRule.waitForNoText("Eintrag bearbeiten")
        composeRule.waitForContentDescription("Karte löschen: QA Karte bearbeitet")
        composeRule.onNodeWithContentDescription("Karte löschen: QA Karte bearbeitet").performScrollTo().performClick()
        composeRule.onNodeWithText("Löschen").performClick()

        composeRule.waitForText("Einträge (0/0)")
        composeRule.onNodeWithText("Keine Einträge").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun entryManagerDefaultsExternalContributionsToReview() {
        completeFirstRunSetupWithoutCards()

        composeRule.onNodeWithContentDescription("Einträge").performClick()
        composeRule.onNodeWithContentDescription("Hinzufügen").performClick()

        composeRule.onNodeWithText("Neuer Eintrag").assertIsDisplayed()
        composeRule.onNodeWithTag("entry_text_field").performTextInput("Mika Vorschlag")
        composeRule.onNodeWithTag("entry_drinks_field").performTextInput("4")
        composeRule.onNodeWithTag("entry_pack_field").performTextReplacement("WG Review")
        composeRule.onNodeWithTag("entry_review_switch").performScrollTo().assertIsOff()

        composeRule.onNodeWithTag("entry_owner_field").performScrollTo().performTextReplacement("WG Bibliothek")
        composeRule.onNodeWithTag("entry_contributor_field").performScrollTo().performTextReplacement("Mika")

        composeRule.onNodeWithTag("entry_review_switch").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("entry_active_switch").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Speichern").performClick()

        composeRule.waitForText("Einträge (1/1)")
        composeRule.waitForText("Mika Vorschlag")
        composeRule.waitForText("Review", substring = true)
    }

    @Test
    fun settingsScreenShowsCoreReleaseOptions() {
        completeFirstRunSetupWithoutCards()

        composeRule.onNodeWithContentDescription("Einstellungen").performClick()

        composeRule.onNodeWithText("Einstellungen").assertIsDisplayed()
        composeRule.onNodeWithText("Darstellung").assertIsDisplayed()
        composeRule.onNodeWithText("Spielstandard").assertIsDisplayed()
        composeRule.onNodeWithText("Trink-Wording").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Backup").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Gerätewechsel").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Transferpaket teilen").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Transferpaket importieren").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Backend-Sync").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Token-Rolle").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Rolle: unbekannt", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Backend-Sync speichern").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Backend-Sync prüfen").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Backend-Mitglieder prüfen").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Backend-Invite teilen").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Contributor-Invite").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Invite-Rolle").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Contributor-Invite erstellen").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Diagnose").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Letzten Fehler teilen").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Letzten Fehler teilen").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Letzten Fehler exportieren").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Letzten Fehler exportieren").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Diagnosebericht exportieren").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Support").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Support-Anfrage vorbereiten").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("App & Rechtliches").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Datenschutzrichtlinie anzeigen").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Datenschutzrichtlinie teilen").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Datenschutz & Sicherheit").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Zurücksetzen").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Alters-/Sicherheitshinweis zurücksetzen").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsThemeAndPrivacyPolicyControlsWork() {
        completeFirstRunSetupWithoutCards()

        composeRule.onNodeWithContentDescription("Einstellungen").performClick()

        composeRule.onNodeWithText("Darstellung").assertIsDisplayed()
        composeRule.onNodeWithText("Dunkel").performClick()
        composeRule.waitForText("Einstellungen")
        composeRule.onNodeWithText("Hell").performClick()
        composeRule.waitForText("Einstellungen")
        composeRule.onNodeWithText("System").performClick()
        composeRule.waitForText("Systemfarben")

        composeRule.onNodeWithText("Datenschutzrichtlinie anzeigen").performScrollTo().performClick()
        composeRule.waitForText("Schließen")
        composeRule.onNodeWithText("kein Konto", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Schließen").performClick()
        composeRule.waitForText("App & Rechtliches")
    }

    @Test
    fun settingsLastIssueActionsReflectStoredCrashState() {
        completeFirstRunSetupWithoutCards()
        saveFakeCrash()

        composeRule.onNodeWithContentDescription("Einstellungen").performClick()

        composeRule.onNodeWithText("Letzter Absturz: java.lang.IllegalStateException")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Letzten Fehler teilen").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithText("Letzten Fehler exportieren").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithText("Letzten Fehler löschen").performScrollTo().assertIsEnabled().performClick()

        composeRule.waitForText("Kein Absturz gespeichert.")
        composeRule.onNodeWithText("Letzten Fehler teilen").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Letzten Fehler exportieren").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Letzten Fehler löschen").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun settingsResetActionsClearPlayersAndReturnToFirstRunGate() {
        completeFirstRunSetupWithoutCards()

        composeRule.onNodeWithText("Hinzufügen").performScrollTo().assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performScrollTo().performTextInput("Jordan")
        composeRule.onNodeWithText("Hinzufügen").performScrollTo().assertIsEnabled().performClick()
        composeRule.waitForText("Jordan")

        composeRule.onNodeWithContentDescription("Einstellungen").performClick()
        composeRule.onNodeWithText("Spieler löschen").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Zurück").performClick()

        composeRule.onNodeWithText("Ohne Spieler zieht ihr frei. Mit Spielern rotiert der Zug nach jeder Karte.")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Einstellungen").performClick()
        composeRule.onNodeWithText("Alters-/Sicherheitshinweis zurücksetzen").performScrollTo().performClick()

        composeRule.onNodeWithText("Bereit machen").assertIsDisplayed()
        composeRule.onNodeWithText("Ohne Karten starten").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun settingsResetActionsResetRoundAndScores() {
        completeFirstRunSetupWithoutCards()

        composeRule.onNode(hasSetTextAction()).performScrollTo().performTextInput("Jordan")
        composeRule.onNodeWithText("Hinzufügen").performScrollTo().assertIsEnabled().performClick()
        composeRule.waitForText("Jordan")
        composeRule.onNodeWithText("+Punkt").performScrollTo().performClick()
        composeRule.onNodeWithText("+Schluck").performScrollTo().performClick()
        composeRule.waitForText("1 P / 1 Schluck")

        composeRule.onNodeWithText("Classic Starter laden").performScrollTo().performClick()
        composeRule.waitForPlayableDeck()
        composeRule.onNodeWithText("Karte ziehen").performScrollTo().performClick()
        composeRule.waitForText("Erledigt, nächster Zug")
        composeRule.waitForText("Rückblick")
        composeRule.waitForText("1 Karten gezogen")

        composeRule.onNodeWithContentDescription("Einstellungen").performClick()
        composeRule.onNodeWithText("Runde zurücksetzen").performScrollTo().performClick()
        composeRule.onNodeWithText("Scores zurücksetzen").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Zurück").performClick()

        composeRule.onNodeWithText("Karte ziehen").performScrollTo().assertIsDisplayed()
        composeRule.waitForNoText("Erledigt, nächster Zug")
        composeRule.waitForNoText("Rückblick")
        composeRule.onNodeWithText("0 P / 0 Schlucke").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun playerPanelAddsPlayersAndUpdatesScoreboard() {
        completeFirstRunSetupWithoutCards()
        val playerName = "Alexandra Langname"

        composeRule.onNodeWithText("Hinzufügen").performScrollTo().assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performScrollTo().performTextInput(playerName)
        composeRule.onNodeWithText("Hinzufügen").performScrollTo().assertIsEnabled().performClick()

        composeRule.waitForText(playerName)
        composeRule.onNodeWithText("Scoreboard").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("1. $playerName").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("0 P / 0 Schlucke").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("+Punkt").performScrollTo().performClick()
        composeRule.onNodeWithText("+Schluck").performScrollTo().performClick()
        composeRule.waitForText("1 P / 1 Schluck")
        composeRule.onNodeWithText("1 P / 1 Schluck").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("Team wechseln").performScrollTo().performClick()
        composeRule.waitForText("Team A")
        composeRule.onNodeWithText("Teams").performScrollTo().assertIsDisplayed()
    }

    private fun completeFirstRunSetupWithoutCards() {
        composeRule.onNodeWithContentDescription("18 plus bestätigen").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Sicher spielen bestätigen").performScrollTo().performClick()
        composeRule.onNodeWithText("Ohne Karten starten").performScrollTo().performClick()
        composeRule.waitForText("Classic Starter laden")
    }

    private fun updateCardUserSettings(ownerName: String, contributorName: String) {
        composeRule.onNodeWithContentDescription("Einstellungen").performClick()
        composeRule.onNodeWithText("Karten-User").performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithTag("settings_card_owner_field")
            .performScrollTo()
            .performTextReplacement(ownerName)
        composeRule
            .onNodeWithTag("settings_active_contributor_field")
            .performScrollTo()
            .performTextReplacement(contributorName)
        composeRule.onNodeWithText("Karten-User speichern").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Zurück").performClick()
    }

    private fun saveFakeCrash() {
        composeRule.activity.applicationContext
            .getSharedPreferences("seemops_diagnostics", Context.MODE_PRIVATE)
            .edit()
            .putString("crashTimestamp", "2026-06-01T12:00:00.000Z")
            .putString("crashClass", "java.lang.IllegalStateException")
            .putString("crashMessage", "Connected test crash")
            .putString("crashStack", "stack")
            .commit()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.waitForText(
        text: String,
        substring: Boolean = false,
    ) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.waitForPlayableDeck() {
        waitForText("Karten dieser Bibliothek spielbar", substring = true)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.waitForEnabledText(text: String) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText(text)
                .fetchSemanticsNodes()
                .any { node -> !node.config.contains(SemanticsProperties.Disabled) }
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.waitForContentDescription(
        contentDescription: String,
    ) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithContentDescription(contentDescription)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.waitForNoText(
        text: String,
        substring: Boolean = false,
    ) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }
}
