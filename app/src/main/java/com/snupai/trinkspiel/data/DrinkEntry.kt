package com.snupai.trinkspiel.data

import com.snupai.trinkspiel.model.CardCategory
import com.snupai.trinkspiel.model.CardSyncStatus
import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_ID
import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_NAME
import com.snupai.trinkspiel.model.QuestionLevel

data class DrinkEntry(
    val id: Long = 0,
    val text: String,
    val drinks: Int,
    val category: String = CardCategory.CHALLENGE.id,
    val packName: String = "Eigene Karten",
    val isEnabled: Boolean = true,
    val isPendingReview: Boolean = false,
    val questionLevel: Int = QuestionLevel.fromDrinks(drinks).id,
    val ownerUserId: String = DEFAULT_CARD_USER_ID,
    val ownerName: String = DEFAULT_CARD_USER_NAME,
    val contributorUserId: String = DEFAULT_CARD_USER_ID,
    val contributorName: String = DEFAULT_CARD_USER_NAME,
    val remoteId: String = "",
    val updatedAtMillis: Long = 0L,
    val syncStatus: String = CardSyncStatus.LOCAL.id,
)
