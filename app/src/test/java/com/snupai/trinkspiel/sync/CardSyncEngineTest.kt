package com.snupai.trinkspiel.sync

import com.snupai.trinkspiel.data.DrinkEntry
import com.snupai.trinkspiel.model.CardSyncStatus
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.model.cardUserIdForName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardSyncEngineTest {

    @Test
    fun planSeparatesRemoteCreatesRemoteUpdatesAndLocalInserts() {
        val ownerId = cardUserIdForName("Lena Library")
        val localOnly = DrinkEntry(
            id = 1,
            text = "Neue lokale Karte",
            drinks = 1,
            ownerUserId = ownerId,
            ownerName = "Lena Library",
            syncStatus = CardSyncStatus.LOCAL.id,
        )
        val dirtyLocal = DrinkEntry(
            id = 2,
            text = "Lokale Bearbeitung",
            drinks = 2,
            ownerUserId = ownerId,
            ownerName = "Lena Library",
            remoteId = "remote-2",
            updatedAtMillis = 20,
            syncStatus = CardSyncStatus.DIRTY.id,
        )
        val olderRemote = RemoteCardSnapshot(
            remoteId = "remote-2",
            text = "Server alt",
            drinks = 2,
            ownerUserId = ownerId,
            ownerName = "Lena Library",
            updatedAtMillis = 10,
        )
        val remoteOnly = RemoteCardSnapshot(
            remoteId = "remote-3",
            text = "Von Mika",
            drinks = 3,
            questionLevel = QuestionLevel.LEVEL_2.id,
            ownerUserId = ownerId,
            ownerName = "Lena Library",
            contributorUserId = cardUserIdForName("Mika"),
            contributorName = "Mika",
            updatedAtMillis = 30,
        )

        val plan = CardSyncEngine.plan(
            localEntries = listOf(localOnly, dirtyLocal),
            remoteCards = listOf(olderRemote, remoteOnly),
            libraryOwnerUserId = ownerId,
        )

        assertEquals(listOf(localOnly.id), plan.createRemote.map { it.id })
        assertEquals(listOf(dirtyLocal.id), plan.updateRemote.map { it.id })
        assertEquals(listOf("remote-3"), plan.insertLocal.map { it.remoteId })
        assertTrue(plan.updateLocal.isEmpty())
        assertTrue(plan.conflicts.isEmpty())
    }

    @Test
    fun identicalDirtyRemoteBackedCardIsMarkedSyncedInsteadOfUploaded() {
        val ownerId = cardUserIdForName("Lena")
        val local = DrinkEntry(
            id = 4,
            text = "Gleiche Karte",
            drinks = 2,
            ownerUserId = ownerId,
            ownerName = "Lena",
            remoteId = "remote-4",
            updatedAtMillis = 50,
            syncStatus = CardSyncStatus.DIRTY.id,
        )
        val remote = RemoteCardSnapshot.fromEntry(
            local.copy(syncStatus = CardSyncStatus.SYNCED.id)
        )!!

        val plan = CardSyncEngine.plan(
            localEntries = listOf(local),
            remoteCards = listOf(remote),
            libraryOwnerUserId = ownerId,
        )

        assertTrue(plan.updateRemote.isEmpty())
        assertEquals(listOf(local.id), plan.markLocalSynced.map { it.id })
        assertEquals(CardSyncStatus.SYNCED.id, plan.markLocalSynced.first().syncStatus)
    }

    @Test
    fun remoteNewerDirtyLocalCardBecomesConflict() {
        val ownerId = cardUserIdForName("Lena")
        val local = DrinkEntry(
            id = 5,
            text = "Lokale Version",
            drinks = 2,
            ownerUserId = ownerId,
            ownerName = "Lena",
            remoteId = "remote-5",
            updatedAtMillis = 10,
            syncStatus = CardSyncStatus.DIRTY.id,
        )
        val remote = RemoteCardSnapshot(
            remoteId = "remote-5",
            text = "Remote Version",
            drinks = 3,
            ownerUserId = ownerId,
            ownerName = "Lena",
            updatedAtMillis = 20,
        )

        val plan = CardSyncEngine.plan(
            localEntries = listOf(local),
            remoteCards = listOf(remote),
            libraryOwnerUserId = ownerId,
        )

        assertTrue(plan.updateRemote.isEmpty())
        assertTrue(plan.updateLocal.isEmpty())
        assertEquals(1, plan.conflicts.size)
        assertEquals(CardSyncConflictReason.LOCAL_AND_REMOTE_CHANGED, plan.conflicts.first().reason)
    }

    @Test
    fun conflictRemoteReplacementKeepsLocalIdAndUsesRemoteContent() {
        val ownerId = cardUserIdForName("Lena")
        val local = DrinkEntry(
            id = 15,
            text = "Lokale Version",
            drinks = 2,
            ownerUserId = ownerId,
            ownerName = "Lena",
            remoteId = "remote-15",
            updatedAtMillis = 10,
            syncStatus = CardSyncStatus.DIRTY.id,
        )
        val remote = RemoteCardSnapshot(
            remoteId = "remote-15",
            text = "Remote Version",
            drinks = 4,
            questionLevel = QuestionLevel.LEVEL_3.id,
            ownerUserId = ownerId,
            ownerName = "Lena",
            contributorUserId = cardUserIdForName("Mika"),
            contributorName = "Mika",
            updatedAtMillis = 20,
        )

        val plan = CardSyncEngine.plan(
            localEntries = listOf(local),
            remoteCards = listOf(remote),
            libraryOwnerUserId = ownerId,
        )
        val replacement = plan.remoteConflictReplacements.single()

        assertEquals(local.id, replacement.id)
        assertEquals("Remote Version", replacement.text)
        assertEquals(4, replacement.drinks)
        assertEquals(QuestionLevel.LEVEL_3.id, replacement.questionLevel)
        assertEquals(cardUserIdForName("Mika"), replacement.contributorUserId)
        assertEquals(CardSyncStatus.SYNCED.id, replacement.syncStatus)
    }

    @Test
    fun conflictRemoteReplacementsCanBeLimitedToSelectedLocalIds() {
        val ownerId = cardUserIdForName("Lena")
        val firstLocal = DrinkEntry(
            id = 16,
            text = "Erste lokale Version",
            drinks = 2,
            ownerUserId = ownerId,
            ownerName = "Lena",
            remoteId = "remote-16",
            updatedAtMillis = 10,
            syncStatus = CardSyncStatus.DIRTY.id,
        )
        val secondLocal = DrinkEntry(
            id = 17,
            text = "Zweite lokale Version",
            drinks = 2,
            ownerUserId = ownerId,
            ownerName = "Lena",
            remoteId = "remote-17",
            updatedAtMillis = 10,
            syncStatus = CardSyncStatus.DIRTY.id,
        )
        val plan = CardSyncEngine.plan(
            localEntries = listOf(firstLocal, secondLocal),
            remoteCards = listOf(
                RemoteCardSnapshot(
                    remoteId = "remote-16",
                    text = "Erste Remote Version",
                    drinks = 3,
                    ownerUserId = ownerId,
                    ownerName = "Lena",
                    updatedAtMillis = 20,
                ),
                RemoteCardSnapshot(
                    remoteId = "remote-17",
                    text = "Zweite Remote Version",
                    drinks = 4,
                    ownerUserId = ownerId,
                    ownerName = "Lena",
                    updatedAtMillis = 20,
                ),
            ),
            libraryOwnerUserId = ownerId,
        )

        val replacements = plan.remoteConflictReplacementsFor(setOf(secondLocal.id))

        assertEquals(1, replacements.size)
        assertEquals(secondLocal.id, replacements.first().id)
        assertEquals("Zweite Remote Version", replacements.first().text)
        assertEquals(4, replacements.first().drinks)
    }

    @Test
    fun remoteNewerCleanLocalCardIsPulledLocally() {
        val ownerId = cardUserIdForName("Lena")
        val local = DrinkEntry(
            id = 6,
            text = "Alt",
            drinks = 1,
            ownerUserId = ownerId,
            ownerName = "Lena",
            remoteId = "remote-6",
            updatedAtMillis = 10,
            syncStatus = CardSyncStatus.SYNCED.id,
        )
        val remote = RemoteCardSnapshot(
            remoteId = "remote-6",
            text = "Neu",
            drinks = 4,
            questionLevel = QuestionLevel.LEVEL_3.id,
            ownerUserId = ownerId,
            ownerName = "Lena",
            updatedAtMillis = 20,
        )

        val plan = CardSyncEngine.plan(
            localEntries = listOf(local),
            remoteCards = listOf(remote),
            libraryOwnerUserId = ownerId,
        )

        assertEquals(1, plan.updateLocal.size)
        assertEquals(local.id, plan.updateLocal.first().id)
        assertEquals("Neu", plan.updateLocal.first().text)
        assertEquals(QuestionLevel.LEVEL_3.id, plan.updateLocal.first().questionLevel)
        assertEquals(CardSyncStatus.SYNCED.id, plan.updateLocal.first().syncStatus)
    }

    @Test
    fun ownerFilterKeepsOtherLibrariesOutOfSyncPlan() {
        val ownerId = cardUserIdForName("Lena")
        val otherOwnerId = cardUserIdForName("Nora")
        val local = DrinkEntry(
            id = 7,
            text = "Andere Sammlung",
            drinks = 1,
            ownerUserId = otherOwnerId,
            ownerName = "Nora",
        )
        val remote = RemoteCardSnapshot(
            remoteId = "remote-other",
            text = "Auch andere Sammlung",
            drinks = 1,
            ownerUserId = otherOwnerId,
            ownerName = "Nora",
        )

        val plan = CardSyncEngine.plan(
            localEntries = listOf(local),
            remoteCards = listOf(remote),
            libraryOwnerUserId = ownerId,
        )

        assertFalse(plan.hasWork)
        assertEquals(1, plan.skippedRemoteCards)
    }
}
