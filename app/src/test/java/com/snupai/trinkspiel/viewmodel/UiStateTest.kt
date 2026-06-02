package com.snupai.trinkspiel.viewmodel

import com.snupai.trinkspiel.data.DrinkEntry
import com.snupai.trinkspiel.data.CardUserEntity
import com.snupai.trinkspiel.model.CardCategory
import com.snupai.trinkspiel.model.CardSyncStatus
import com.snupai.trinkspiel.model.DrinkIntensity
import com.snupai.trinkspiel.model.GameMode
import com.snupai.trinkspiel.model.GameSettings
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.util.AppBackupData
import com.snupai.trinkspiel.util.BackendSyncInvite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStateTest {

    @Test
    fun deckBlockersExplainEachFilteringLayer() {
        val state = UiState(
            entries = listOf(
                DrinkEntry(id = 1, text = "Pausiert", drinks = 1, isEnabled = false),
                DrinkEntry(id = 2, text = "Pack aus", drinks = 1, packName = "Aus"),
                DrinkEntry(id = 3, text = "Regel", drinks = 1, category = CardCategory.RULE.id),
                DrinkEntry(id = 4, text = "Zu stark", drinks = 3, category = CardCategory.CHALLENGE.id),
            ),
            playableEntries = emptyList(),
            mode = GameMode.CHILL,
            intensity = DrinkIntensity.LOW,
            excludedPackNames = setOf("Aus"),
        )

        assertEquals(
            DeckBlockerSummary(
                pausedCards = 1,
                disabledPackCards = 1,
                modeOrCategoryCards = 1,
                intensityCards = 1,
            ),
            state.deckBlockers
        )
        assertEquals(
            listOf(
                "1 Karte ist pausiert.",
                "1 Karte liegt in deaktivierten Packs.",
                "1 Karte passt nicht zu Modus/Kategorien.",
                "1 Karte ist für Locker zu stark.",
            ),
            state.emptyDeckHints()
        )
    }

    @Test
    fun emptyDeckHintsAreHiddenWhenAPlayableCardExists() {
        val playable = DrinkEntry(id = 1, text = "Spielbar", drinks = 1)
        val state = UiState(
            entries = listOf(
                playable,
                DrinkEntry(id = 2, text = "Pausiert", drinks = 1, isEnabled = false),
            ),
            playableEntries = listOf(playable),
        )

        assertTrue(state.emptyDeckHints().isEmpty())
    }

    @Test
    fun pendingReviewCardsAreSeparatedFromPausedCards() {
        val state = UiState(
            entries = listOf(
                DrinkEntry(id = 1, text = "Review", drinks = 1, isEnabled = false, isPendingReview = true),
                DrinkEntry(id = 2, text = "Pausiert", drinks = 1, isEnabled = false),
            ),
            playableEntries = emptyList(),
        )

        assertEquals(1, state.deckBlockers.pendingReviewCards)
        assertEquals(1, state.deckBlockers.pausedCards)
        assertEquals(
            listOf(
                "1 Karte ist pausiert.",
                "1 Karte ist im Review.",
            ),
            state.emptyDeckHints(),
        )
    }

    @Test
    fun otherLibraryCardsAreSeparatedFromActiveLibraryBlockers() {
        val state = UiState(
            entries = listOf(
                DrinkEntry(
                    id = 1,
                    text = "Fremde Bibliothek",
                    drinks = 1,
                    ownerUserId = "library_lena",
                    ownerName = "Lena",
                ),
            ),
            playableEntries = emptyList(),
            cardOwnerUserId = "library_wg",
            cardOwnerName = "WG Bibliothek",
        )

        assertEquals(1, state.deckBlockers.otherLibraryCards)
        assertEquals(0, state.activeLibraryEntryCount)
        assertEquals(
            listOf("1 Karte gehört zu einer anderen Bibliothek."),
            state.emptyDeckHints(),
        )
    }

    @Test
    fun disabledQuestionLevelsAreSeparatedFromIntensityBlockers() {
        val state = UiState(
            entries = listOf(
                DrinkEntry(
                    id = 1,
                    text = "Stufe aus",
                    drinks = 1,
                    questionLevel = QuestionLevel.LEVEL_2.id,
                ),
                DrinkEntry(
                    id = 2,
                    text = "Zu stark",
                    drinks = 4,
                    questionLevel = QuestionLevel.LEVEL_3.id,
                ),
            ),
            playableEntries = emptyList(),
            intensity = DrinkIntensity.LOW,
            enabledQuestionLevelIds = setOf(QuestionLevel.LEVEL_1.id, QuestionLevel.LEVEL_3.id),
        )

        assertEquals(1, state.deckBlockers.questionLevelCards)
        assertEquals(1, state.deckBlockers.intensityCards)
        assertEquals(
            listOf(
                "1 Karte ist durch Stufen deaktiviert.",
                "1 Karte ist für Locker zu stark.",
            ),
            state.emptyDeckHints(),
        )
    }

    @Test
    fun questionLevelCountsKeepLevelsSeparatedForManagement() {
        val state = UiState(
            entries = listOf(
                DrinkEntry(id = 1, text = "Einfach", drinks = 1, questionLevel = QuestionLevel.LEVEL_1.id),
                DrinkEntry(id = 2, text = "Saufen", drinks = 2, questionLevel = QuestionLevel.LEVEL_2.id),
                DrinkEntry(id = 3, text = "Hardcore", drinks = 4, questionLevel = QuestionLevel.LEVEL_3.id),
                DrinkEntry(id = 4, text = "Nochmal", drinks = 1, questionLevel = QuestionLevel.LEVEL_1.id),
            ),
            questionLevelCounts = mapOf(
                QuestionLevel.LEVEL_1.id to 2,
                QuestionLevel.LEVEL_2.id to 1,
                QuestionLevel.LEVEL_3.id to 1,
            ),
        )

        assertEquals(2, state.questionLevelCounts[QuestionLevel.LEVEL_1.id])
        assertEquals(1, state.questionLevelCounts[QuestionLevel.LEVEL_2.id])
        assertEquals(1, state.questionLevelCounts[QuestionLevel.LEVEL_3.id])
    }

    @Test
    fun syncOpenCountTracksLocalAndDirtyCards() {
        val state = UiState(
            syncStatusCounts = mapOf(
                CardSyncStatus.LOCAL.id to 2,
                CardSyncStatus.DIRTY.id to 1,
                CardSyncStatus.SYNCED.id to 4,
            ),
        )

        assertEquals(3, state.syncOpenCount)
    }

    @Test
    fun activeLibraryCountsIgnoreOtherOwnerCards() {
        val settings = GameSettings(
            cardOwnerUserId = "library_wg",
            cardOwnerName = "WG Bibliothek",
        )
        val activeLibraryEntries = entriesForActiveLibrary(
            entries = listOf(
                DrinkEntry(
                    id = 1,
                    text = "WG einfach",
                    drinks = 1,
                    questionLevel = QuestionLevel.LEVEL_1.id,
                    ownerUserId = "library_wg",
                    ownerName = "WG Bibliothek",
                    syncStatus = CardSyncStatus.LOCAL.id,
                ),
                DrinkEntry(
                    id = 2,
                    text = "WG saufen",
                    drinks = 2,
                    questionLevel = QuestionLevel.LEVEL_2.id,
                    ownerUserId = "library_wg",
                    ownerName = "WG Bibliothek",
                    syncStatus = CardSyncStatus.DIRTY.id,
                ),
                DrinkEntry(
                    id = 3,
                    text = "Lena hardcore",
                    drinks = 4,
                    questionLevel = QuestionLevel.LEVEL_3.id,
                    ownerUserId = "library_lena",
                    ownerName = "Lena Bibliothek",
                    syncStatus = CardSyncStatus.SYNCED.id,
                ),
            ),
            settings = settings,
        )

        assertEquals(listOf(1L, 2L), activeLibraryEntries.map { it.id })
        assertEquals(
            mapOf(
                QuestionLevel.LEVEL_1.id to 1,
                QuestionLevel.LEVEL_2.id to 1,
            ),
            questionLevelCountsFor(activeLibraryEntries),
        )
        assertEquals(
            mapOf(
                CardSyncStatus.LOCAL.id to 1,
                CardSyncStatus.DIRTY.id to 1,
            ),
            syncStatusCountsFor(activeLibraryEntries),
        )
    }

    @Test
    fun activeLibraryActionsIgnoreOtherOwnerCards() {
        val settings = GameSettings(
            cardOwnerUserId = "library_wg",
            cardOwnerName = "WG Bibliothek",
        )
        val actionEntries = activeLibraryActionEntries(
            entries = listOf(
                DrinkEntry(
                    id = 1,
                    text = "WG Review",
                    drinks = 1,
                    ownerUserId = "library_wg",
                    ownerName = "WG Bibliothek",
                    isPendingReview = true,
                ),
                DrinkEntry(
                    id = 2,
                    text = "Lena Review",
                    drinks = 1,
                    ownerUserId = "library_lena",
                    ownerName = "Lena Bibliothek",
                    isPendingReview = true,
                ),
            ),
            settings = settings,
        )

        assertEquals(listOf(1L), actionEntries.map { it.id })
    }

    @Test
    fun activeLibraryActionsUseStoredEntriesInsteadOfSpoofedCandidates() {
        val settings = GameSettings(
            cardOwnerUserId = "library_wg",
            cardOwnerName = "WG Bibliothek",
        )
        val existingEntries = listOf(
            DrinkEntry(
                id = 1,
                text = "WG Original",
                drinks = 1,
                ownerUserId = "library_wg",
                ownerName = "WG Bibliothek",
                isPendingReview = true,
            ),
            DrinkEntry(
                id = 2,
                text = "Lena Original",
                drinks = 1,
                ownerUserId = "library_lena",
                ownerName = "Lena Bibliothek",
                isPendingReview = true,
            ),
        )
        val actionEntries = activeLibraryActionEntries(
            entries = listOf(
                existingEntries.first().copy(
                    text = "WG manipuliert",
                    ownerUserId = "library_lena",
                    ownerName = "Lena Bibliothek",
                ),
                existingEntries.last().copy(
                    text = "Lena gespooft",
                    ownerUserId = "library_wg",
                    ownerName = "WG Bibliothek",
                ),
                DrinkEntry(id = 99, text = "Nicht gespeichert", drinks = 1),
            ),
            settings = settings,
            existingEntries = existingEntries,
        )

        assertEquals(listOf(1L), actionEntries.map { it.id })
        assertEquals("WG Original", actionEntries.single().text)
        assertEquals("library_wg", actionEntries.single().ownerUserId)
    }

    @Test
    fun singleActiveLibraryActionUsesStoredEntryInsteadOfSpoofedCandidate() {
        val settings = GameSettings(
            cardOwnerUserId = "library_wg",
            cardOwnerName = "WG Bibliothek",
        )
        val existingEntries = listOf(
            DrinkEntry(
                id = 1,
                text = "WG Original",
                drinks = 1,
                ownerUserId = "library_wg",
                ownerName = "WG Bibliothek",
                isPendingReview = true,
            ),
            DrinkEntry(
                id = 2,
                text = "Lena Original",
                drinks = 1,
                ownerUserId = "library_lena",
                ownerName = "Lena Bibliothek",
                isPendingReview = true,
            ),
        )

        val activeEntry = activeLibraryActionEntry(
            entry = existingEntries.first().copy(
                text = "WG manipuliert",
                ownerUserId = "library_lena",
                ownerName = "Lena Bibliothek",
            ),
            settings = settings,
            existingEntries = existingEntries,
        )
        val foreignEntry = activeLibraryActionEntry(
            entry = existingEntries.last().copy(
                text = "Lena gespooft",
                ownerUserId = "library_wg",
                ownerName = "WG Bibliothek",
            ),
            settings = settings,
            existingEntries = existingEntries,
        )

        assertEquals(existingEntries.first(), activeEntry)
        assertEquals(null, foreignEntry)
    }

    @Test
    fun enabledChangeActionsDoNotApprovePendingReviewCards() {
        val settings = GameSettings(
            cardOwnerUserId = "library_wg",
            cardOwnerName = "WG Bibliothek",
        )
        val pausedEntry = DrinkEntry(
            id = 1,
            text = "Pausiert",
            drinks = 1,
            ownerUserId = "library_wg",
            ownerName = "WG Bibliothek",
            isEnabled = false,
        )
        val reviewEntry = DrinkEntry(
            id = 2,
            text = "Review",
            drinks = 1,
            ownerUserId = "library_wg",
            ownerName = "WG Bibliothek",
            isEnabled = false,
            isPendingReview = true,
        )
        val activeEntry = DrinkEntry(
            id = 3,
            text = "Aktiv",
            drinks = 1,
            ownerUserId = "library_wg",
            ownerName = "WG Bibliothek",
            isEnabled = true,
        )
        val existingEntries = listOf(pausedEntry, reviewEntry, activeEntry)

        val activateEntries = entriesForEnabledChangeAction(
            entries = existingEntries,
            settings = settings,
            existingEntries = existingEntries,
            isEnabled = true,
        )
        val pauseEntries = entriesForEnabledChangeAction(
            entries = existingEntries,
            settings = settings,
            existingEntries = existingEntries,
            isEnabled = false,
        )

        assertEquals(listOf(pausedEntry), activateEntries)
        assertEquals(listOf(activeEntry), pauseEntries)
    }

    @Test
    fun updateGuardAllowsOnlyExistingCardsFromActiveLibrary() {
        val settings = GameSettings(
            cardOwnerUserId = "library_wg",
            cardOwnerName = "WG Bibliothek",
        )
        val existingEntries = listOf(
            DrinkEntry(
                id = 1,
                text = "WG Karte",
                drinks = 1,
                ownerUserId = "library_wg",
                ownerName = "WG Bibliothek",
            ),
            DrinkEntry(
                id = 2,
                text = "Lena Karte",
                drinks = 1,
                ownerUserId = "library_lena",
                ownerName = "Lena Bibliothek",
            ),
        )

        assertTrue(
            entryCanBeUpdatedFromActiveLibrary(
                existingEntries = existingEntries,
                candidate = existingEntries.first().copy(
                    ownerUserId = "library_nora",
                    ownerName = "Nora Bibliothek",
                ),
                settings = settings,
            )
        )
        assertFalse(
            entryCanBeUpdatedFromActiveLibrary(
                existingEntries = existingEntries,
                candidate = existingEntries.last().copy(text = "Fremd geändert"),
                settings = settings,
            )
        )
        assertFalse(
            entryCanBeUpdatedFromActiveLibrary(
                existingEntries = existingEntries,
                candidate = DrinkEntry(id = 99, text = "Nicht vorhanden", drinks = 1),
                settings = settings,
            )
        )
        assertFalse(
            entryCanBeUpdatedFromActiveLibrary(
                existingEntries = existingEntries,
                candidate = DrinkEntry(id = 0, text = "Neue Karte", drinks = 1),
                settings = settings,
            )
        )
    }

    @Test
    fun editProfileUpdateKeepsContributorWhenOnlyCardTextChanges() {
        val settings = GameSettings(
            cardOwnerUserId = "library_wg",
            cardOwnerName = "WG Bibliothek",
            activeContributorUserId = "account_sam",
            activeContributorName = "Sam",
        )
        val storedEntry = DrinkEntry(
            id = 1,
            text = "Alt",
            drinks = 1,
            ownerUserId = "library_wg",
            ownerName = "WG Bibliothek",
            contributorUserId = "account_mika",
            contributorName = "Mika",
        )

        val update = cardProfileUpdateAfterEdit(
            currentSettings = settings,
            storedEntry = storedEntry,
            editedEntry = storedEntry.copy(text = "Neu"),
        )

        assertFalse(update.hasChanges)
        assertEquals("account_sam", update.settings.activeContributorUserId)
        assertEquals("Sam", update.settings.activeContributorName)
    }

    @Test
    fun editProfileUpdateTracksChangedContributor() {
        val settings = GameSettings(
            cardOwnerUserId = "library_wg",
            cardOwnerName = "WG Bibliothek",
            activeContributorUserId = "account_sam",
            activeContributorName = "Sam",
        )
        val storedEntry = DrinkEntry(
            id = 1,
            text = "Karte",
            drinks = 1,
            ownerUserId = "library_wg",
            ownerName = "WG Bibliothek",
            contributorUserId = "account_mika",
            contributorName = "Mika",
        )

        val update = cardProfileUpdateAfterEdit(
            currentSettings = settings,
            storedEntry = storedEntry,
            editedEntry = storedEntry.copy(
                contributorUserId = "account_nora",
                contributorName = "Nora",
            ),
        )

        assertFalse(update.ownerChanged)
        assertTrue(update.contributorChanged)
        assertEquals("account_nora", update.settings.activeContributorUserId)
        assertEquals("Nora", update.settings.activeContributorName)
    }

    @Test
    fun editProfileUpdateSwitchesOwnerAndContributorWhenCardMovesLibrary() {
        val settings = GameSettings(
            cardOwnerUserId = "library_wg",
            cardOwnerName = "WG Bibliothek",
            activeContributorUserId = "account_sam",
            activeContributorName = "Sam",
        )
        val storedEntry = DrinkEntry(
            id = 1,
            text = "Karte",
            drinks = 1,
            ownerUserId = "library_wg",
            ownerName = "WG Bibliothek",
            contributorUserId = "account_mika",
            contributorName = "Mika",
        )

        val update = cardProfileUpdateAfterEdit(
            currentSettings = settings,
            storedEntry = storedEntry,
            editedEntry = storedEntry.copy(
                ownerUserId = "library_lena",
                ownerName = "Lena Bibliothek",
                contributorUserId = "account_lena",
                contributorName = "Lena",
            ),
        )

        assertTrue(update.ownerChanged)
        assertTrue(update.contributorChanged)
        assertEquals("library_lena", update.settings.cardOwnerUserId)
        assertEquals("Lena Bibliothek", update.settings.cardOwnerName)
        assertEquals("account_lena", update.settings.activeContributorUserId)
        assertEquals("Lena", update.settings.activeContributorName)
    }

    @Test
    fun cardImportPreviewSummarizesNewCardsByLevelAndContributor() {
        val sanitizedEntries = listOf(
            DrinkEntry(
                text = "Neu 1",
                drinks = 1,
                packName = "WG Pack",
                questionLevel = QuestionLevel.LEVEL_1.id,
                contributorName = "Mika",
            ),
            DrinkEntry(
                text = "Neu 2",
                drinks = 3,
                packName = "WG Pack",
                questionLevel = QuestionLevel.LEVEL_2.id,
                contributorName = "Mika",
            ),
            DrinkEntry(
                text = "Duplikat",
                drinks = 4,
                packName = "Chaos Pack",
                questionLevel = QuestionLevel.LEVEL_3.id,
                contributorName = "Lena",
            ),
        )
        val uniqueEntries = sanitizedEntries.take(2)

        val preview = CardImportPreview.fromEntries(
            totalCards = sanitizedEntries.size,
            skippedCards = 1,
            sanitizedEntries = sanitizedEntries,
            uniqueEntries = uniqueEntries,
        )

        assertEquals(3, preview.totalCards)
        assertEquals(2, preview.newCards)
        assertEquals(1, preview.skippedCards)
        assertEquals(listOf("Chaos Pack", "WG Pack"), preview.packNames)
        assertEquals(1, preview.questionLevelCounts[QuestionLevel.LEVEL_1.id])
        assertEquals(1, preview.questionLevelCounts[QuestionLevel.LEVEL_2.id])
        assertEquals(null, preview.questionLevelCounts[QuestionLevel.LEVEL_3.id])
        assertEquals(listOf(CardImportUserPreview("Mika", 2)), preview.contributorCounts)
        assertEquals(listOf(CardImportUserPreview("Mika", 2)), preview.externalContributorCounts)
        assertEquals(0, preview.pendingReviewCards)
    }

    @Test
    fun cardImportPreviewDoesNotTreatOwnerCardsAsExternalContributions() {
        val ownerId = "library_wg"
        val sanitizedEntries = listOf(
            DrinkEntry(
                text = "Eigene Karte",
                drinks = 1,
                ownerUserId = ownerId,
                ownerName = "WG Bibliothek",
                contributorUserId = ownerId,
                contributorName = "WG Bibliothek",
            ),
            DrinkEntry(
                text = "Mikas Karte",
                drinks = 2,
                ownerUserId = ownerId,
                ownerName = "WG Bibliothek",
                contributorUserId = "account_mika",
                contributorName = "Mika",
                isPendingReview = true,
            ),
        )

        val preview = CardImportPreview.fromEntries(
            totalCards = sanitizedEntries.size,
            skippedCards = 0,
            sanitizedEntries = sanitizedEntries,
            uniqueEntries = sanitizedEntries,
        )

        assertEquals(
            listOf(CardImportUserPreview("Mika", 1), CardImportUserPreview("WG Bibliothek", 1)),
            preview.contributorCounts,
        )
        assertEquals(listOf(CardImportUserPreview("Mika", 1)), preview.externalContributorCounts)
        assertEquals(1, preview.pendingReviewCards)
    }

    @Test
    fun cardUserSummariesKeepSavedProfilesWithoutCards() {
        val summaries = buildCardUserSummaries(
            entries = emptyList(),
            savedUsers = listOf(CardUserEntity("account_nora", "Nora")),
            settings = GameSettings(
                cardOwnerUserId = "library_wg",
                cardOwnerName = "WG Bibliothek",
                activeContributorUserId = "account_sam",
                activeContributorName = "Sam",
            ),
        )

        assertEquals(
            listOf("account_nora", "account_sam", "library_wg"),
            summaries.map { it.userId },
        )
        assertTrue(summaries.all { it.totalCount == 0 })
    }

    @Test
    fun cardUserSummariesCountOwnersAndContributors() {
        val summaries = buildCardUserSummaries(
            entries = listOf(
                DrinkEntry(
                    id = 1,
                    text = "Von Nora",
                    drinks = 2,
                    ownerUserId = "library_wg",
                    ownerName = "WG Bibliothek",
                    contributorUserId = "account_nora",
                    contributorName = "Nora",
                ),
                DrinkEntry(
                    id = 2,
                    text = "Vom Owner",
                    drinks = 1,
                    ownerUserId = "library_wg",
                    ownerName = "WG Bibliothek",
                    contributorUserId = "library_wg",
                    contributorName = "WG Bibliothek",
                ),
            ),
            savedUsers = listOf(CardUserEntity("account_nora", "Nora Remote")),
            settings = GameSettings(
                cardOwnerUserId = "library_wg",
                cardOwnerName = "WG Bibliothek",
                activeContributorUserId = "account_nora",
                activeContributorName = "Nora",
            ),
        )

        val library = summaries.first { it.userId == "library_wg" }
        val nora = summaries.first { it.userId == "account_nora" }

        assertEquals("WG Bibliothek", library.displayName)
        assertEquals(2, library.ownedCount)
        assertEquals(1, library.contributedCount)
        assertEquals("Nora", nora.displayName)
        assertEquals(0, nora.ownedCount)
        assertEquals(1, nora.contributedCount)
    }

    @Test
    fun backendInviteCardUsersPreserveRemoteAccountIds() {
        val users = cardUserEntitiesForBackendInvite(
            BackendSyncInvite(
                endpointUrl = "https://example.com/api",
                accessToken = "invite_123",
                libraryOwnerUserId = "library_wg",
                libraryOwnerName = "WG Bibliothek",
                contributorUserId = "account_nora",
                contributorName = "Nora",
                role = "write",
            )
        )

        assertEquals(
            listOf(
                CardUserEntity("library_wg", "WG Bibliothek"),
                CardUserEntity("account_nora", "Nora"),
            ),
            users,
        )
    }

    @Test
    fun backendInviteCardUsersCollapseSameOwnerAndContributor() {
        val users = cardUserEntitiesForBackendInvite(
            BackendSyncInvite(
                endpointUrl = "https://example.com/api",
                accessToken = "admin",
                libraryOwnerUserId = "library_wg",
                libraryOwnerName = "WG Bibliothek",
                contributorUserId = "library_wg",
                contributorName = "WG Bibliothek",
                role = "admin",
            )
        )

        assertEquals(listOf(CardUserEntity("library_wg", "WG Bibliothek")), users)
    }

    @Test
    fun backupRestoreCardUsersIncludeBackupSettingsAndCardMetadata() {
        val users = cardUserEntitiesForBackupRestore(
            AppBackupData(
                cards = listOf(
                    DrinkEntry(
                        text = "Von Sam",
                        drinks = 1,
                        ownerUserId = "library_wg",
                        ownerName = "WG Bibliothek",
                        contributorUserId = "account_sam",
                        contributorName = "Sam",
                    )
                ),
                settings = GameSettings(
                    cardOwnerUserId = "library_wg",
                    cardOwnerName = "WG Bibliothek",
                    activeContributorUserId = "account_nora",
                    activeContributorName = "Nora",
                ),
                cardUsers = listOf(CardUserEntity("account_mika", "Mika")),
            )
        )

        assertEquals(
            listOf("account_mika", "library_wg", "account_nora", "account_sam"),
            users.map { it.id },
        )
    }
}
