package com.snupai.trinkspiel.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.snupai.trinkspiel.model.CardSyncStatus
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.model.cardUserIdForName
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DrinkRepositoryInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: DrinkRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = DrinkRepository(database.drinkEntryDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deleteByPackNameForOwnerKeepsSamePackInOtherLibraries() = runBlocking {
        val wgOwnerId = cardUserIdForName("WG Bibliothek")
        val lenaOwnerId = cardUserIdForName("Lena Bibliothek")
        val sharedPackName = "Classic Starter"

        repository.addAll(
            listOf(
                testEntry(
                    id = 1,
                    text = "WG einfach",
                    drinks = 1,
                    level = QuestionLevel.LEVEL_1,
                    packName = sharedPackName,
                    ownerUserId = wgOwnerId,
                    ownerName = "WG Bibliothek",
                ),
                testEntry(
                    id = 2,
                    text = "WG saufen",
                    drinks = 2,
                    level = QuestionLevel.LEVEL_2,
                    packName = sharedPackName,
                    ownerUserId = wgOwnerId,
                    ownerName = "WG Bibliothek",
                ),
                testEntry(
                    id = 3,
                    text = "WG hardcore",
                    drinks = 5,
                    level = QuestionLevel.LEVEL_3,
                    packName = sharedPackName,
                    ownerUserId = wgOwnerId,
                    ownerName = "WG Bibliothek",
                ),
                testEntry(
                    id = 4,
                    text = "Lena gleicher Pack",
                    drinks = 2,
                    level = QuestionLevel.LEVEL_2,
                    packName = sharedPackName,
                    ownerUserId = lenaOwnerId,
                    ownerName = "Lena Bibliothek",
                ),
                testEntry(
                    id = 5,
                    text = "WG anderer Pack",
                    drinks = 5,
                    level = QuestionLevel.LEVEL_3,
                    packName = "WG Spezial",
                    ownerUserId = wgOwnerId,
                    ownerName = "WG Bibliothek",
                ),
            )
        )

        repository.deleteByPackNameForOwner(sharedPackName, wgOwnerId)

        val remainingEntries = repository.entries.first()
        val remainingTexts = remainingEntries.map { it.text }.toSet()

        assertEquals(setOf("Lena gleicher Pack", "WG anderer Pack"), remainingTexts)
        assertFalse(
            remainingEntries.any { entry ->
                entry.packName == sharedPackName && entry.ownerUserId == wgOwnerId
            }
        )
        assertTrue(
            remainingEntries.any { entry ->
                entry.text == "Lena gleicher Pack" &&
                    entry.packName == sharedPackName &&
                    entry.ownerUserId == lenaOwnerId
            }
        )
    }

    @Test
    fun updateMovesEntryBetweenQuestionLevelTables() = runBlocking {
        val ownerId = cardUserIdForName("WG Bibliothek")
        val id = repository.add(
            testEntry(
                id = 0,
                text = "Wechselt Level",
                drinks = 1,
                level = QuestionLevel.LEVEL_1,
                packName = "Level Test",
                ownerUserId = ownerId,
                ownerName = "WG Bibliothek",
            )
        )

        assertEquals(listOf(id), idsInLevel(QuestionLevel.LEVEL_1))
        assertTrue(idsInLevel(QuestionLevel.LEVEL_3).isEmpty())

        val original = repository.entries.first().single { it.id == id }
        repository.update(
            original.copy(
                drinks = 5,
                questionLevel = QuestionLevel.LEVEL_3.id,
            )
        )

        val combinedEntries = repository.entries.first()
        val levelThreeEntries = database.drinkEntryDao()
            .observeByQuestionLevel(QuestionLevel.LEVEL_3.id)
            .first()

        assertTrue(idsInLevel(QuestionLevel.LEVEL_1).isEmpty())
        assertEquals(listOf(id), levelThreeEntries.map { it.id })
        assertEquals(QuestionLevel.LEVEL_3.id, levelThreeEntries.single().questionLevel)
        assertEquals(listOf(id), combinedEntries.map { it.id })
    }

    @Test
    fun importedReplacementMovesEntryBetweenQuestionLevelTables() = runBlocking {
        val ownerId = cardUserIdForName("WG Bibliothek")
        repository.addAll(
            listOf(
                testEntry(
                    id = 42,
                    text = "Remote alt",
                    drinks = 1,
                    level = QuestionLevel.LEVEL_1,
                    packName = "Sync Test",
                    ownerUserId = ownerId,
                    ownerName = "WG Bibliothek",
                ).copy(
                    remoteId = "remote-42",
                    updatedAtMillis = 10,
                    syncStatus = CardSyncStatus.SYNCED.id,
                )
            )
        )

        repository.replaceImported(
            listOf(
                testEntry(
                    id = 42,
                    text = "Remote neu",
                    drinks = 3,
                    level = QuestionLevel.LEVEL_2,
                    packName = "Sync Test",
                    ownerUserId = ownerId,
                    ownerName = "WG Bibliothek",
                ).copy(
                    remoteId = "remote-42",
                    updatedAtMillis = 20,
                    syncStatus = CardSyncStatus.SYNCED.id,
                )
            )
        )

        val combinedEntries = repository.entries.first()
        val levelTwoEntries = database.drinkEntryDao()
            .observeByQuestionLevel(QuestionLevel.LEVEL_2.id)
            .first()

        assertTrue(idsInLevel(QuestionLevel.LEVEL_1).isEmpty())
        assertEquals(listOf(42L), levelTwoEntries.map { it.id })
        assertEquals("Remote neu", levelTwoEntries.single().text)
        assertEquals(QuestionLevel.LEVEL_2.id, levelTwoEntries.single().questionLevel)
        assertEquals(listOf(42L), combinedEntries.map { it.id })
    }

    private suspend fun idsInLevel(level: QuestionLevel): List<Long> =
        database.drinkEntryDao()
            .observeByQuestionLevel(level.id)
            .first()
            .map { it.id }

    private fun testEntry(
        id: Long,
        text: String,
        drinks: Int,
        level: QuestionLevel,
        packName: String,
        ownerUserId: String,
        ownerName: String,
    ): DrinkEntry =
        DrinkEntry(
            id = id,
            text = text,
            drinks = drinks,
            questionLevel = level.id,
            packName = packName,
            ownerUserId = ownerUserId,
            ownerName = ownerName,
            contributorUserId = ownerUserId,
            contributorName = ownerName,
        )
}
