package com.snupai.trinkspiel.data

import android.content.Context
import androidx.core.content.edit
import com.snupai.trinkspiel.model.CardCategory
import com.snupai.trinkspiel.model.CompletedTurn
import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_ID
import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_NAME
import com.snupai.trinkspiel.model.DrinkIntensity
import com.snupai.trinkspiel.model.DrawnCardRecord
import com.snupai.trinkspiel.model.GameMode
import com.snupai.trinkspiel.model.GameSettings
import com.snupai.trinkspiel.model.PlayerStats
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.model.SavedRoundState
import com.snupai.trinkspiel.model.TeamOption
import com.snupai.trinkspiel.model.ThemeMode
import com.snupai.trinkspiel.model.normalizedCardUserId
import com.snupai.trinkspiel.model.normalizedCardUserName
import com.snupai.trinkspiel.sync.RemoteSyncConfig
import org.json.JSONArray
import org.json.JSONObject

class GamePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("trinkspiel_state", Context.MODE_PRIVATE)

    fun loadSettings(): GameSettings {
        val players = loadPlayers()
        return GameSettings(
            players = players,
            playerStats = loadPlayerStats(players),
            currentPlayerIndex = prefs.getInt(KEY_CURRENT_PLAYER_INDEX, 0).coerceAtLeast(0),
            mode = GameMode.fromId(prefs.getString(KEY_MODE, GameMode.CLASSIC.id).orEmpty()),
            intensity = DrinkIntensity.fromId(prefs.getString(KEY_INTENSITY, DrinkIntensity.MEDIUM.id).orEmpty()),
            ageGateAccepted = prefs.getBoolean(KEY_AGE_GATE_ACCEPTED, false),
            safetyNoticeAccepted = prefs.getBoolean(KEY_SAFETY_ACCEPTED, false),
            firstRunSetupCompleted = prefs.getBoolean(KEY_FIRST_RUN_SETUP_COMPLETED, false),
            themeMode = ThemeMode.fromId(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.id).orEmpty()),
            dynamicColors = prefs.getBoolean(KEY_DYNAMIC_COLORS, false),
            drinkSingular = prefs.getString(KEY_DRINK_SINGULAR, "Schluck").orEmpty(),
            drinkPlural = prefs.getString(KEY_DRINK_PLURAL, "Schlucke").orEmpty(),
            cardOwnerUserId = prefs.getString(KEY_CARD_OWNER_USER_ID, DEFAULT_CARD_USER_ID).orEmpty(),
            cardOwnerName = prefs.getString(KEY_CARD_OWNER_NAME, DEFAULT_CARD_USER_NAME).orEmpty(),
            activeContributorUserId = prefs.getString(KEY_ACTIVE_CONTRIBUTOR_USER_ID, DEFAULT_CARD_USER_ID).orEmpty(),
            activeContributorName = prefs.getString(KEY_ACTIVE_CONTRIBUTOR_NAME, DEFAULT_CARD_USER_NAME).orEmpty(),
            excludedPackNames = loadExcludedPackNames(),
            excludedPackNamesByOwner = loadExcludedPackNamesByOwner(),
            customCategoryIds = loadCustomCategoryIds(),
            enabledQuestionLevelIds = loadEnabledQuestionLevelIds(),
        ).normalize()
    }

    fun loadRemoteSyncConfig(): RemoteSyncConfig =
        RemoteSyncConfig(
            endpointUrl = prefs.getString(KEY_REMOTE_SYNC_ENDPOINT_URL, "").orEmpty(),
            accessToken = prefs.getString(KEY_REMOTE_SYNC_ACCESS_TOKEN, "").orEmpty(),
            role = prefs.getString(KEY_REMOTE_SYNC_ROLE, "").orEmpty(),
        ).normalized()

    fun saveRemoteSyncConfig(config: RemoteSyncConfig) {
        val normalized = config.normalized()
        prefs.edit {
            putString(KEY_REMOTE_SYNC_ENDPOINT_URL, normalized.endpointUrl)
            putString(KEY_REMOTE_SYNC_ACCESS_TOKEN, normalized.accessToken)
            putString(KEY_REMOTE_SYNC_ROLE, normalized.role)
        }
    }

    fun saveSettings(settings: GameSettings) {
        val normalized = settings.normalize()
        prefs.edit {
            putString(KEY_PLAYERS, JSONArray(normalized.players).toString())
            putString(KEY_PLAYER_STATS, normalized.scoreboard.toPlayerStatsJsonArray().toString())
            putInt(KEY_CURRENT_PLAYER_INDEX, normalized.currentPlayerIndex)
            putString(KEY_MODE, normalized.mode.id)
            putString(KEY_INTENSITY, normalized.intensity.id)
            putBoolean(KEY_AGE_GATE_ACCEPTED, normalized.ageGateAccepted)
            putBoolean(KEY_SAFETY_ACCEPTED, normalized.safetyNoticeAccepted)
            putBoolean(KEY_FIRST_RUN_SETUP_COMPLETED, normalized.firstRunSetupCompleted)
            putString(KEY_THEME_MODE, normalized.themeMode.id)
            putBoolean(KEY_DYNAMIC_COLORS, normalized.dynamicColors)
            putString(KEY_DRINK_SINGULAR, normalized.drinkSingular)
            putString(KEY_DRINK_PLURAL, normalized.drinkPlural)
            putString(KEY_CARD_OWNER_USER_ID, normalized.cardOwnerUserId)
            putString(KEY_CARD_OWNER_NAME, normalized.cardOwnerName)
            putString(KEY_ACTIVE_CONTRIBUTOR_USER_ID, normalized.activeContributorUserId)
            putString(KEY_ACTIVE_CONTRIBUTOR_NAME, normalized.activeContributorName)
            putString(
                KEY_EXCLUDED_PACK_NAMES,
                JSONArray(
                    normalized.excludedPackNamesForActiveLibrary()
                        .sortedWith(String.CASE_INSENSITIVE_ORDER)
                ).toString()
            )
            putString(
                KEY_EXCLUDED_PACK_NAMES_BY_OWNER,
                normalized.excludedPackNamesByOwner.toPackExclusionsJsonObject().toString()
            )
            putString(KEY_CUSTOM_CATEGORY_IDS, JSONArray(normalized.customCategoryIds.toList()).toString())
            putString(
                KEY_ENABLED_QUESTION_LEVEL_IDS,
                JSONArray(normalized.enabledQuestionLevelIds.sorted()).toString()
            )
        }
    }

    fun loadRoundState(): SavedRoundState = SavedRoundState(
        currentCardId = prefs.getLong(KEY_CURRENT_CARD_ID, NO_CARD_ID).takeUnless { it == NO_CARD_ID },
        isCardVisible = prefs.getBoolean(KEY_CARD_VISIBLE, false),
        drawnCardIds = prefs.getString(KEY_DRAWN_IDS, "")
            .orEmpty()
            .split(",")
            .mapNotNull { it.toLongOrNull() }
            .toSet(),
        drawHistory = loadDrawHistory(),
        drawsThisRound = prefs.getInt(KEY_DRAWS, 0).coerceAtLeast(0),
        drinksThisRound = prefs.getInt(KEY_DRINKS, 0).coerceAtLeast(0),
        deckFinishedCount = prefs.getInt(KEY_DECKS, 0).coerceAtLeast(0),
        lastCompletedTurn = loadLastCompletedTurn(),
    )

    fun saveRoundState(state: SavedRoundState) {
        prefs.edit {
            putLong(KEY_CURRENT_CARD_ID, state.currentCardId ?: NO_CARD_ID)
            putBoolean(KEY_CARD_VISIBLE, state.isCardVisible)
            putString(KEY_DRAWN_IDS, state.drawnCardIds.joinToString(","))
            putString(KEY_DRAW_HISTORY, state.drawHistory.toDrawHistoryJsonArray().toString())
            putInt(KEY_DRAWS, state.drawsThisRound.coerceAtLeast(0))
            putInt(KEY_DRINKS, state.drinksThisRound.coerceAtLeast(0))
            putInt(KEY_DECKS, state.deckFinishedCount.coerceAtLeast(0))
            val turn = state.lastCompletedTurn
            if (turn == null) {
                remove(KEY_LAST_TURN_CARD_ID)
                remove(KEY_LAST_TURN_CARD_DRINKS)
                remove(KEY_LAST_TURN_PLAYER)
                remove(KEY_LAST_TURN_PLAYER_INDEX)
            } else {
                putLong(KEY_LAST_TURN_CARD_ID, turn.cardId)
                putInt(KEY_LAST_TURN_CARD_DRINKS, turn.cardDrinks.coerceAtLeast(0))
                putString(KEY_LAST_TURN_PLAYER, turn.playerName.orEmpty())
                putInt(KEY_LAST_TURN_PLAYER_INDEX, turn.previousPlayerIndex.coerceAtLeast(0))
            }
        }
    }

    private fun loadDrawHistory(): List<DrawnCardRecord> {
        val raw = prefs.getString(KEY_DRAW_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val text = obj.optString("text").trim().takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val drinks = obj.optInt("drinks", 1).coerceAtLeast(1)
                DrawnCardRecord(
                    cardId = obj.optLong("cardId", NO_CARD_ID),
                    text = text,
                    drinks = drinks,
                    category = CardCategory.fromId(obj.optString("category", CardCategory.CHALLENGE.id)).id,
                    packName = obj.optString("packName", "Import").trim().ifBlank { "Import" },
                    playerName = obj.optString("playerName", "")
                        .trim()
                        .takeIf { it.isNotBlank() },
                    roundNumber = obj.optInt("roundNumber", 1).coerceAtLeast(1),
                    questionLevel = QuestionLevel.fromId(
                        obj.optInt("questionLevel", QuestionLevel.fromDrinks(drinks).id)
                    ).id,
                    ownerUserId = normalizedCardUserId(
                        obj.optString("ownerUserId", DEFAULT_CARD_USER_ID),
                        obj.optString("ownerName", DEFAULT_CARD_USER_NAME)
                    ),
                    ownerName = obj.optString("ownerName", DEFAULT_CARD_USER_NAME).trim()
                        .ifBlank { DEFAULT_CARD_USER_NAME },
                    contributorUserId = normalizedCardUserId(
                        obj.optString("contributorUserId", DEFAULT_CARD_USER_ID),
                        obj.optString("contributorName", DEFAULT_CARD_USER_NAME)
                    ),
                    contributorName = obj.optString("contributorName", DEFAULT_CARD_USER_NAME).trim()
                        .ifBlank { DEFAULT_CARD_USER_NAME },
                )
            }.filter { it.cardId != NO_CARD_ID }
        }.getOrDefault(emptyList())
    }

    private fun loadLastCompletedTurn(): CompletedTurn? {
        val cardId = prefs.getLong(KEY_LAST_TURN_CARD_ID, NO_CARD_ID)
        if (cardId == NO_CARD_ID) return null
        return CompletedTurn(
            cardId = cardId,
            cardDrinks = prefs.getInt(KEY_LAST_TURN_CARD_DRINKS, 0).coerceAtLeast(0),
            playerName = prefs.getString(KEY_LAST_TURN_PLAYER, null)
                ?.trim()
                ?.takeIf { it.isNotBlank() },
            previousPlayerIndex = prefs.getInt(KEY_LAST_TURN_PLAYER_INDEX, 0).coerceAtLeast(0),
        )
    }

    private fun loadPlayers(): List<String> {
        val raw = prefs.getString(KEY_PLAYERS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                array.optString(i).trim().takeIf { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    private fun loadExcludedPackNames(): Set<String> {
        val raw = prefs.getString(KEY_EXCLUDED_PACK_NAMES, null) ?: return emptySet()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                array.optString(i).trim().takeIf { it.isNotBlank() }
            }.toSet()
        }.getOrDefault(emptySet())
    }

    private fun loadExcludedPackNamesByOwner(): Map<String, Set<String>> {
        val raw = prefs.getString(KEY_EXCLUDED_PACK_NAMES_BY_OWNER, null) ?: return emptyMap()
        return runCatching {
            JSONObject(raw).toPackExclusionsMap()
        }.getOrDefault(emptyMap())
    }

    private fun loadCustomCategoryIds(): Set<String> {
        val raw = prefs.getString(KEY_CUSTOM_CATEGORY_IDS, null)
            ?: return CardCategory.entries.map { it.id }.toSet()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                array.optString(i).trim().takeIf { it.isNotBlank() }
            }.map { CardCategory.fromId(it).id }.toSet()
        }.getOrDefault(CardCategory.entries.map { it.id }.toSet())
    }

    private fun loadEnabledQuestionLevelIds(): Set<Int> {
        val raw = prefs.getString(KEY_ENABLED_QUESTION_LEVEL_IDS, null)
            ?: return QuestionLevel.entries.map { it.id }.toSet()
        return runCatching {
            val validIds = QuestionLevel.entries.map { it.id }.toSet()
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                array.optInt(i, 0).takeIf { it in validIds }
            }.toSet()
        }.getOrDefault(QuestionLevel.entries.map { it.id }.toSet())
    }


    private fun loadPlayerStats(players: List<String>): Map<String, PlayerStats> {
        val raw = prefs.getString(KEY_PLAYER_STATS, null) ?: return emptyMap()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
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
        }.getOrDefault(emptyMap())
    }

    private fun GameSettings.normalize(): GameSettings {
        val cleanedPlayers = players
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        val index = when {
            cleanedPlayers.isEmpty() -> 0
            currentPlayerIndex in cleanedPlayers.indices -> currentPlayerIndex
            else -> 0
        }
        val cleanOwnerName = normalizedCardUserName(cardOwnerName)
        val cleanContributorName = activeContributorName.trim().ifBlank { cleanOwnerName }
        return copy(
            players = cleanedPlayers,
            playerStats = normalizePlayerStats(cleanedPlayers, playerStats),
            currentPlayerIndex = index,
            drinkSingular = drinkSingular.trim().ifBlank { "Schluck" },
            drinkPlural = drinkPlural.trim().ifBlank { "Schlucke" },
            cardOwnerUserId = normalizedCardUserId(cardOwnerUserId, cleanOwnerName),
            cardOwnerName = cleanOwnerName,
            activeContributorUserId = normalizedCardUserId(activeContributorUserId, cleanContributorName),
            activeContributorName = cleanContributorName,
            excludedPackNames = excludedPackNames
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet(),
            excludedPackNamesByOwner = excludedPackNamesByOwner.cleanPackExclusions(),
            customCategoryIds = customCategoryIds
                .map { CardCategory.fromId(it).id }
                .toSet(),
            enabledQuestionLevelIds = enabledQuestionLevelIds
                .mapNotNull { id -> QuestionLevel.entries.firstOrNull { it.id == id }?.id }
                .toSet(),
        ).withNormalizedPackExclusions()
    }

    private fun normalizePlayerStats(
        players: List<String>,
        stats: Map<String, PlayerStats>
    ): Map<String, PlayerStats> {
        val byName = stats.values.associateBy { it.name.lowercase() }
        return players.associateWith { player ->
            val existing = stats[player] ?: byName[player.lowercase()]
            PlayerStats(
                name = player,
                team = existing?.team ?: TeamOption.SOLO,
                points = existing?.points?.coerceAtLeast(0) ?: 0,
                drinks = existing?.drinks?.coerceAtLeast(0) ?: 0,
            )
        }
    }

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

    private fun List<DrawnCardRecord>.toDrawHistoryJsonArray(): JSONArray {
        val array = JSONArray()
        forEach { record ->
            array.put(
                JSONObject()
                    .put("cardId", record.cardId)
                    .put("text", record.text)
                    .put("drinks", record.drinks.coerceAtLeast(1))
                    .put("category", CardCategory.fromId(record.category).id)
                    .put("packName", record.packName)
                    .put("playerName", record.playerName.orEmpty())
                    .put("roundNumber", record.roundNumber.coerceAtLeast(1))
                    .put("questionLevel", QuestionLevel.fromId(record.questionLevel).id)
                    .put("ownerUserId", normalizedCardUserId(record.ownerUserId, record.ownerName))
                    .put("ownerName", record.ownerName.trim().ifBlank { DEFAULT_CARD_USER_NAME })
                    .put("contributorUserId", normalizedCardUserId(record.contributorUserId, record.contributorName))
                    .put("contributorName", record.contributorName.trim().ifBlank { DEFAULT_CARD_USER_NAME })
            )
        }
        return array
    }

    private fun Map<String, Set<String>>.toPackExclusionsJsonObject(): JSONObject =
        JSONObject().apply {
            cleanPackExclusions()
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                .forEach { (ownerUserId, packNames) ->
                    put(
                        ownerUserId,
                        JSONArray(packNames.sortedWith(String.CASE_INSENSITIVE_ORDER))
                    )
                }
        }

    private fun JSONObject.toPackExclusionsMap(): Map<String, Set<String>> {
        val result = mutableMapOf<String, Set<String>>()
        val keys = keys()
        while (keys.hasNext()) {
            val ownerUserId = keys.next().trim()
            if (ownerUserId.isBlank()) continue
            val packNames = optJSONArray(ownerUserId) ?: continue
            val cleanPackNames = (0 until packNames.length())
                .mapNotNull { index -> packNames.optString(index).trim().takeIf { it.isNotBlank() } }
                .toSet()
            if (cleanPackNames.isNotEmpty()) {
                result[ownerUserId] = cleanPackNames
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

    private companion object {
        const val KEY_PLAYERS = "players"
        const val KEY_PLAYER_STATS = "playerStats"
        const val KEY_CURRENT_PLAYER_INDEX = "currentPlayerIndex"
        const val KEY_MODE = "mode"
        const val KEY_INTENSITY = "intensity"
        const val KEY_AGE_GATE_ACCEPTED = "ageGateAccepted"
        const val KEY_SAFETY_ACCEPTED = "safetyNoticeAccepted"
        const val KEY_FIRST_RUN_SETUP_COMPLETED = "firstRunSetupCompleted"
        const val KEY_THEME_MODE = "themeMode"
        const val KEY_DYNAMIC_COLORS = "dynamicColors"
        const val KEY_DRINK_SINGULAR = "drinkSingular"
        const val KEY_DRINK_PLURAL = "drinkPlural"
        const val KEY_CARD_OWNER_USER_ID = "cardOwnerUserId"
        const val KEY_CARD_OWNER_NAME = "cardOwnerName"
        const val KEY_ACTIVE_CONTRIBUTOR_USER_ID = "activeContributorUserId"
        const val KEY_ACTIVE_CONTRIBUTOR_NAME = "activeContributorName"
        const val KEY_REMOTE_SYNC_ENDPOINT_URL = "remoteSyncEndpointUrl"
        const val KEY_REMOTE_SYNC_ACCESS_TOKEN = "remoteSyncAccessToken"
        const val KEY_REMOTE_SYNC_ROLE = "remoteSyncRole"
        const val KEY_EXCLUDED_PACK_NAMES = "excludedPackNames"
        const val KEY_EXCLUDED_PACK_NAMES_BY_OWNER = "excludedPackNamesByOwner"
        const val KEY_CUSTOM_CATEGORY_IDS = "customCategoryIds"
        const val KEY_ENABLED_QUESTION_LEVEL_IDS = "enabledQuestionLevelIds"
        const val KEY_CURRENT_CARD_ID = "currentCardId"
        const val KEY_CARD_VISIBLE = "cardVisible"
        const val KEY_DRAWN_IDS = "drawnCardIds"
        const val KEY_DRAW_HISTORY = "drawHistory"
        const val KEY_DRAWS = "drawsThisRound"
        const val KEY_DRINKS = "drinksThisRound"
        const val KEY_DECKS = "deckFinishedCount"
        const val KEY_LAST_TURN_CARD_ID = "lastTurnCardId"
        const val KEY_LAST_TURN_CARD_DRINKS = "lastTurnCardDrinks"
        const val KEY_LAST_TURN_PLAYER = "lastTurnPlayer"
        const val KEY_LAST_TURN_PLAYER_INDEX = "lastTurnPlayerIndex"
        const val NO_CARD_ID = -1L
    }
}
