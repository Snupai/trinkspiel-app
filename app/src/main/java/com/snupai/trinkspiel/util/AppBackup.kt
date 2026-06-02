package com.snupai.trinkspiel.util

import com.snupai.trinkspiel.data.DrinkEntry
import com.snupai.trinkspiel.data.CardUserEntity
import com.snupai.trinkspiel.model.CardCategory
import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_ID
import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_NAME
import com.snupai.trinkspiel.model.DrinkIntensity
import com.snupai.trinkspiel.model.GameMode
import com.snupai.trinkspiel.model.GameSettings
import com.snupai.trinkspiel.model.PlayerStats
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.model.TeamOption
import com.snupai.trinkspiel.model.ThemeMode
import com.snupai.trinkspiel.model.normalizedCardUserId
import com.snupai.trinkspiel.model.normalizedCardUserName
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class AppBackupData(
    val cards: List<DrinkEntry>,
    val settings: GameSettings?,
    val cardUsers: List<CardUserEntity> = emptyList(),
)

object AppBackup {
    private const val BACKUP_VERSION = 1

    fun toJson(
        entries: List<DrinkEntry>,
        settings: GameSettings,
        cardUsers: List<CardUserEntity> = emptyList(),
    ): String {
        val root = JSONObject().apply {
            put("type", "seemops.backup")
            put("version", BACKUP_VERSION)
            put("cards", ImportExport.entriesToJsonArray(entries))
            put("settings", settings.toJsonObject())
            put("cardUsers", cardUsers.toCardUsersJsonArray())
        }
        return root.toString(2)
    }

    fun fromJson(json: String): AppBackupData {
        val trimmed = json.trim()
        if (trimmed.isBlank()) return AppBackupData(cards = emptyList(), settings = null)

        val root = JSONTokener(trimmed).nextValue()
        return when (root) {
            is JSONArray -> AppBackupData(
                cards = ImportExport.entriesFromJsonArray(root, defaultPackName = "Import"),
                settings = null,
            )
            is JSONObject -> {
                val cards = root.optJSONArray("cards")?.let { cardsArray ->
                    val fallbackPackName = root.optString("packName", "Import").ifBlank { "Import" }
                    ImportExport.entriesFromJsonArray(cardsArray, fallbackPackName)
                }.orEmpty()
                AppBackupData(
                    cards = cards,
                    settings = root.optJSONObject("settings")?.toGameSettings(),
                    cardUsers = root.optJSONArray("cardUsers").toCardUsers(),
                )
            }
            else -> AppBackupData(cards = emptyList(), settings = null)
        }
    }

    fun fileName(): String = "seemops_backup.json"

    private fun GameSettings.toJsonObject(): JSONObject = JSONObject().apply {
        put("players", players.toStringJsonArray())
        put("playerStats", scoreboard.toPlayerStatsJsonArray())
        put("currentPlayerIndex", currentPlayerIndex)
        put("mode", mode.id)
        put("intensity", intensity.id)
        put("ageGateAccepted", ageGateAccepted)
        put("safetyNoticeAccepted", safetyNoticeAccepted)
        put("firstRunSetupCompleted", firstRunSetupCompleted)
        put("themeMode", themeMode.id)
        put("dynamicColors", dynamicColors)
        put("drinkSingular", drinkSingular)
        put("drinkPlural", drinkPlural)
        put("cardOwnerUserId", normalizedCardUserId(cardOwnerUserId, cardOwnerName))
        put("cardOwnerName", cardOwnerName)
        put("activeContributorUserId", normalizedCardUserId(activeContributorUserId, activeContributorName))
        put("activeContributorName", activeContributorName)
        put(
            "excludedPackNames",
            excludedPackNamesForActiveLibrary()
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
                .toStringJsonArray()
        )
        put("excludedPackNamesByOwner", excludedPackNamesByOwner.toPackExclusionsJsonObject())
        put("customCategoryIds", customCategoryIds.sorted().toStringJsonArray())
        put("enabledQuestionLevelIds", enabledQuestionLevelIds.sorted().toIntJsonArray())
    }

    private fun JSONObject.toGameSettings(): GameSettings {
        val players = optJSONArray("players").toStringList()
        val stats = optJSONArray("playerStats").toPlayerStats(players)
        return GameSettings(
            players = players,
            playerStats = stats,
            currentPlayerIndex = optInt("currentPlayerIndex", 0).coerceAtLeast(0),
            mode = GameMode.fromId(optString("mode", GameMode.CLASSIC.id)),
            intensity = DrinkIntensity.fromId(optString("intensity", DrinkIntensity.MEDIUM.id)),
            ageGateAccepted = optBoolean("ageGateAccepted", false),
            safetyNoticeAccepted = optBoolean("safetyNoticeAccepted", false),
            firstRunSetupCompleted = optBoolean("firstRunSetupCompleted", false),
            themeMode = ThemeMode.fromId(optString("themeMode", ThemeMode.SYSTEM.id)),
            dynamicColors = optBoolean("dynamicColors", false),
            drinkSingular = optString("drinkSingular", "Schluck"),
            drinkPlural = optString("drinkPlural", "Schlucke"),
            cardOwnerUserId = normalizedCardUserId(
                optString("cardOwnerUserId", DEFAULT_CARD_USER_ID),
                optString("cardOwnerName", DEFAULT_CARD_USER_NAME)
            ),
            cardOwnerName = optString("cardOwnerName", DEFAULT_CARD_USER_NAME),
            activeContributorUserId = normalizedCardUserId(
                optString("activeContributorUserId", DEFAULT_CARD_USER_ID),
                optString("activeContributorName", DEFAULT_CARD_USER_NAME)
            ),
            activeContributorName = optString("activeContributorName", DEFAULT_CARD_USER_NAME),
            excludedPackNames = optJSONArray("excludedPackNames").toStringList().toSet(),
            excludedPackNamesByOwner = optJSONObject("excludedPackNamesByOwner").toPackExclusionsMap(),
            customCategoryIds = optJSONArray("customCategoryIds")
                .toStringList()
                .map { CardCategory.fromId(it).id }
                .toSet()
                .ifEmpty { CardCategory.entries.map { it.id }.toSet() },
            enabledQuestionLevelIds = optJSONArray("enabledQuestionLevelIds")
                .toIntSet(validValues = QuestionLevel.entries.map { it.id }.toSet())
                ?: QuestionLevel.entries.map { it.id }.toSet(),
        ).withNormalizedPackExclusions()
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            optString(index).trim().takeIf { it.isNotBlank() }
        }
    }

    private fun JSONArray?.toCardUsers(): List<CardUserEntity> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            val obj = optJSONObject(index) ?: return@mapNotNull null
            val displayName = normalizedCardUserName(
                obj.optString("displayName", obj.optString("name", DEFAULT_CARD_USER_NAME))
            )
            val id = normalizedCardUserId(obj.optString("id", DEFAULT_CARD_USER_ID), displayName)
            CardUserEntity(id = id, displayName = displayName)
        }.distinctBy { it.id }
    }

    private fun JSONArray?.toPlayerStats(players: List<String>): Map<String, PlayerStats> {
        if (this == null) return emptyMap()
        return (0 until length()).mapNotNull { index ->
            val obj = optJSONObject(index) ?: return@mapNotNull null
            val name = obj.optString("name").trim().takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            PlayerStats(
                name = name,
                team = TeamOption.fromId(obj.optString("team", TeamOption.SOLO.id)),
                points = obj.optInt("points", 0).coerceAtLeast(0),
                drinks = obj.optInt("drinks", 0).coerceAtLeast(0),
            )
        }.associateBy { stats ->
            players.firstOrNull { it.equals(stats.name, ignoreCase = true) } ?: stats.name
        }
    }

    private fun List<String>.toStringJsonArray(): JSONArray {
        val array = JSONArray()
        forEach { value -> array.put(value) }
        return array
    }

    private fun List<Int>.toIntJsonArray(): JSONArray {
        val array = JSONArray()
        forEach { value -> array.put(value) }
        return array
    }

    private fun JSONArray?.toIntSet(validValues: Set<Int>): Set<Int>? {
        if (this == null) return null
        return (0 until length()).mapNotNull { index ->
            optInt(index, 0).takeIf { it in validValues }
        }.toSet()
    }

    private fun Map<String, Set<String>>.toPackExclusionsJsonObject(): JSONObject =
        JSONObject().apply {
            cleanPackExclusions()
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                .forEach { (ownerUserId, packNames) ->
                    put(
                        ownerUserId,
                        packNames.sortedWith(String.CASE_INSENSITIVE_ORDER).toStringJsonArray()
                    )
                }
        }

    private fun JSONObject?.toPackExclusionsMap(): Map<String, Set<String>> {
        if (this == null) return emptyMap()
        val result = mutableMapOf<String, Set<String>>()
        val keys = keys()
        while (keys.hasNext()) {
            val ownerUserId = keys.next().trim()
            if (ownerUserId.isBlank()) continue
            val packNames = optJSONArray(ownerUserId).toStringList().toSet()
            if (packNames.isNotEmpty()) {
                result[ownerUserId] = packNames
            }
        }
        return result
    }

    private fun Map<String, Set<String>>.cleanPackExclusions(): Map<String, Set<String>> =
        mapNotNull { (ownerUserId, packNames) ->
            val cleanOwnerUserId = ownerUserId.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val cleanPackNames = packNames
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
            cleanOwnerUserId to cleanPackNames
        }
            .filter { (_, packNames) -> packNames.isNotEmpty() }
            .toMap()

    private fun List<PlayerStats>.toPlayerStatsJsonArray(): JSONArray {
        val array = JSONArray()
        forEach { stats ->
            array.put(
                JSONObject()
                    .put("name", stats.name)
                    .put("team", stats.team.id)
                    .put("points", stats.points)
                    .put("drinks", stats.drinks)
            )
        }
        return array
    }

    private fun List<CardUserEntity>.toCardUsersJsonArray(): JSONArray {
        val array = JSONArray()
        map { user ->
            val displayName = normalizedCardUserName(user.displayName)
            CardUserEntity(
                id = normalizedCardUserId(user.id, displayName),
                displayName = displayName,
            )
        }
            .distinctBy { it.id }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
            .forEach { user ->
                array.put(
                    JSONObject()
                        .put("id", user.id)
                        .put("displayName", user.displayName)
                )
            }
        return array
    }
}
