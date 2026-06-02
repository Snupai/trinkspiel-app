package com.snupai.trinkspiel.viewmodel

import com.snupai.trinkspiel.data.DrinkEntry
import com.snupai.trinkspiel.model.CardSyncStatus
import com.snupai.trinkspiel.model.GameSettings
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.model.cardUserIdForName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardImportPlannerTest {

    @Test
    fun remoteImportUpdatesExistingSyncedCardWhenIncomingCardIsNewer() {
        val existing = DrinkEntry(
            id = 7,
            text = "Alte Aufgabe",
            drinks = 1,
            questionLevel = QuestionLevel.LEVEL_1.id,
            remoteId = "remote-card-1",
            updatedAtMillis = 10,
            syncStatus = CardSyncStatus.SYNCED.id,
        )
        val incoming = existing.copy(
            id = 0,
            text = "Neue Aufgabe",
            drinks = 3,
            questionLevel = QuestionLevel.LEVEL_2.id,
            updatedAtMillis = 20,
        )

        val plan = prepareCardImport(
            entries = listOf(incoming),
            existingEntries = listOf(existing),
            settings = GameSettings(),
            preserveUserMetadata = true,
        )

        assertEquals(0, plan.uniqueEntries.size)
        assertEquals(1, plan.updatedEntries.size)
        assertEquals(0, plan.skippedCards)
        assertEquals(7, plan.updatedEntries.first().id)
        assertEquals("Neue Aufgabe", plan.updatedEntries.first().text)
        assertEquals(QuestionLevel.LEVEL_2.id, plan.updatedEntries.first().questionLevel)
    }

    @Test
    fun remoteImportDoesNotOverwriteDirtyLocalCard() {
        val existing = DrinkEntry(
            id = 9,
            text = "Lokale Änderung",
            drinks = 2,
            remoteId = "remote-card-2",
            updatedAtMillis = 10,
            syncStatus = CardSyncStatus.DIRTY.id,
        )
        val incoming = existing.copy(
            id = 0,
            text = "Server Änderung",
            updatedAtMillis = 99,
            syncStatus = CardSyncStatus.SYNCED.id,
        )

        val plan = prepareCardImport(
            entries = listOf(incoming),
            existingEntries = listOf(existing),
            settings = GameSettings(),
            preserveUserMetadata = true,
        )

        assertTrue(plan.uniqueEntries.isEmpty())
        assertTrue(plan.updatedEntries.isEmpty())
        assertEquals(1, plan.skippedCards)
    }

    @Test
    fun normalImportUsesLocalOwnerAndKeepsContributor() {
        val incoming = DrinkEntry(
            text = "Beitrag",
            drinks = 1,
            ownerName = "Lena",
            contributorName = "Mika",
            remoteId = "foreign-remote-card",
            updatedAtMillis = 42,
            syncStatus = CardSyncStatus.SYNCED.id,
        )

        val plan = prepareCardImport(
            entries = listOf(incoming),
            existingEntries = emptyList(),
            settings = GameSettings(
                cardOwnerUserId = cardUserIdForName("Meine Sammlung"),
                cardOwnerName = "Meine Sammlung",
                activeContributorUserId = cardUserIdForName("Ich"),
                activeContributorName = "Ich",
            ),
            preserveUserMetadata = false,
        )

        val entry = plan.uniqueEntries.first()
        assertEquals(cardUserIdForName("Meine Sammlung"), entry.ownerUserId)
        assertEquals("Meine Sammlung", entry.ownerName)
        assertEquals(cardUserIdForName("Mika"), entry.contributorUserId)
        assertEquals("Mika", entry.contributorName)
        assertEquals("", entry.remoteId)
        assertEquals(0L, entry.updatedAtMillis)
        assertEquals(CardSyncStatus.LOCAL.id, entry.syncStatus)
        assertTrue(entry.isPendingReview)
        assertFalse(entry.isEnabled)
    }

    @Test
    fun normalImportFromActiveContributorStaysPlayable() {
        val incoming = DrinkEntry(
            text = "Eigene Karte",
            drinks = 1,
        )
        val ownerId = cardUserIdForName("WG Bibliothek")

        val plan = prepareCardImport(
            entries = listOf(incoming),
            existingEntries = emptyList(),
            settings = GameSettings(
                cardOwnerUserId = ownerId,
                cardOwnerName = "WG Bibliothek",
                activeContributorUserId = ownerId,
                activeContributorName = "WG Bibliothek",
            ),
            preserveUserMetadata = false,
        )

        val entry = plan.uniqueEntries.first()
        assertEquals(ownerId, entry.ownerUserId)
        assertEquals("WG Bibliothek", entry.ownerName)
        assertEquals(ownerId, entry.contributorUserId)
        assertEquals("WG Bibliothek", entry.contributorName)
        assertFalse(entry.isPendingReview)
        assertTrue(entry.isEnabled)
    }

    @Test
    fun duplicateTextInDifferentLibraryCanBeImported() {
        val existing = DrinkEntry(
            id = 4,
            text = "Gleiche Karte",
            drinks = 1,
            ownerUserId = cardUserIdForName("Lena Bibliothek"),
            ownerName = "Lena Bibliothek",
        )
        val incoming = DrinkEntry(
            text = "Gleiche Karte",
            drinks = 1,
        )

        val plan = prepareCardImport(
            entries = listOf(incoming),
            existingEntries = listOf(existing),
            settings = GameSettings(
                cardOwnerUserId = cardUserIdForName("WG Bibliothek"),
                cardOwnerName = "WG Bibliothek",
            ),
            preserveUserMetadata = false,
        )

        assertEquals(1, plan.uniqueEntries.size)
        assertEquals(0, plan.skippedCards)
        assertEquals(cardUserIdForName("WG Bibliothek"), plan.uniqueEntries.first().ownerUserId)
    }

    @Test
    fun duplicateTextInSameLibraryIsSkipped() {
        val ownerId = cardUserIdForName("WG Bibliothek")
        val existing = DrinkEntry(
            id = 5,
            text = "Gleiche Karte",
            drinks = 1,
            ownerUserId = ownerId,
            ownerName = "WG Bibliothek",
        )
        val incoming = DrinkEntry(
            text = "Gleiche Karte",
            drinks = 2,
        )

        val plan = prepareCardImport(
            entries = listOf(incoming),
            existingEntries = listOf(existing),
            settings = GameSettings(
                cardOwnerUserId = ownerId,
                cardOwnerName = "WG Bibliothek",
            ),
            preserveUserMetadata = false,
        )

        assertTrue(plan.uniqueEntries.isEmpty())
        assertEquals(1, plan.skippedCards)
    }

    @Test
    fun sameRemoteIdInDifferentLibraryDoesNotOverwriteLocalCard() {
        val existing = DrinkEntry(
            id = 12,
            text = "Lena Remote",
            drinks = 1,
            ownerUserId = cardUserIdForName("Lena Bibliothek"),
            ownerName = "Lena Bibliothek",
            remoteId = "remote-card-shared",
            updatedAtMillis = 10,
            syncStatus = CardSyncStatus.SYNCED.id,
        )
        val incoming = DrinkEntry(
            text = "WG Remote",
            drinks = 1,
            ownerUserId = cardUserIdForName("WG Bibliothek"),
            ownerName = "WG Bibliothek",
            remoteId = "remote-card-shared",
            updatedAtMillis = 20,
            syncStatus = CardSyncStatus.SYNCED.id,
        )

        val plan = prepareCardImport(
            entries = listOf(incoming),
            existingEntries = listOf(existing),
            settings = GameSettings(),
            preserveUserMetadata = true,
        )

        assertEquals(1, plan.uniqueEntries.size)
        assertTrue(plan.updatedEntries.isEmpty())
        assertEquals(0, plan.skippedCards)
    }

    @Test
    fun importPreviewCountsUpdatedCardsByLevelAndContributor() {
        val updated = DrinkEntry(
            text = "Update",
            drinks = 4,
            questionLevel = QuestionLevel.LEVEL_3.id,
            contributorName = "Mika",
        )

        val preview = CardImportPreview.fromEntries(
            totalCards = 1,
            skippedCards = 0,
            sanitizedEntries = listOf(updated),
            uniqueEntries = emptyList(),
            updatedEntries = listOf(updated),
        )

        assertEquals(0, preview.newCards)
        assertEquals(1, preview.updatedCards)
        assertTrue(preview.hasCardChanges)
        assertEquals(1, preview.questionLevelCounts[QuestionLevel.LEVEL_3.id])
        assertEquals(listOf(CardImportUserPreview("Mika", 1)), preview.contributorCounts)
        assertEquals(listOf(CardImportUserPreview("Mika", 1)), preview.externalContributorCounts)
    }
}
