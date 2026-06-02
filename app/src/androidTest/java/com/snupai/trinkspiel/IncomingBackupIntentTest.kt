package com.snupai.trinkspiel

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.snupai.trinkspiel.data.DrinkEntry
import com.snupai.trinkspiel.model.CardCategory
import com.snupai.trinkspiel.model.DrinkIntensity
import com.snupai.trinkspiel.model.GameMode
import com.snupai.trinkspiel.model.GameSettings
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.util.AppBackup
import com.snupai.trinkspiel.util.BackendSyncInvitePackage
import com.snupai.trinkspiel.util.CardSyncPackage
import com.snupai.trinkspiel.util.TransferPackageShare
import com.snupai.trinkspiel.sync.RemoteSyncConfig
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class IncomingBackupIntentTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetSharedStateBeforeTest() {
        resetSharedState()
    }

    @After
    fun resetSharedStateAfterTest() {
        resetSharedState()
    }

    @Test
    fun sharedBackupJsonShowsImportPreview() {
        val context = composeRule.activity
        val json = """
            {
              "type": "seemops.backup",
              "version": 1,
              "cards": [
                {
                  "text": "Direktimport Karte ${System.nanoTime()}",
                  "drinks": 1,
                  "category": "${CardCategory.CHALLENGE.id}",
                  "packName": "Direktimport Test",
                  "enabled": true
                }
              ],
              "settings": null
            }
        """.trimIndent()
        val packageFile = TransferPackageShare.createFile(context, json)
        val intent = TransferPackageShare.createIntent(context, packageFile).apply {
            setClass(context, MainActivity::class.java)
        }

        composeRule.activity.runOnUiThread {
            composeRule.activity.handleIncomingBackupIntentForTest(intent)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Transferpaket prüfen")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("Transferpaket prüfen").assertExists()
        composeRule.onNodeWithText("Karten in Datei: 1").assertExists()
        composeRule.onNodeWithText("Neue Karten: 1").assertExists()
        composeRule.onNodeWithText("Import übernehmen").assertExists()
    }

    @Test
    fun sharedBackupJsonCanBeConfirmedAndImported() {
        val context = composeRule.activity
        val importedCardText = "Transfer Import ${System.nanoTime()}"
        val json = AppBackup.toJson(
            entries = listOf(
                DrinkEntry(
                    text = importedCardText,
                    drinks = 1,
                    category = CardCategory.CHALLENGE.id,
                    packName = "Transfer Import Test",
                    isEnabled = true,
                )
            ),
            settings = GameSettings(
                mode = GameMode.CLASSIC,
                intensity = DrinkIntensity.LOW,
                ageGateAccepted = true,
                safetyNoticeAccepted = true,
                firstRunSetupCompleted = true,
            ),
        )
        val packageFile = TransferPackageShare.createFile(context, json)
        val intent = TransferPackageShare.createIntent(context, packageFile).apply {
            setClass(context, MainActivity::class.java)
        }

        composeRule.activity.runOnUiThread {
            composeRule.activity.handleIncomingBackupIntentForTest(intent)
        }

        composeRule.waitForText("Transferpaket prüfen")
        composeRule.onNodeWithText("Einstellungen: werden übernommen").assertExists()
        composeRule.onNodeWithText("Intensität: Locker").assertExists()
        composeRule.onNodeWithText("Import übernehmen").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Transferpaket prüfen")
                .fetchSemanticsNodes()
                .isEmpty()
        }
        composeRule.waitForText("Karte ziehen")
        composeRule.onNodeWithContentDescription("Einträge").performClick()
        composeRule.waitForText(importedCardText)
        composeRule.onNodeWithText(importedCardText).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun sharedCardSyncJsonShowsSyncPreview() {
        val context = composeRule.activity
        val json = cardSyncJson(
            cardText = "Direkt Sync Preview ${System.nanoTime()}",
            remoteId = "instrumented-preview-${System.nanoTime()}",
        )
        val packageFile = TransferPackageShare.createFile(
            context = context,
            json = json,
            fileName = CardSyncPackage.fileName(),
            shareFolder = "card_sync",
        )
        val intent = TransferPackageShare.createIntent(context, packageFile).apply {
            setClass(context, MainActivity::class.java)
        }

        composeRule.activity.runOnUiThread {
            composeRule.activity.handleIncomingBackupIntentForTest(intent)
        }

        composeRule.waitForText("Karten-Sync-Paket prüfen")
        composeRule.onNodeWithText("Neue Karten lokal: 1").assertExists()
        composeRule.onNodeWithText("Remote-Updates lokal: 0").assertExists()
        composeRule.onNodeWithText("Konflikte: 0").assertExists()
        composeRule.onNodeWithText("Lokale Änderungen übernehmen").assertExists()
    }

    @Test
    fun sharedCardSyncJsonCanBeConfirmedAndRecognizedAsSynced() {
        val context = composeRule.activity
        val remoteId = "instrumented-import-${System.nanoTime()}"
        val json = cardSyncJson(
            cardText = "Direkt Sync Import ${System.nanoTime()}",
            remoteId = remoteId,
        )
        val packageFile = TransferPackageShare.createFile(
            context = context,
            json = json,
            fileName = CardSyncPackage.fileName(),
            shareFolder = "card_sync",
        )
        val intent = TransferPackageShare.createIntent(context, packageFile).apply {
            setClass(context, MainActivity::class.java)
        }

        composeRule.activity.runOnUiThread {
            composeRule.activity.handleIncomingBackupIntentForTest(intent)
        }

        composeRule.waitForText("Karten-Sync-Paket prüfen")
        composeRule.onNodeWithText("Neue Karten lokal: 1").assertExists()
        composeRule.onNodeWithText("Lokale Änderungen übernehmen").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Karten-Sync-Paket prüfen")
                .fetchSemanticsNodes()
                .isEmpty()
        }

        composeRule.activity.runOnUiThread {
            composeRule.activity.handleIncomingBackupIntentForTest(intent)
        }

        composeRule.waitForText("Karten-Sync-Paket prüfen")
        composeRule.onNodeWithText("Neue Karten lokal: 0").assertExists()
        composeRule.onNodeWithText("Remote-Updates lokal: 0").assertExists()
        composeRule.onNodeWithText("Als synchron markieren: 0").assertExists()
        composeRule
            .onNodeWithText("In dieser Datei wurden keine lokal übernehmbaren Karten gefunden.")
            .assertExists()
        composeRule.onNodeWithText("Lokale Änderungen übernehmen").assertIsNotEnabled()
    }

    @Test
    fun sharedBackendInviteJsonShowsInvitePreviewAndCanBeConfirmed() {
        val context = composeRule.activity
        val json = BackendSyncInvitePackage.toJson(
            config = RemoteSyncConfig(
                endpointUrl = "https://sync.example.test/api",
                accessToken = "writer-token",
            ),
            libraryOwnerUserId = "account_wg_library",
            libraryOwnerName = "WG Bibliothek",
            contributorUserId = "account_mika",
            contributorName = "Mika",
            role = "write",
        )
        val packageFile = TransferPackageShare.createFile(
            context = context,
            json = json,
            fileName = BackendSyncInvitePackage.fileName(),
            shareFolder = "backend_invite",
        )
        val intent = TransferPackageShare.createIntent(context, packageFile).apply {
            setClass(context, MainActivity::class.java)
        }

        composeRule.activity.runOnUiThread {
            composeRule.activity.handleIncomingBackupIntentForTest(intent)
        }

        composeRule.waitForText("Backend-Invite prüfen")
        composeRule.onNodeWithText("Bibliothek: WG Bibliothek", substring = true).assertExists()
        composeRule.onNodeWithText("Backend: https://sync.example.test/api", substring = true).assertExists()
        composeRule.onNodeWithText("Rolle: write", substring = true).assertExists()
        composeRule.onNodeWithText("Invite übernehmen").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Backend-Invite prüfen")
                .fetchSemanticsNodes()
                .isEmpty()
        }

        composeRule.onNodeWithContentDescription("Einstellungen").performClick()
        composeRule.onNodeWithText("Rolle: write", substring = true).performScrollTo().assertExists()
    }

    private fun cardSyncJson(
        cardText: String,
        remoteId: String,
    ): String =
        CardSyncPackage.toJson(
            entries = listOf(
                DrinkEntry(
                    text = cardText,
                    drinks = 4,
                    category = CardCategory.SPICY.id,
                    packName = "Direkt Sync Test",
                    questionLevel = QuestionLevel.LEVEL_3.id,
                    contributorName = "QA Contributor",
                    remoteId = remoteId,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            ),
        )

    private fun ComposeContentTestRule.waitForText(text: String) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText(text)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun resetSharedState() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.resetSharedStateForTest()
        }
        composeRule.waitForIdle()
    }
}
