package com.snupai.trinkspiel.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.snupai.trinkspiel.model.CardCategory
import com.snupai.trinkspiel.model.CardSyncStatus
import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_ID
import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_NAME
import com.snupai.trinkspiel.model.QuestionLevel

@Entity(tableName = "drink_level_1_entries")
data class DrinkLevelOneEntryEntity(
    @PrimaryKey val id: Long,
    val text: String,
    val drinks: Int,
    val category: String = CardCategory.CHALLENGE.id,
    val packName: String = "Eigene Karten",
    val isEnabled: Boolean = true,
    val isPendingReview: Boolean = false,
    val questionLevel: Int = QuestionLevel.LEVEL_1.id,
    val ownerUserId: String = DEFAULT_CARD_USER_ID,
    val ownerName: String = DEFAULT_CARD_USER_NAME,
    val contributorUserId: String = DEFAULT_CARD_USER_ID,
    val contributorName: String = DEFAULT_CARD_USER_NAME,
    val remoteId: String = "",
    val updatedAtMillis: Long = 0L,
    val syncStatus: String = CardSyncStatus.LOCAL.id,
)

@Entity(tableName = "drink_level_2_entries")
data class DrinkLevelTwoEntryEntity(
    @PrimaryKey val id: Long,
    val text: String,
    val drinks: Int,
    val category: String = CardCategory.CHALLENGE.id,
    val packName: String = "Eigene Karten",
    val isEnabled: Boolean = true,
    val isPendingReview: Boolean = false,
    val questionLevel: Int = QuestionLevel.LEVEL_2.id,
    val ownerUserId: String = DEFAULT_CARD_USER_ID,
    val ownerName: String = DEFAULT_CARD_USER_NAME,
    val contributorUserId: String = DEFAULT_CARD_USER_ID,
    val contributorName: String = DEFAULT_CARD_USER_NAME,
    val remoteId: String = "",
    val updatedAtMillis: Long = 0L,
    val syncStatus: String = CardSyncStatus.LOCAL.id,
)

@Entity(tableName = "drink_level_3_entries")
data class DrinkLevelThreeEntryEntity(
    @PrimaryKey val id: Long,
    val text: String,
    val drinks: Int,
    val category: String = CardCategory.CHALLENGE.id,
    val packName: String = "Eigene Karten",
    val isEnabled: Boolean = true,
    val isPendingReview: Boolean = false,
    val questionLevel: Int = QuestionLevel.LEVEL_3.id,
    val ownerUserId: String = DEFAULT_CARD_USER_ID,
    val ownerName: String = DEFAULT_CARD_USER_NAME,
    val contributorUserId: String = DEFAULT_CARD_USER_ID,
    val contributorName: String = DEFAULT_CARD_USER_NAME,
    val remoteId: String = "",
    val updatedAtMillis: Long = 0L,
    val syncStatus: String = CardSyncStatus.LOCAL.id,
)

fun DrinkEntry.toLevelOneEntity(): DrinkLevelOneEntryEntity =
    DrinkLevelOneEntryEntity(
        id = id,
        text = text,
        drinks = drinks,
        category = category,
        packName = packName,
        isEnabled = isEnabled,
        isPendingReview = isPendingReview,
        questionLevel = QuestionLevel.LEVEL_1.id,
        ownerUserId = ownerUserId,
        ownerName = ownerName,
        contributorUserId = contributorUserId,
        contributorName = contributorName,
        remoteId = remoteId,
        updatedAtMillis = updatedAtMillis,
        syncStatus = syncStatus,
    )

fun DrinkEntry.toLevelTwoEntity(): DrinkLevelTwoEntryEntity =
    DrinkLevelTwoEntryEntity(
        id = id,
        text = text,
        drinks = drinks,
        category = category,
        packName = packName,
        isEnabled = isEnabled,
        isPendingReview = isPendingReview,
        questionLevel = QuestionLevel.LEVEL_2.id,
        ownerUserId = ownerUserId,
        ownerName = ownerName,
        contributorUserId = contributorUserId,
        contributorName = contributorName,
        remoteId = remoteId,
        updatedAtMillis = updatedAtMillis,
        syncStatus = syncStatus,
    )

fun DrinkEntry.toLevelThreeEntity(): DrinkLevelThreeEntryEntity =
    DrinkLevelThreeEntryEntity(
        id = id,
        text = text,
        drinks = drinks,
        category = category,
        packName = packName,
        isEnabled = isEnabled,
        isPendingReview = isPendingReview,
        questionLevel = QuestionLevel.LEVEL_3.id,
        ownerUserId = ownerUserId,
        ownerName = ownerName,
        contributorUserId = contributorUserId,
        contributorName = contributorName,
        remoteId = remoteId,
        updatedAtMillis = updatedAtMillis,
        syncStatus = syncStatus,
    )
