package com.snupai.trinkspiel.model

enum class CardCategory(val id: String, val label: String) {
    TRUTH("truth", "Wahrheit"),
    CHALLENGE("challenge", "Aufgabe"),
    RULE("rule", "Regel"),
    EVERYONE("everyone", "Alle"),
    DUEL("duel", "Duell"),
    MINI_GAME("mini_game", "Mini-Spiel"),
    SPICY("spicy", "Spicy");

    companion object {
        fun fromId(id: String): CardCategory =
            entries.firstOrNull { it.id == id } ?: CHALLENGE
    }
}

const val DEFAULT_CARD_USER_ID = "local"
const val DEFAULT_CARD_USER_NAME = "Lokal"

private val allowedImportedCardUserId = Regex("[A-Za-z0-9_.:-]+")

fun normalizedCardUserName(name: String): String =
    name.trim().ifBlank { DEFAULT_CARD_USER_NAME }

fun cardUserIdForName(name: String): String {
    val normalizedName = normalizedCardUserName(name)
    if (normalizedName.equals(DEFAULT_CARD_USER_NAME, ignoreCase = true)) {
        return DEFAULT_CARD_USER_ID
    }
    val hexName = normalizedName
        .lowercase()
        .toByteArray(Charsets.UTF_8)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return "local_user_$hexName"
}

fun normalizedCardUserId(id: String, displayName: String): String {
    val generatedId = cardUserIdForName(displayName)
    val cleanId = id.trim()
    return when {
        cleanId.isBlank() -> generatedId
        cleanId == DEFAULT_CARD_USER_ID && generatedId != DEFAULT_CARD_USER_ID -> generatedId
        cleanId.startsWith("local_user_") && cleanId != generatedId -> generatedId
        cleanId.matches(allowedImportedCardUserId) -> cleanId
        else -> generatedId
    }
}

fun shouldReviewContribution(ownerName: String, contributorName: String): Boolean {
    val cleanOwnerName = normalizedCardUserName(ownerName)
    val cleanContributorName = contributorName.trim().ifBlank { cleanOwnerName }
    return cardUserIdForName(cleanOwnerName) != cardUserIdForName(cleanContributorName)
}

fun shouldReviewContribution(
    ownerUserId: String,
    ownerName: String,
    contributorUserId: String,
    contributorName: String,
): Boolean {
    val cleanOwnerName = normalizedCardUserName(ownerName)
    val cleanContributorName = contributorName.trim().ifBlank { cleanOwnerName }
    return normalizedCardUserId(ownerUserId, cleanOwnerName) !=
        normalizedCardUserId(contributorUserId, cleanContributorName)
}

enum class QuestionLevel(
    val id: Int,
    val label: String,
    val shortLabel: String,
    val description: String,
) {
    LEVEL_1(
        id = 1,
        label = "1 Einfach",
        shortLabel = "L1",
        description = "Lockere Fragen und einfache Aufgaben.",
    ),
    LEVEL_2(
        id = 2,
        label = "2 Saufen",
        shortLabel = "L2",
        description = "Trinklastige Karten mit klaren Schlucken.",
    ),
    LEVEL_3(
        id = 3,
        label = "3 Fast ausziehen",
        shortLabel = "L3",
        description = "Viel Alkohol und sehr freizuegige Aufgaben.",
    );

    companion object {
        fun fromId(id: Int): QuestionLevel =
            entries.firstOrNull { it.id == id } ?: LEVEL_1

        fun fromDrinks(drinks: Int): QuestionLevel =
            when {
                drinks <= 1 -> LEVEL_1
                drinks <= 3 -> LEVEL_2
                else -> LEVEL_3
            }
    }
}

enum class DrinkIntensity(val id: String, val label: String, val maxDrinks: Int) {
    LOW("low", "Locker", 1),
    MEDIUM("medium", "Normal", 2),
    HIGH("high", "Chaos", Int.MAX_VALUE);

    companion object {
        fun fromId(id: String): DrinkIntensity =
            entries.firstOrNull { it.id == id } ?: MEDIUM
    }
}

enum class GameMode(
    val id: String,
    val label: String,
    val allowedCategories: Set<String>? = null
) {
    CHILL(
        id = "chill",
        label = "Chill",
        allowedCategories = setOf(
            CardCategory.TRUTH.id,
            CardCategory.CHALLENGE.id,
            CardCategory.EVERYONE.id,
        )
    ),
    CLASSIC(
        id = "classic",
        label = "Classic",
        allowedCategories = setOf(
            CardCategory.TRUTH.id,
            CardCategory.CHALLENGE.id,
            CardCategory.RULE.id,
            CardCategory.EVERYONE.id,
            CardCategory.DUEL.id,
            CardCategory.MINI_GAME.id,
        )
    ),
    COUPLES(
        id = "couples",
        label = "Couples",
        allowedCategories = setOf(
            CardCategory.TRUTH.id,
            CardCategory.CHALLENGE.id,
            CardCategory.DUEL.id,
            CardCategory.SPICY.id,
        )
    ),
    CHAOS(id = "chaos", label = "Chaos"),
    CUSTOM(id = "custom", label = "Custom");

    fun allows(category: String): Boolean =
        allowedCategories?.contains(category) ?: true

    companion object {
        fun fromId(id: String): GameMode =
            entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

enum class EntrySortMode(val label: String) {
    NEWEST("Neueste"),
    OLDEST("Älteste"),
    LEVEL("Stufe"),
    DRINKS_DESC("Meiste Schlucke"),
    DRINKS_ASC("Wenigste Schlucke"),
    CATEGORY("Kategorie"),
    USER("User"),
    SYNC("Sync"),
    TEXT("Text");
}

enum class CardSyncStatus(val id: String, val label: String) {
    LOCAL("local", "Lokal"),
    DIRTY("dirty", "Geändert"),
    SYNCED("synced", "Synchronisiert");

    companion object {
        fun fromId(id: String): CardSyncStatus =
            entries.firstOrNull { it.id == id } ?: LOCAL
    }
}

enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Hell"),
    DARK("dark", "Dunkel");

    companion object {
        fun fromId(id: String): ThemeMode =
            entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

enum class TeamOption(val id: String, val label: String) {
    SOLO("solo", "Solo"),
    TEAM_A("team_a", "Team A"),
    TEAM_B("team_b", "Team B"),
    TEAM_C("team_c", "Team C");

    companion object {
        fun fromId(id: String): TeamOption =
            entries.firstOrNull { it.id == id } ?: SOLO

        fun next(id: String): TeamOption {
            val currentIndex = entries.indexOf(fromId(id))
            return entries[(currentIndex + 1) % entries.size]
        }
    }
}

data class PlayerStats(
    val name: String,
    val team: TeamOption = TeamOption.SOLO,
    val points: Int = 0,
    val drinks: Int = 0,
)

data class TeamStats(
    val team: TeamOption,
    val points: Int,
    val drinks: Int,
)

data class CompletedTurn(
    val cardId: Long,
    val cardDrinks: Int,
    val playerName: String?,
    val previousPlayerIndex: Int,
)

data class DrawnCardRecord(
    val cardId: Long,
    val text: String,
    val drinks: Int,
    val category: String,
    val packName: String,
    val playerName: String?,
    val roundNumber: Int,
    val questionLevel: Int = QuestionLevel.LEVEL_1.id,
    val ownerUserId: String = DEFAULT_CARD_USER_ID,
    val ownerName: String = DEFAULT_CARD_USER_NAME,
    val contributorUserId: String = DEFAULT_CARD_USER_ID,
    val contributorName: String = DEFAULT_CARD_USER_NAME,
)

data class GameSettings(
    val players: List<String> = emptyList(),
    val playerStats: Map<String, PlayerStats> = emptyMap(),
    val currentPlayerIndex: Int = 0,
    val mode: GameMode = GameMode.CLASSIC,
    val intensity: DrinkIntensity = DrinkIntensity.MEDIUM,
    val ageGateAccepted: Boolean = false,
    val safetyNoticeAccepted: Boolean = false,
    val firstRunSetupCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColors: Boolean = false,
    val drinkSingular: String = "Schluck",
    val drinkPlural: String = "Schlucke",
    val cardOwnerUserId: String = DEFAULT_CARD_USER_ID,
    val cardOwnerName: String = DEFAULT_CARD_USER_NAME,
    val activeContributorUserId: String = DEFAULT_CARD_USER_ID,
    val activeContributorName: String = DEFAULT_CARD_USER_NAME,
    val excludedPackNames: Set<String> = emptySet(),
    val excludedPackNamesByOwner: Map<String, Set<String>> = emptyMap(),
    val customCategoryIds: Set<String> = CardCategory.entries.map { it.id }.toSet(),
    val enabledQuestionLevelIds: Set<Int> = QuestionLevel.entries.map { it.id }.toSet(),
) {
    val currentPlayer: String?
        get() = players.getOrNull(currentPlayerIndex.coerceIn(0, (players.size - 1).coerceAtLeast(0)))

    val requiresFirstRunSetup: Boolean
        get() = !ageGateAccepted || !safetyNoticeAccepted || !firstRunSetupCompleted

    fun drinkLabel(count: Int): String =
        if (count == 1) drinkSingular else drinkPlural

    fun isPackEnabled(packName: String): Boolean =
        packName.trim().let { it.isNotBlank() && it !in excludedPackNamesForActiveLibrary() }

    fun excludedPackNamesForActiveLibrary(): Set<String> =
        excludedPackNamesForOwner(cardOwnerUserId, cardOwnerName)

    fun excludedPackNamesForOwner(ownerUserId: String, ownerName: String): Set<String> {
        val ownerKey = normalizedCardUserId(ownerUserId, normalizedCardUserName(ownerName))
        val activeOwnerKey = normalizedCardUserId(cardOwnerUserId, normalizedCardUserName(cardOwnerName))
        return excludedPackNamesByOwner.cleanPackExclusions()[ownerKey]
            ?: if (ownerKey == activeOwnerKey) excludedPackNames.cleanPackNames() else emptySet()
    }

    fun withExcludedPackNamesForActiveLibrary(packNames: Set<String>): GameSettings {
        val normalized = withNormalizedPackExclusions()
        val ownerKey = normalized.cardOwnerUserId
        val cleanPackNames = packNames.cleanPackNames()
        val nextExclusionsByOwner = if (cleanPackNames.isEmpty()) {
            normalized.excludedPackNamesByOwner - ownerKey
        } else {
            normalized.excludedPackNamesByOwner + (ownerKey to cleanPackNames)
        }
        return normalized.copy(
            excludedPackNames = cleanPackNames,
            excludedPackNamesByOwner = nextExclusionsByOwner,
        )
    }

    fun withCardOwnerProfile(ownerUserId: String, ownerName: String): GameSettings {
        val normalized = withNormalizedPackExclusions()
        val cleanOwnerName = normalizedCardUserName(ownerName)
        val cleanOwnerUserId = normalizedCardUserId(ownerUserId, cleanOwnerName)
        return normalized.copy(
            cardOwnerUserId = cleanOwnerUserId,
            cardOwnerName = cleanOwnerName,
            excludedPackNames = normalized.excludedPackNamesForOwner(cleanOwnerUserId, cleanOwnerName),
        )
    }

    fun withNormalizedPackExclusions(): GameSettings {
        val activeOwnerKey = normalizedCardUserId(cardOwnerUserId, normalizedCardUserName(cardOwnerName))
        val cleanLegacyExclusions = excludedPackNames.cleanPackNames()
        val cleanExclusionsByOwner = excludedPackNamesByOwner.cleanPackExclusions()
        val activeExclusions = cleanExclusionsByOwner[activeOwnerKey] ?: cleanLegacyExclusions
        val nextExclusionsByOwner = if (activeExclusions.isEmpty()) {
            cleanExclusionsByOwner - activeOwnerKey
        } else {
            cleanExclusionsByOwner + (activeOwnerKey to activeExclusions)
        }
        return copy(
            excludedPackNames = activeExclusions,
            excludedPackNamesByOwner = nextExclusionsByOwner,
        )
    }

    fun isCategoryEnabled(category: String): Boolean =
        if (mode == GameMode.CUSTOM) {
            CardCategory.fromId(category).id in customCategoryIds
        } else {
            mode.allows(category)
        }

    fun isQuestionLevelEnabled(questionLevel: Int): Boolean =
        QuestionLevel.fromId(questionLevel).id in enabledQuestionLevelIds

    fun isCardInActiveLibrary(ownerUserId: String, ownerName: String): Boolean {
        val cleanActiveOwnerName = normalizedCardUserName(cardOwnerName)
        val cleanEntryOwnerName = normalizedCardUserName(ownerName)
        return normalizedCardUserId(cardOwnerUserId, cleanActiveOwnerName) ==
            normalizedCardUserId(ownerUserId, cleanEntryOwnerName)
    }

    val scoreboard: List<PlayerStats>
        get() = players.map { playerStats[it] ?: PlayerStats(name = it) }

    val teamScores: List<TeamStats>
        get() = scoreboard
            .filter { it.team != TeamOption.SOLO }
            .groupBy { it.team }
            .map { (team, players) ->
                TeamStats(
                    team = team,
                    points = players.sumOf { it.points },
                    drinks = players.sumOf { it.drinks },
                )
            }
            .sortedBy { it.team.ordinal }

    fun renamePlayer(index: Int, newName: String): GameSettings {
        val cleanName = newName.trim()
        if (cleanName.isBlank() || index !in players.indices) return this
        if (players.withIndex().any { it.index != index && it.value.equals(cleanName, ignoreCase = true) }) {
            return this
        }

        val oldName = players[index]
        val updatedPlayers = players.toMutableList().also { it[index] = cleanName }
        val existingStats = playerStats[oldName]
            ?: playerStats.values.firstOrNull { it.name.equals(oldName, ignoreCase = true) }
            ?: PlayerStats(name = oldName)
        val updatedStats = playerStats
            .filterKeys { !it.equals(oldName, ignoreCase = true) }
            .plus(cleanName to existingStats.copy(name = cleanName))

        return copy(players = updatedPlayers, playerStats = updatedStats)
    }

    fun undoCompletedTurn(turn: CompletedTurn): GameSettings {
        val restoredIndex = when {
            players.isEmpty() -> 0
            turn.previousPlayerIndex in players.indices -> turn.previousPlayerIndex
            currentPlayerIndex in players.indices -> currentPlayerIndex
            else -> 0
        }
        val player = turn.playerName?.let { completedBy ->
            players.firstOrNull { it.equals(completedBy, ignoreCase = true) }
        }
        val adjustedStats = if (player == null) {
            playerStats
        } else {
            val current = playerStats[player] ?: PlayerStats(name = player)
            playerStats + (player to current.copy(
                points = (current.points - 1).coerceAtLeast(0),
                drinks = (current.drinks - turn.cardDrinks.coerceAtLeast(0)).coerceAtLeast(0),
            ))
        }

        return copy(
            playerStats = adjustedStats,
            currentPlayerIndex = restoredIndex,
        )
    }
}

private fun Set<String>.cleanPackNames(): Set<String> =
    map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()

private fun Map<String, Set<String>>.cleanPackExclusions(): Map<String, Set<String>> =
    mapNotNull { (ownerUserId, packNames) ->
        val cleanOwnerUserId = ownerUserId.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val cleanPackNames = packNames.cleanPackNames()
        cleanOwnerUserId to cleanPackNames
    }
        .filter { (_, packNames) -> packNames.isNotEmpty() }
        .toMap()

data class SavedRoundState(
    val currentCardId: Long? = null,
    val isCardVisible: Boolean = false,
    val drawnCardIds: Set<Long> = emptySet(),
    val drawHistory: List<DrawnCardRecord> = emptyList(),
    val drawsThisRound: Int = 0,
    val drinksThisRound: Int = 0,
    val deckFinishedCount: Int = 0,
    val lastCompletedTurn: CompletedTurn? = null,
) {
    fun removeDeletedCards(cardIds: Set<Long>): SavedRoundState {
        if (cardIds.isEmpty()) return this
        val nextCurrentCardId = currentCardId?.takeUnless { it in cardIds }
        return copy(
            currentCardId = nextCurrentCardId,
            isCardVisible = isCardVisible && nextCurrentCardId != null,
            drawnCardIds = drawnCardIds - cardIds,
            drawHistory = drawHistory.filterNot { it.cardId in cardIds },
            lastCompletedTurn = lastCompletedTurn?.takeUnless { it.cardId in cardIds },
        )
    }

    fun skipVisibleCard(cardDrinks: Int): SavedRoundState =
        copy(
            currentCardId = null,
            isCardVisible = false,
            drinksThisRound = (drinksThisRound - cardDrinks.coerceAtLeast(0)).coerceAtLeast(0),
            lastCompletedTurn = null,
        )
}
