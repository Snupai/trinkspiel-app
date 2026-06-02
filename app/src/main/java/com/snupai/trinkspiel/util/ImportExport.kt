package com.snupai.trinkspiel.util

import com.snupai.trinkspiel.data.DrinkEntry
import com.snupai.trinkspiel.model.CardCategory
import com.snupai.trinkspiel.model.CardSyncStatus
import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_ID
import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_NAME
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.model.normalizedCardUserId
import org.json.JSONArray
import org.json.JSONObject

object ImportExport {

    fun toJson(entries: List<DrinkEntry>, packName: String = "Seemops Export"): String {
        val root = JSONObject().apply {
            put("version", 7)
            put("packName", packName)
            put("cards", entries.toJsonArray())
        }
        return root.toString(2)
    }

    fun packNameFor(entries: List<DrinkEntry>, fallback: String = "Seemops Export"): String =
        entries
            .map { it.packName }
            .distinct()
            .singleOrNull()
            ?: fallback

    fun fileNameFor(entries: List<DrinkEntry>, fallback: String = "trinkspiel_export"): String {
        val name = packNameFor(entries, fallback)
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { fallback }
        return "$name.json"
    }

    fun fromJson(json: String): List<DrinkEntry> {
        val trimmed = json.trim()
        if (trimmed.isBlank()) return emptyList()

        val root = JSONTokenerCompat.next(trimmed)
        return when (root) {
            is JSONArray -> root.toEntries(defaultPackName = "Import")
            is JSONObject -> {
                val packName = root.optString("packName", "Import").ifBlank { "Import" }
                root.optJSONArray("cards")?.toEntries(packName) ?: emptyList()
            }
            else -> emptyList()
        }
    }

    fun entriesToJsonArray(entries: List<DrinkEntry>): JSONArray = entries.toJsonArray()

    fun entriesFromJsonArray(array: JSONArray, defaultPackName: String): List<DrinkEntry> =
        array.toEntries(defaultPackName)

    private fun List<DrinkEntry>.toJsonArray(): JSONArray {
        val array = JSONArray()
        for (entry in this) {
            val obj = JSONObject().apply {
                put("text", entry.text)
                put("drinks", entry.drinks)
                put("category", entry.category)
                put("packName", entry.packName)
                put("enabled", entry.isEnabled)
                put("pendingReview", entry.isPendingReview)
                put("questionLevel", QuestionLevel.fromId(entry.questionLevel).id)
                put("ownerUserId", normalizedCardUserId(entry.ownerUserId, entry.ownerName))
                put("ownerName", entry.ownerName.ifBlank { DEFAULT_CARD_USER_NAME })
                put("contributorUserId", normalizedCardUserId(entry.contributorUserId, entry.contributorName))
                put("contributorName", entry.contributorName.ifBlank { DEFAULT_CARD_USER_NAME })
                put("remoteId", entry.remoteId.trim())
                put("updatedAtMillis", entry.updatedAtMillis.coerceAtLeast(0L))
                put("syncStatus", CardSyncStatus.fromId(entry.syncStatus).id)
            }
            array.put(obj)
        }
        return array
    }

    private fun JSONArray.toEntries(defaultPackName: String): List<DrinkEntry> =
        (0 until length()).mapNotNull { i ->
            val obj = optJSONObject(i) ?: return@mapNotNull null
            val text = obj.optString("text").trim()
            if (text.isBlank()) return@mapNotNull null
            val drinks = obj.optInt("drinks", 1).coerceAtLeast(1)
            val fallbackLevel = QuestionLevel.fromDrinks(drinks).id
            val ownerName = obj.userNameFrom("ownerName", "owner")
            val contributorName = obj.userNameFrom("contributorName", "contributor", "addedBy")
            DrinkEntry(
                id = 0,
                text = text,
                drinks = drinks,
                category = CardCategory.fromId(obj.optString("category", CardCategory.CHALLENGE.id)).id,
                packName = obj.optString("packName", defaultPackName).ifBlank { defaultPackName },
                isEnabled = obj.optBoolean("enabled", obj.optBoolean("isEnabled", true)),
                isPendingReview = obj.optBoolean("pendingReview", obj.optBoolean("isPendingReview", false)),
                questionLevel = QuestionLevel.fromId(
                    obj.optInt("questionLevel", obj.optInt("level", fallbackLevel))
                ).id,
                ownerUserId = normalizedCardUserId(
                    obj.userIdFrom("ownerUserId", "ownerId"),
                    ownerName
                ),
                ownerName = ownerName,
                contributorUserId = normalizedCardUserId(
                    obj.userIdFrom("contributorUserId", "contributorId", "addedById"),
                    contributorName
                ),
                contributorName = contributorName,
                remoteId = obj.optString("remoteId", obj.optString("remoteCardId", "")).trim(),
                updatedAtMillis = obj.optLong("updatedAtMillis", obj.optLong("updatedAt", 0L)).coerceAtLeast(0L),
                syncStatus = CardSyncStatus.fromId(obj.optString("syncStatus", CardSyncStatus.LOCAL.id)).id,
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

private object JSONTokenerCompat {
    fun next(json: String): Any = org.json.JSONTokener(json).nextValue()
}
