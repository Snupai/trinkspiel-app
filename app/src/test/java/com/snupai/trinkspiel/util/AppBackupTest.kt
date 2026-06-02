package com.snupai.trinkspiel.util

import com.snupai.trinkspiel.data.CardUserEntity
import com.snupai.trinkspiel.data.DrinkEntry
import com.snupai.trinkspiel.model.CardCategory
import com.snupai.trinkspiel.model.DrinkIntensity
import com.snupai.trinkspiel.model.GameMode
import com.snupai.trinkspiel.model.GameSettings
import com.snupai.trinkspiel.model.PlayerStats
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.model.TeamOption
import com.snupai.trinkspiel.model.ThemeMode
import com.snupai.trinkspiel.model.cardUserIdForName
import com.snupai.trinkspiel.ui.dialog.backupRestoreMessage
import com.snupai.trinkspiel.viewmodel.BackupRestoreSummary
import com.snupai.trinkspiel.viewmodel.ImportSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBackupTest {

    @Test
    fun roundTripKeepsCardsAndSettings() {
        val entries = listOf(
            DrinkEntry(
                text = "Backup Karte",
                drinks = 3,
                category = CardCategory.RULE.id,
                packName = "Backup Pack",
                isEnabled = false,
                isPendingReview = true,
                questionLevel = QuestionLevel.LEVEL_2.id,
                ownerUserId = cardUserIdForName("Lena"),
                ownerName = "Lena",
                contributorUserId = cardUserIdForName("Mika"),
                contributorName = "Mika",
            )
        )
        val settings = GameSettings(
            players = listOf("Lena", "Mika"),
            playerStats = mapOf(
                "Lena" to PlayerStats("Lena", TeamOption.TEAM_A, points = 4, drinks = 8),
                "Mika" to PlayerStats("Mika", TeamOption.TEAM_B, points = 2, drinks = 5),
            ),
            currentPlayerIndex = 1,
            mode = GameMode.CUSTOM,
            intensity = DrinkIntensity.HIGH,
            ageGateAccepted = true,
            safetyNoticeAccepted = true,
            firstRunSetupCompleted = true,
            themeMode = ThemeMode.DARK,
            dynamicColors = true,
            drinkSingular = "Sip",
            drinkPlural = "Sips",
            cardOwnerUserId = cardUserIdForName("Lena Library"),
            cardOwnerName = "Lena Library",
            activeContributorUserId = cardUserIdForName("Mika"),
            activeContributorName = "Mika",
            excludedPackNames = setOf("Backup Pack"),
            excludedPackNamesByOwner = mapOf(
                cardUserIdForName("Lena Library") to setOf("Backup Pack"),
                cardUserIdForName("WG Bibliothek") to setOf("WG Pack"),
            ),
            customCategoryIds = setOf(CardCategory.RULE.id, CardCategory.TRUTH.id),
            enabledQuestionLevelIds = setOf(QuestionLevel.LEVEL_1.id, QuestionLevel.LEVEL_3.id),
        )

        val parsed = AppBackup.fromJson(
            AppBackup.toJson(
                entries = entries,
                settings = settings,
                cardUsers = listOf(
                    CardUserEntity("library_lena", "Lena Library"),
                    CardUserEntity("account_mika", "Mika Remote"),
                ),
            )
        )
        val restoredSettings = assertNotNull(parsed.settings).let { parsed.settings!! }

        assertEquals(1, parsed.cards.size)
        assertEquals(
            listOf(
                CardUserEntity("library_lena", "Lena Library"),
                CardUserEntity("account_mika", "Mika Remote"),
            ),
            parsed.cardUsers,
        )
        assertEquals("Backup Karte", parsed.cards.first().text)
        assertEquals(3, parsed.cards.first().drinks)
        assertEquals(CardCategory.RULE.id, parsed.cards.first().category)
        assertEquals("Backup Pack", parsed.cards.first().packName)
        assertFalse(parsed.cards.first().isEnabled)
        assertTrue(parsed.cards.first().isPendingReview)
        assertEquals(QuestionLevel.LEVEL_2.id, parsed.cards.first().questionLevel)
        assertEquals(cardUserIdForName("Lena"), parsed.cards.first().ownerUserId)
        assertEquals("Lena", parsed.cards.first().ownerName)
        assertEquals(cardUserIdForName("Mika"), parsed.cards.first().contributorUserId)
        assertEquals("Mika", parsed.cards.first().contributorName)
        assertEquals(listOf("Lena", "Mika"), restoredSettings.players)
        assertEquals(1, restoredSettings.currentPlayerIndex)
        assertEquals(GameMode.CUSTOM, restoredSettings.mode)
        assertEquals(DrinkIntensity.HIGH, restoredSettings.intensity)
        assertTrue(restoredSettings.ageGateAccepted)
        assertTrue(restoredSettings.safetyNoticeAccepted)
        assertTrue(restoredSettings.firstRunSetupCompleted)
        assertEquals(ThemeMode.DARK, restoredSettings.themeMode)
        assertTrue(restoredSettings.dynamicColors)
        assertEquals("Sip", restoredSettings.drinkSingular)
        assertEquals("Sips", restoredSettings.drinkPlural)
        assertEquals(cardUserIdForName("Lena Library"), restoredSettings.cardOwnerUserId)
        assertEquals("Lena Library", restoredSettings.cardOwnerName)
        assertEquals(cardUserIdForName("Mika"), restoredSettings.activeContributorUserId)
        assertEquals("Mika", restoredSettings.activeContributorName)
        assertEquals(setOf("Backup Pack"), restoredSettings.excludedPackNames)
        assertEquals(
            setOf("WG Pack"),
            restoredSettings.excludedPackNamesForOwner(
                cardUserIdForName("WG Bibliothek"),
                "WG Bibliothek",
            ),
        )
        assertEquals(setOf(CardCategory.RULE.id, CardCategory.TRUTH.id), restoredSettings.customCategoryIds)
        assertEquals(
            setOf(QuestionLevel.LEVEL_1.id, QuestionLevel.LEVEL_3.id),
            restoredSettings.enabledQuestionLevelIds,
        )
        assertEquals(TeamOption.TEAM_A, restoredSettings.scoreboard[0].team)
        assertEquals(4, restoredSettings.scoreboard[0].points)
        assertEquals(8, restoredSettings.scoreboard[0].drinks)
        assertEquals(TeamOption.TEAM_B, restoredSettings.scoreboard[1].team)
    }

    @Test
    fun restoreAcceptsCardOnlyExportsWithoutSettings() {
        val parsed = AppBackup.fromJson(
            ImportExport.toJson(
                entries = listOf(DrinkEntry(text = "Nur Karte", drinks = 1, packName = "Pack")),
                packName = "Pack",
            )
        )

        assertEquals(1, parsed.cards.size)
        assertEquals("Nur Karte", parsed.cards.first().text)
        assertNull(parsed.settings)
        assertTrue(parsed.cardUsers.isEmpty())
    }

    @Test
    fun cardUsersWithoutCardsRoundTrip() {
        val parsed = AppBackup.fromJson(
            AppBackup.toJson(
                entries = emptyList(),
                settings = GameSettings(
                    cardOwnerUserId = "library_wg",
                    cardOwnerName = "WG Bibliothek",
                    activeContributorUserId = "account_nora",
                    activeContributorName = "Nora",
                ),
                cardUsers = listOf(
                    CardUserEntity("account_sam", "Sam"),
                    CardUserEntity("account_nora", "Nora"),
                ),
            )
        )

        assertTrue(parsed.cards.isEmpty())
        assertEquals(
            listOf(
                CardUserEntity("account_nora", "Nora"),
                CardUserEntity("account_sam", "Sam"),
            ),
            parsed.cardUsers,
        )
        assertEquals("library_wg", parsed.settings?.cardOwnerUserId)
        assertEquals("account_nora", parsed.settings?.activeContributorUserId)
    }

    @Test
    fun restoreMessageMentionsPausedReviewImports() {
        val summary = BackupRestoreSummary(
            cards = ImportSummary(added = 2, skippedDuplicates = 1),
            settingsRestored = true,
        )

        assertEquals(
            "2 Karten importiert, 1 Duplikate übersprungen, neue Karten pausiert, Einstellungen übernommen",
            summary.backupRestoreMessage(cardsPaused = true),
        )
    }

    @Test
    fun restoreMessageMentionsCardUsers() {
        val summary = BackupRestoreSummary(
            cards = ImportSummary(added = 0, skippedDuplicates = 0),
            settingsRestored = false,
            cardUsersRestored = 2,
        )

        assertEquals(
            "0 Karten importiert, 2 Karten-User übernommen",
            summary.backupRestoreMessage(),
        )
    }
}
