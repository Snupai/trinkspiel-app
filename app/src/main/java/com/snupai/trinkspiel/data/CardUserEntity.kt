package com.snupai.trinkspiel.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_ID
import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_NAME

@Entity(tableName = "card_users")
data class CardUserEntity(
    @PrimaryKey val id: String = DEFAULT_CARD_USER_ID,
    val displayName: String = DEFAULT_CARD_USER_NAME,
)
