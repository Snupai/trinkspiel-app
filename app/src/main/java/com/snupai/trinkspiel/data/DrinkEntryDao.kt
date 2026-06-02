package com.snupai.trinkspiel.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DrinkEntryDao {
    @Query(
        """
        SELECT id, text, drinks, category, packName, isEnabled, isPendingReview, questionLevel, ownerUserId, ownerName, contributorUserId, contributorName, remoteId, updatedAtMillis, syncStatus
        FROM drink_level_1_entries
        UNION ALL
        SELECT id, text, drinks, category, packName, isEnabled, isPendingReview, questionLevel, ownerUserId, ownerName, contributorUserId, contributorName, remoteId, updatedAtMillis, syncStatus
        FROM drink_level_2_entries
        UNION ALL
        SELECT id, text, drinks, category, packName, isEnabled, isPendingReview, questionLevel, ownerUserId, ownerName, contributorUserId, contributorName, remoteId, updatedAtMillis, syncStatus
        FROM drink_level_3_entries
        ORDER BY id DESC
        """
    )
    fun observeAll(): Flow<List<DrinkEntry>>

    @Query(
        """
        SELECT id, text, drinks, category, packName, isEnabled, isPendingReview, questionLevel, ownerUserId, ownerName, contributorUserId, contributorName, remoteId, updatedAtMillis, syncStatus
        FROM (
            SELECT id, text, drinks, category, packName, isEnabled, isPendingReview, questionLevel, ownerUserId, ownerName, contributorUserId, contributorName, remoteId, updatedAtMillis, syncStatus
            FROM drink_level_1_entries
            UNION ALL
            SELECT id, text, drinks, category, packName, isEnabled, isPendingReview, questionLevel, ownerUserId, ownerName, contributorUserId, contributorName, remoteId, updatedAtMillis, syncStatus
            FROM drink_level_2_entries
            UNION ALL
            SELECT id, text, drinks, category, packName, isEnabled, isPendingReview, questionLevel, ownerUserId, ownerName, contributorUserId, contributorName, remoteId, updatedAtMillis, syncStatus
            FROM drink_level_3_entries
        )
        WHERE questionLevel = :questionLevel
        ORDER BY id DESC
        """
    )
    fun observeByQuestionLevel(questionLevel: Int): Flow<List<DrinkEntry>>

    @Query("SELECT id, displayName FROM card_users ORDER BY lower(displayName), id")
    fun observeCardUsers(): Flow<List<CardUserEntity>>

    @Query(
        """
        SELECT MAX(id)
        FROM (
            SELECT id FROM drink_level_1_entries
            UNION ALL
            SELECT id FROM drink_level_2_entries
            UNION ALL
            SELECT id FROM drink_level_3_entries
        )
        """
    )
    suspend fun maxEntryId(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLevelOne(entry: DrinkLevelOneEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLevelTwo(entry: DrinkLevelTwoEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLevelThree(entry: DrinkLevelThreeEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllLevelOne(entries: List<DrinkLevelOneEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllLevelTwo(entries: List<DrinkLevelTwoEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllLevelThree(entries: List<DrinkLevelThreeEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCardUsers(users: List<CardUserEntity>)

    @Query("DELETE FROM drink_level_1_entries WHERE id = :id")
    suspend fun deleteLevelOneById(id: Long)

    @Query("DELETE FROM drink_level_2_entries WHERE id = :id")
    suspend fun deleteLevelTwoById(id: Long)

    @Query("DELETE FROM drink_level_3_entries WHERE id = :id")
    suspend fun deleteLevelThreeById(id: Long)

    @Query("DELETE FROM drink_level_1_entries WHERE id IN (:ids)")
    suspend fun deleteLevelOneByIds(ids: List<Long>)

    @Query("DELETE FROM drink_level_2_entries WHERE id IN (:ids)")
    suspend fun deleteLevelTwoByIds(ids: List<Long>)

    @Query("DELETE FROM drink_level_3_entries WHERE id IN (:ids)")
    suspend fun deleteLevelThreeByIds(ids: List<Long>)

    @Query("DELETE FROM drink_level_1_entries")
    suspend fun deleteAllLevelOne()

    @Query("DELETE FROM drink_level_2_entries")
    suspend fun deleteAllLevelTwo()

    @Query("DELETE FROM drink_level_3_entries")
    suspend fun deleteAllLevelThree()

    @Query("DELETE FROM drink_level_1_entries WHERE packName = :packName")
    suspend fun deleteLevelOneByPackName(packName: String)

    @Query("DELETE FROM drink_level_2_entries WHERE packName = :packName")
    suspend fun deleteLevelTwoByPackName(packName: String)

    @Query("DELETE FROM drink_level_3_entries WHERE packName = :packName")
    suspend fun deleteLevelThreeByPackName(packName: String)

    @Query("DELETE FROM drink_level_1_entries WHERE packName = :packName AND ownerUserId = :ownerUserId")
    suspend fun deleteLevelOneByPackNameAndOwner(packName: String, ownerUserId: String)

    @Query("DELETE FROM drink_level_2_entries WHERE packName = :packName AND ownerUserId = :ownerUserId")
    suspend fun deleteLevelTwoByPackNameAndOwner(packName: String, ownerUserId: String)

    @Query("DELETE FROM drink_level_3_entries WHERE packName = :packName AND ownerUserId = :ownerUserId")
    suspend fun deleteLevelThreeByPackNameAndOwner(packName: String, ownerUserId: String)
}
