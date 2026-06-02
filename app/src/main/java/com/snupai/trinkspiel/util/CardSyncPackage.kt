package com.snupai.trinkspiel.util

import com.snupai.trinkspiel.data.DrinkEntry
import com.snupai.trinkspiel.model.CardCategory
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.model.normalizedCardUserId
import com.snupai.trinkspiel.model.normalizedCardUserName
import com.snupai.trinkspiel.sync.RemoteCardJson
import com.snupai.trinkspiel.sync.RemoteCardSnapshot
import org.json.JSONObject
import org.json.JSONTokener

object CardSyncPackage {
    private const val PACKAGE_TYPE = "seemops.card_sync"
    private const val PACKAGE_VERSION = 1

    fun toJson(
        entries: List<DrinkEntry>,
        libraryOwnerUserId: String? = null,
    ): String {
        val ownerFilter = libraryOwnerUserId?.trim()?.takeIf { it.isNotBlank() }
        val cards = entries
            .filter { entry ->
                ownerFilter == null ||
                    normalizedCardUserId(entry.ownerUserId, entry.ownerName) == ownerFilter
            }
            .mapNotNull { entry -> entry.toRemoteSnapshotForPackage() }
        val root = JSONObject().apply {
            put("type", PACKAGE_TYPE)
            put("version", PACKAGE_VERSION)
            put("exportedAtMillis", System.currentTimeMillis())
            put("cards", RemoteCardJson.toJsonArray(cards))
        }
        return root.toString(2)
    }

    fun fromJson(json: String): List<RemoteCardSnapshot> {
        val trimmed = json.trim()
        if (trimmed.isBlank()) return emptyList()
        return RemoteCardJson.fromJson(trimmed)
    }

    fun isCardSyncPackage(json: String): Boolean =
        runCatching {
            val root = JSONTokener(json.trim()).nextValue()
            root is JSONObject && root.optString("type") == PACKAGE_TYPE
        }.getOrDefault(false)

    fun fileName(): String = "seemops_card_sync.json"

    private fun DrinkEntry.toRemoteSnapshotForPackage(): RemoteCardSnapshot? {
        val text = text.trim().takeIf { it.isNotBlank() } ?: return null
        val ownerName = normalizedCardUserName(ownerName)
        val contributorName = contributorName.trim().ifBlank { ownerName }
        val cleanOwnerUserId = normalizedCardUserId(ownerUserId, ownerName)
        val remoteId = remoteId.trim()
            .ifBlank { generatedPackageRemoteId(cleanOwnerUserId, id, text) }
        return RemoteCardSnapshot(
            remoteId = remoteId,
            text = text,
            drinks = drinks.coerceAtLeast(1),
            category = CardCategory.fromId(category).id,
            packName = packName.trim().ifBlank { "Eigene Karten" },
            isEnabled = isEnabled,
            isPendingReview = isPendingReview,
            questionLevel = QuestionLevel.fromId(questionLevel).id,
            ownerUserId = cleanOwnerUserId,
            ownerName = ownerName,
            contributorUserId = normalizedCardUserId(contributorUserId, contributorName),
            contributorName = contributorName,
            updatedAtMillis = updatedAtMillis.coerceAtLeast(0L),
        )
    }

    private fun generatedPackageRemoteId(ownerUserId: String, localId: Long, text: String): String {
        val stableId = if (localId > 0L) {
            localId.toString()
        } else {
            text.lowercase()
                .toByteArray(Charsets.UTF_8)
                .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }
        return "package:$ownerUserId:$stableId"
    }
}
