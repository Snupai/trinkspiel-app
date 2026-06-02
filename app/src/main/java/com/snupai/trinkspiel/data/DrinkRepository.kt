package com.snupai.trinkspiel.data

import com.snupai.trinkspiel.model.CardSyncStatus
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.model.normalizedCardUserId
import com.snupai.trinkspiel.model.normalizedCardUserName
import kotlinx.coroutines.flow.Flow

class DrinkRepository(private val dao: DrinkEntryDao) {
    val entries: Flow<List<DrinkEntry>> = dao.observeAll()
    val cardUsers: Flow<List<CardUserEntity>> = dao.observeCardUsers()

    suspend fun upsertCardUser(user: CardUserEntity) {
        dao.insertCardUsers(listOf(user))
    }

    suspend fun upsertCardUsers(users: List<CardUserEntity>) {
        if (users.isNotEmpty()) dao.insertCardUsers(users)
    }

    suspend fun add(text: String, drinks: Int): Long =
        add(DrinkEntry(text = text, drinks = drinks))

    suspend fun add(entry: DrinkEntry): Long {
        val entryWithId = entry
            .withNormalizedMetadata()
            .withCreatedTimestamp()
            .withStableId()
        dao.upsertCardUsersFor(listOf(entryWithId))
        insertIntoLevel(entryWithId)
        return entryWithId.id
    }

    suspend fun update(entry: DrinkEntry) {
        val entryWithId = entry
            .withNormalizedMetadata()
            .markedDirtyForLocalUpdate()
            .withStableId()
        dao.upsertCardUsersFor(listOf(entryWithId))
        dao.deleteByIdAcrossLevels(entryWithId.id)
        insertIntoLevel(entryWithId)
    }

    suspend fun updateAll(entries: List<DrinkEntry>) {
        entries.forEach { update(it) }
    }

    suspend fun replaceImported(entries: List<DrinkEntry>) {
        if (entries.isEmpty()) return
        val entriesWithIds = entries
            .map { it.withNormalizedMetadata() }
            .withStableIds()
        dao.upsertCardUsersFor(entriesWithIds)
        entriesWithIds.forEach { entry ->
            dao.deleteByIdAcrossLevels(entry.id)
            insertIntoLevel(entry)
        }
    }

    suspend fun delete(entry: DrinkEntry) {
        if (entry.id <= 0L) return
        dao.deleteByIdAcrossLevels(entry.id)
    }

    suspend fun deleteEntries(entries: List<DrinkEntry>) {
        val ids = entries.map { it.id }.filter { it > 0L }
        if (ids.isEmpty()) return
        dao.deleteByIdsAcrossLevels(ids)
    }

    suspend fun deleteAll() {
        dao.deleteAllLevelOne()
        dao.deleteAllLevelTwo()
        dao.deleteAllLevelThree()
    }

    suspend fun deleteByPackName(packName: String) {
        dao.deleteLevelOneByPackName(packName)
        dao.deleteLevelTwoByPackName(packName)
        dao.deleteLevelThreeByPackName(packName)
    }

    suspend fun deleteByPackNameForOwner(packName: String, ownerUserId: String) {
        val cleanPackName = packName.trim()
        val cleanOwnerUserId = ownerUserId.trim()
        if (cleanPackName.isBlank() || cleanOwnerUserId.isBlank()) return
        dao.deleteLevelOneByPackNameAndOwner(cleanPackName, cleanOwnerUserId)
        dao.deleteLevelTwoByPackNameAndOwner(cleanPackName, cleanOwnerUserId)
        dao.deleteLevelThreeByPackNameAndOwner(cleanPackName, cleanOwnerUserId)
    }

    suspend fun addAll(entries: List<DrinkEntry>) {
        if (entries.isEmpty()) return
        val entriesWithIds = entries
            .map { it.withNormalizedMetadata() }
            .withStableIds()
        dao.upsertCardUsersFor(entriesWithIds)
        dao.insertAllLevelOne(
            entriesWithIds
                .filter { QuestionLevel.fromId(it.questionLevel) == QuestionLevel.LEVEL_1 }
                .map { it.copy(questionLevel = QuestionLevel.LEVEL_1.id).toLevelOneEntity() }
        )
        dao.insertAllLevelTwo(
            entriesWithIds
                .filter { QuestionLevel.fromId(it.questionLevel) == QuestionLevel.LEVEL_2 }
                .map { it.copy(questionLevel = QuestionLevel.LEVEL_2.id).toLevelTwoEntity() }
        )
        dao.insertAllLevelThree(
            entriesWithIds
                .filter { QuestionLevel.fromId(it.questionLevel) == QuestionLevel.LEVEL_3 }
                .map { it.copy(questionLevel = QuestionLevel.LEVEL_3.id).toLevelThreeEntity() }
        )
    }

    private suspend fun DrinkEntry.withStableId(): DrinkEntry =
        if (id > 0L) this else copy(id = dao.nextEntryId())

    private suspend fun List<DrinkEntry>.withStableIds(): List<DrinkEntry> {
        val usedIds = map { it.id }.filter { it > 0L }.toMutableSet()
        var nextId = maxOf(dao.nextEntryId(), (usedIds.maxOrNull() ?: 0L) + 1L)
        return map { entry ->
            if (entry.id > 0L) {
                entry
            } else {
                while (nextId in usedIds) nextId += 1L
                entry.copy(id = nextId).also {
                    usedIds += nextId
                    nextId += 1L
                }
            }
        }
    }

    private fun DrinkEntry.withNormalizedMetadata(): DrinkEntry {
        val cleanOwnerName = normalizedCardUserName(ownerName)
        val cleanContributorName = contributorName.trim().ifBlank { cleanOwnerName }
        return copy(
            ownerUserId = normalizedCardUserId(ownerUserId, cleanOwnerName),
            ownerName = cleanOwnerName,
            contributorUserId = normalizedCardUserId(contributorUserId, cleanContributorName),
            contributorName = cleanContributorName,
            remoteId = remoteId.trim(),
            updatedAtMillis = updatedAtMillis.coerceAtLeast(0L),
            syncStatus = CardSyncStatus.fromId(syncStatus).id,
        )
    }

    private fun DrinkEntry.markedDirtyForLocalUpdate(): DrinkEntry =
        withUpdatedTimestamp().let { touchedEntry ->
            if (touchedEntry.remoteId.isBlank()) {
                touchedEntry.copy(syncStatus = CardSyncStatus.LOCAL.id)
            } else {
                touchedEntry.copy(syncStatus = CardSyncStatus.DIRTY.id)
            }
        }

    private fun DrinkEntry.withCreatedTimestamp(): DrinkEntry =
        if (remoteId.isBlank()) {
            copy(updatedAtMillis = updatedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis())
        } else {
            this
        }

    private fun DrinkEntry.withUpdatedTimestamp(): DrinkEntry =
        copy(updatedAtMillis = maxOf(updatedAtMillis, System.currentTimeMillis()))

    private suspend fun DrinkEntryDao.upsertCardUsersFor(entries: List<DrinkEntry>) {
        val users = entries
            .flatMap { entry ->
                listOf(
                    CardUserEntity(entry.ownerUserId, entry.ownerName),
                    CardUserEntity(entry.contributorUserId, entry.contributorName),
                )
            }
            .distinctBy { it.id }
        if (users.isNotEmpty()) insertCardUsers(users)
    }

    private suspend fun insertIntoLevel(entry: DrinkEntry) {
        when (QuestionLevel.fromId(entry.questionLevel)) {
            QuestionLevel.LEVEL_1 -> dao.insertLevelOne(
                entry.copy(questionLevel = QuestionLevel.LEVEL_1.id).toLevelOneEntity()
            )
            QuestionLevel.LEVEL_2 -> dao.insertLevelTwo(
                entry.copy(questionLevel = QuestionLevel.LEVEL_2.id).toLevelTwoEntity()
            )
            QuestionLevel.LEVEL_3 -> dao.insertLevelThree(
                entry.copy(questionLevel = QuestionLevel.LEVEL_3.id).toLevelThreeEntity()
            )
        }
    }

    private suspend fun DrinkEntryDao.nextEntryId(): Long =
        (maxEntryId() ?: 0L) + 1L

    private suspend fun DrinkEntryDao.deleteByIdAcrossLevels(id: Long) {
        deleteLevelOneById(id)
        deleteLevelTwoById(id)
        deleteLevelThreeById(id)
    }

    private suspend fun DrinkEntryDao.deleteByIdsAcrossLevels(ids: List<Long>) {
        deleteLevelOneByIds(ids)
        deleteLevelTwoByIds(ids)
        deleteLevelThreeByIds(ids)
    }
}
