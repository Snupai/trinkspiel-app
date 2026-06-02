package com.snupai.trinkspiel.sync

import com.snupai.trinkspiel.model.CardCategory
import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_ID
import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_NAME
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.model.normalizedCardUserId
import com.snupai.trinkspiel.model.normalizedCardUserName
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

object RemoteCardJson {
    fun toJsonArray(cards: List<RemoteCardSnapshot>): JSONArray {
        val array = JSONArray()
        cards.forEach { card ->
            array.put(toJsonObject(card))
        }
        return array
    }

    fun toJsonObject(card: RemoteCardSnapshot): JSONObject =
        JSONObject()
            .put("remoteId", card.remoteId.trim())
            .put("text", card.text.trim())
            .put("drinks", card.drinks.coerceAtLeast(1))
            .put("category", CardCategory.fromId(card.category).id)
            .put("packName", card.packName.trim().ifBlank { "Eigene Karten" })
            .put("enabled", card.isEnabled)
            .put("pendingReview", card.isPendingReview)
            .put("questionLevel", QuestionLevel.fromId(card.questionLevel).id)
            .put("ownerUserId", normalizedCardUserId(card.ownerUserId, card.ownerName))
            .put("ownerName", normalizedCardUserName(card.ownerName))
            .put("contributorUserId", normalizedCardUserId(card.contributorUserId, card.contributorName))
            .put("contributorName", card.contributorName.trim().ifBlank { normalizedCardUserName(card.ownerName) })
            .put("updatedAtMillis", card.updatedAtMillis.coerceAtLeast(0L))

    fun fromJson(json: String): List<RemoteCardSnapshot> {
        val trimmed = json.trim()
        if (trimmed.isBlank()) return emptyList()

        val root = JSONTokener(trimmed).nextValue()
        return when (root) {
            is JSONArray -> fromJsonArray(root)
            is JSONObject -> fromJsonArray(root.optJSONArray("cards") ?: return emptyList())
            else -> emptyList()
        }
    }

    fun fromJsonArray(cards: JSONArray): List<RemoteCardSnapshot> =
        (0 until cards.length()).mapNotNull { index ->
            cards.optJSONObject(index)?.toRemoteCardOrNull()
        }

    fun JSONObject.toRemoteCardOrNull(): RemoteCardSnapshot? {
        val text = optString("text").trim().takeIf { it.isNotBlank() } ?: return null
        val drinks = optInt("drinks", 1).coerceAtLeast(1)
        val ownerName = userNameFrom("ownerName", "owner")
        val contributorName = userNameFrom("contributorName", "contributor", "addedBy")
        val remoteId = optString("remoteId", optString("remoteCardId", "")).trim()
        if (remoteId.isBlank()) return null
        return RemoteCardSnapshot(
            remoteId = remoteId,
            text = text,
            drinks = drinks,
            category = CardCategory.fromId(optString("category", CardCategory.CHALLENGE.id)).id,
            packName = optString("packName", "Eigene Karten").trim().ifBlank { "Eigene Karten" },
            isEnabled = optBoolean("enabled", optBoolean("isEnabled", true)),
            isPendingReview = optBoolean("pendingReview", optBoolean("isPendingReview", false)),
            questionLevel = QuestionLevel.fromId(
                optInt("questionLevel", optInt("level", QuestionLevel.fromDrinks(drinks).id))
            ).id,
            ownerUserId = normalizedCardUserId(
                userIdFrom("ownerUserId", "ownerId"),
                ownerName,
            ),
            ownerName = ownerName,
            contributorUserId = normalizedCardUserId(
                userIdFrom("contributorUserId", "contributorId", "addedById"),
                contributorName,
            ),
            contributorName = contributorName,
            updatedAtMillis = optLong("updatedAtMillis", optLong("updatedAt", 0L)).coerceAtLeast(0L),
        )
    }

    private fun JSONObject.userNameFrom(vararg keys: String): String {
        keys.forEach { key ->
            val value = optString(key).trim()
            if (value.isNotBlank()) return value
        }
        return DEFAULT_CARD_USER_NAME
    }

    private fun JSONObject.userIdFrom(vararg keys: String): String {
        keys.forEach { key ->
            val value = optString(key).trim()
            if (value.isNotBlank()) return value
        }
        return DEFAULT_CARD_USER_ID
    }
}
