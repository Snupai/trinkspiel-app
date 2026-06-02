package com.snupai.trinkspiel.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.snupai.trinkspiel.data.BuiltInPack
import com.snupai.trinkspiel.data.BuiltInPacks
import com.snupai.trinkspiel.data.CardUserEntity
import com.snupai.trinkspiel.data.DrinkEntry
import com.snupai.trinkspiel.data.DrinkRepository
import com.snupai.trinkspiel.data.GamePreferences
import com.snupai.trinkspiel.data.PackTemplate
import com.snupai.trinkspiel.data.PackTemplates
import com.snupai.trinkspiel.model.CardCategory
import com.snupai.trinkspiel.model.CardSyncStatus
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
import com.snupai.trinkspiel.model.TeamStats
import com.snupai.trinkspiel.model.ThemeMode
import com.snupai.trinkspiel.model.cardUserIdForName
import com.snupai.trinkspiel.model.normalizedCardUserId
import com.snupai.trinkspiel.model.normalizedCardUserName
import com.snupai.trinkspiel.model.shouldReviewContribution
import com.snupai.trinkspiel.sync.CardSyncEngine
import com.snupai.trinkspiel.sync.CardSyncPlan
import com.snupai.trinkspiel.sync.HttpRemoteCardSyncApi
import com.snupai.trinkspiel.sync.RemoteCardPushOperation
import com.snupai.trinkspiel.sync.RemoteCardPushRequest
import com.snupai.trinkspiel.sync.RemoteCardSnapshot
import com.snupai.trinkspiel.sync.RemoteCardSyncApi
import com.snupai.trinkspiel.sync.RemoteLibraryMembership
import com.snupai.trinkspiel.sync.RemoteSyncConfig
import com.snupai.trinkspiel.sync.normalizedRemoteSyncRole
import com.snupai.trinkspiel.util.AppBackup
import com.snupai.trinkspiel.util.AppBackupData
import com.snupai.trinkspiel.util.BackendSyncInvite
import com.snupai.trinkspiel.util.CardSyncPackage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ImportSummary(
    val added: Int,
    val skippedDuplicates: Int,
    val updated: Int = 0,
)

data class CardImportPreview(
    val totalCards: Int,
    val newCards: Int,
    val updatedCards: Int = 0,
    val skippedCards: Int,
    val packNames: List<String>,
    val questionLevelCounts: Map<Int, Int> = emptyMap(),
    val contributorCounts: List<CardImportUserPreview> = emptyList(),
    val externalContributorCounts: List<CardImportUserPreview> = emptyList(),
    val pendingReviewCards: Int = 0,
) {
    val hasNewCards: Boolean
        get() = newCards > 0

    val hasCardChanges: Boolean
        get() = newCards > 0 || updatedCards > 0

    companion object {
        fun fromEntries(
            totalCards: Int,
            skippedCards: Int,
            sanitizedEntries: List<DrinkEntry>,
            uniqueEntries: List<DrinkEntry>,
            updatedEntries: List<DrinkEntry> = emptyList(),
        ): CardImportPreview {
            val changedEntries = uniqueEntries + updatedEntries
            return CardImportPreview(
                totalCards = totalCards,
                newCards = uniqueEntries.size,
                updatedCards = updatedEntries.size,
                skippedCards = skippedCards,
                packNames = sanitizedEntries
                    .map { it.packName }
                    .distinct()
                    .sortedWith(String.CASE_INSENSITIVE_ORDER),
                questionLevelCounts = changedEntries
                    .groupingBy { QuestionLevel.fromId(it.questionLevel).id }
                    .eachCount(),
                contributorCounts = changedEntries
                    .groupingBy { normalizedCardUserName(it.contributorName) }
                    .eachCount()
                    .map { (displayName, cardCount) ->
                        CardImportUserPreview(displayName, cardCount)
                    }
                    .sortedWith(
                        compareByDescending<CardImportUserPreview> { it.cardCount }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
                    ),
                externalContributorCounts = changedEntries
                    .filter { entry ->
                        shouldReviewContribution(
                            ownerUserId = entry.ownerUserId,
                            ownerName = entry.ownerName,
                            contributorUserId = entry.contributorUserId,
                            contributorName = entry.contributorName,
                        )
                    }
                    .groupingBy { normalizedCardUserName(it.contributorName) }
                    .eachCount()
                    .map { (displayName, cardCount) ->
                        CardImportUserPreview(displayName, cardCount)
                    }
                    .sortedWith(
                        compareByDescending<CardImportUserPreview> { it.cardCount }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
                    ),
                pendingReviewCards = changedEntries.count { it.isPendingReview },
            )
        }
    }
}

data class CardImportUserPreview(
    val displayName: String,
    val cardCount: Int,
)

data class BackupRestoreSummary(
    val cards: ImportSummary,
    val settingsRestored: Boolean,
    val cardUsersRestored: Int = 0,
)

data class CardSyncApplySummary(
    val remoteCreatesPending: Int,
    val remoteUpdatesPending: Int,
    val localInserted: Int,
    val localUpdated: Int,
    val localMarkedSynced: Int,
    val conflicts: Int,
    val conflictsResolved: Int = 0,
    val skippedRemoteCards: Int,
)

data class RemoteCardSyncRunSummary(
    val pulled: CardSyncApplySummary,
    val remoteCreated: Int,
    val remoteUpdated: Int,
) {
    val remoteChanged: Int
        get() = remoteCreated + remoteUpdated
}

data class RemoteCardSyncPreview(
    val remoteCards: List<RemoteCardSnapshot>,
    val plan: CardSyncPlan,
)

data class BackupImportPreview(
    val cards: CardImportPreview,
    val settingsFound: Boolean,
    val playersFound: Int,
    val cardUsersFound: Int = 0,
    val mode: GameMode?,
    val intensity: DrinkIntensity?,
) {
    val hasImportableContent: Boolean
        get() = cards.hasCardChanges || settingsFound || cardUsersFound > 0
}

data class DeckBlockerSummary(
    val otherLibraryCards: Int = 0,
    val pausedCards: Int = 0,
    val pendingReviewCards: Int = 0,
    val disabledPackCards: Int = 0,
    val modeOrCategoryCards: Int = 0,
    val questionLevelCards: Int = 0,
    val intensityCards: Int = 0,
) {
    val total: Int
        get() = otherLibraryCards +
            pausedCards +
            pendingReviewCards +
            disabledPackCards +
            modeOrCategoryCards +
            questionLevelCards +
            intensityCards
}

data class CardUserSummary(
    val userId: String,
    val displayName: String,
    val ownedCount: Int,
    val contributedCount: Int,
) {
    val totalCount: Int
        get() = ownedCount + contributedCount
}

data class UiState(
    val entries: List<DrinkEntry> = emptyList(),
    val playableEntries: List<DrinkEntry> = emptyList(),
    val currentCard: DrinkEntry? = null,
    val isCardVisible: Boolean = false,
    val drawnCardIds: Set<Long> = emptySet(),
    val drawHistory: List<DrawnCardRecord> = emptyList(),
    val drawsThisRound: Int = 0,
    val drinksThisRound: Int = 0,
    val deckFinishedCount: Int = 0,
    val players: List<String> = emptyList(),
    val playerStats: List<PlayerStats> = emptyList(),
    val teamStats: List<TeamStats> = emptyList(),
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
    val remoteSyncEndpointUrl: String = "",
    val remoteSyncAccessToken: String = "",
    val remoteSyncRole: String = "",
    val canUndoLastTurn: Boolean = false,
    val packNames: List<String> = emptyList(),
    val cardUsers: List<CardUserSummary> = emptyList(),
    val questionLevelCounts: Map<Int, Int> = emptyMap(),
    val syncStatusCounts: Map<String, Int> = emptyMap(),
    val excludedPackNames: Set<String> = emptySet(),
    val customCategoryIds: Set<String> = CardCategory.entries.map { it.id }.toSet(),
    val enabledQuestionLevelIds: Set<Int> = QuestionLevel.entries.map { it.id }.toSet(),
) {
    val remainingCards: Int
        get() = (playableEntries.size - drawnCardIds.size).coerceAtLeast(0)

    val activeLibraryEntryCount: Int
        get() = entries.count(::isActiveLibraryEntry)

    val currentPlayer: String?
        get() = players.getOrNull(currentPlayerIndex)

    val currentPlayerStats: PlayerStats?
        get() = playerStats.getOrNull(currentPlayerIndex)

    val syncOpenCount: Int
        get() = syncStatusCounts
            .filterKeys { CardSyncStatus.fromId(it) != CardSyncStatus.SYNCED }
            .values
            .sum()

    val requiresFirstRunSetup: Boolean
        get() = !ageGateAccepted || !safetyNoticeAccepted || !firstRunSetupCompleted

    val remoteSyncConfigured: Boolean
        get() = remoteSyncEndpointUrl.isNotBlank()

    fun drinkLabel(count: Int): String =
        if (count == 1) drinkSingular else drinkPlural

    fun isPackEnabled(packName: String): Boolean =
        packName !in excludedPackNames

    fun isCategoryEnabled(category: String): Boolean =
        if (mode == GameMode.CUSTOM) {
            CardCategory.fromId(category).id in customCategoryIds
        } else {
            mode.allows(category)
        }

    fun isCustomCategoryEnabled(category: CardCategory): Boolean =
        category.id in customCategoryIds

    fun isQuestionLevelEnabled(questionLevel: QuestionLevel): Boolean =
        questionLevel.id in enabledQuestionLevelIds

    val deckBlockers: DeckBlockerSummary
        get() {
            var pausedCards = 0
            var pendingReviewCards = 0
            var otherLibraryCards = 0
            var disabledPackCards = 0
            var modeOrCategoryCards = 0
            var questionLevelCards = 0
            var intensityCards = 0

            entries.forEach { entry ->
                when {
                    !isActiveLibraryEntry(entry) -> otherLibraryCards += 1
                    entry.isPendingReview -> pendingReviewCards += 1
                    !entry.isEnabled -> pausedCards += 1
                    !isPackEnabled(entry.packName) -> disabledPackCards += 1
                    !isCategoryEnabled(entry.category) -> modeOrCategoryCards += 1
                    !isQuestionLevelEnabled(QuestionLevel.fromId(entry.questionLevel)) -> questionLevelCards += 1
                    entry.drinks > intensity.maxDrinks -> intensityCards += 1
                }
            }

            return DeckBlockerSummary(
                otherLibraryCards = otherLibraryCards,
                pausedCards = pausedCards,
                pendingReviewCards = pendingReviewCards,
                disabledPackCards = disabledPackCards,
                modeOrCategoryCards = modeOrCategoryCards,
                questionLevelCards = questionLevelCards,
                intensityCards = intensityCards,
            )
        }

    fun emptyDeckHints(): List<String> {
        if (entries.isEmpty() || playableEntries.isNotEmpty()) return emptyList()
        val blockers = deckBlockers
        return buildList {
            if (blockers.otherLibraryCards > 0) {
                add(
                    "${cardCount(blockers.otherLibraryCards)} " +
                        "${belongsVerb(blockers.otherLibraryCards)} zu einer anderen Bibliothek."
                )
            }
            if (blockers.pausedCards > 0) {
                add("${cardCount(blockers.pausedCards)} ${isVerb(blockers.pausedCards)} pausiert.")
            }
            if (blockers.pendingReviewCards > 0) {
                add("${cardCount(blockers.pendingReviewCards)} ${isVerb(blockers.pendingReviewCards)} im Review.")
            }
            if (blockers.disabledPackCards > 0) {
                add(
                    "${cardCount(blockers.disabledPackCards)} " +
                        "${lieVerb(blockers.disabledPackCards)} in deaktivierten Packs."
                )
            }
            if (blockers.modeOrCategoryCards > 0) {
                add(
                    "${cardCount(blockers.modeOrCategoryCards)} " +
                        "${fitVerb(blockers.modeOrCategoryCards)} nicht zu Modus/Kategorien."
                )
            }
            if (blockers.questionLevelCards > 0) {
                add(
                    "${cardCount(blockers.questionLevelCards)} " +
                        "${isVerb(blockers.questionLevelCards)} durch Stufen deaktiviert."
                )
            }
            if (blockers.intensityCards > 0) {
                add(
                    "${cardCount(blockers.intensityCards)} " +
                        "${isVerb(blockers.intensityCards)} für ${intensity.label} zu stark."
                )
            }
            if (isEmpty()) {
                add("Aktiviere Karten oder lockere die Spieloptionen.")
            }
        }
    }

    private fun cardCount(count: Int): String =
        if (count == 1) "1 Karte" else "$count Karten"

    private fun fitVerb(count: Int): String =
        if (count == 1) "passt" else "passen"

    private fun isVerb(count: Int): String =
        if (count == 1) "ist" else "sind"

    private fun belongsVerb(count: Int): String =
        if (count == 1) "gehört" else "gehören"

    private fun lieVerb(count: Int): String =
        if (count == 1) "liegt" else "liegen"

    fun isActiveLibraryEntry(entry: DrinkEntry): Boolean =
        normalizedCardUserId(entry.ownerUserId, entry.ownerName) == cardOwnerUserId
}

class DrinkViewModel(
    private val repo: DrinkRepository,
    private val preferences: GamePreferences,
    private val remoteCardSyncApi: RemoteCardSyncApi = HttpRemoteCardSyncApi(),
) : ViewModel() {

    val builtInPacks: List<BuiltInPack> = BuiltInPacks.all
    val packTemplates: List<PackTemplate> = PackTemplates.all

    private val _roundState = MutableStateFlow(preferences.loadRoundState())
    private val _settings = MutableStateFlow(preferences.loadSettings())
    private val _remoteSyncConfig = MutableStateFlow(preferences.loadRemoteSyncConfig())

    val uiState: StateFlow<UiState> = combine(
        repo.entries,
        repo.cardUsers,
        _roundState,
        _settings,
        _remoteSyncConfig,
    ) { entries, cardUsers, round, settings, remoteSyncConfig ->
        val normalizedSettings = settings.normalized()
        val normalizedRemoteSyncConfig = remoteSyncConfig.normalized()
        val playableEntries = entries.filterPlayable(normalizedSettings)
        val activeLibraryEntries = entriesForActiveLibrary(entries, normalizedSettings)
        val packNames = activeLibraryEntries
            .map { it.packName }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
        val questionLevelCounts = questionLevelCountsFor(activeLibraryEntries)
        val syncStatusCounts = syncStatusCountsFor(activeLibraryEntries)
        val playableIds = playableEntries.map { it.id }.toSet()
        val validDrawnIds = round.drawnCardIds.intersect(playableIds)
        val currentCard = playableEntries.firstOrNull { it.id == round.currentCardId }

        UiState(
            entries = entries,
            playableEntries = playableEntries,
            currentCard = currentCard,
            isCardVisible = round.isCardVisible && currentCard != null,
            drawnCardIds = validDrawnIds,
            drawHistory = round.drawHistory,
            drawsThisRound = round.drawsThisRound,
            drinksThisRound = round.drinksThisRound,
            deckFinishedCount = round.deckFinishedCount,
            players = normalizedSettings.players,
            playerStats = normalizedSettings.scoreboard,
            teamStats = normalizedSettings.teamScores,
            currentPlayerIndex = normalizedSettings.currentPlayerIndex,
            mode = normalizedSettings.mode,
            intensity = normalizedSettings.intensity,
            ageGateAccepted = normalizedSettings.ageGateAccepted,
            safetyNoticeAccepted = normalizedSettings.safetyNoticeAccepted,
            firstRunSetupCompleted = normalizedSettings.firstRunSetupCompleted,
            themeMode = normalizedSettings.themeMode,
            dynamicColors = normalizedSettings.dynamicColors,
            drinkSingular = normalizedSettings.drinkSingular,
            drinkPlural = normalizedSettings.drinkPlural,
            cardOwnerUserId = normalizedSettings.cardOwnerUserId,
            cardOwnerName = normalizedSettings.cardOwnerName,
            activeContributorUserId = normalizedSettings.activeContributorUserId,
            activeContributorName = normalizedSettings.activeContributorName,
            remoteSyncEndpointUrl = normalizedRemoteSyncConfig.endpointUrl,
            remoteSyncAccessToken = normalizedRemoteSyncConfig.accessToken,
            remoteSyncRole = normalizedRemoteSyncConfig.role,
            canUndoLastTurn = round.lastCompletedTurn?.let { turn ->
                entries.any { it.id == turn.cardId }
            } == true,
            packNames = packNames,
            cardUsers = buildCardUserSummaries(
                entries = entries,
                savedUsers = cardUsers,
                settings = normalizedSettings,
            ),
            questionLevelCounts = questionLevelCounts,
            syncStatusCounts = syncStatusCounts,
            excludedPackNames = normalizedSettings.excludedPackNamesForActiveLibrary(),
            customCategoryIds = normalizedSettings.customCategoryIds,
            enabledQuestionLevelIds = normalizedSettings.enabledQuestionLevelIds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    suspend fun addEntry(
        text: String,
        drinks: Int,
        category: String,
        packName: String,
        isEnabled: Boolean = true,
        isPendingReview: Boolean = false,
        questionLevel: Int = QuestionLevel.fromDrinks(drinks).id,
        ownerName: String = DEFAULT_CARD_USER_NAME,
        contributorName: String = ownerName,
        ownerUserId: String = cardUserIdForName(ownerName),
        contributorUserId: String = cardUserIdForName(contributorName),
    ): Boolean {
        val entry = sanitizeEntry(
            DrinkEntry(
                text = text,
                drinks = drinks,
                category = category,
                packName = packName,
                isEnabled = isEnabled && !isPendingReview,
                isPendingReview = isPendingReview,
                questionLevel = questionLevel,
                ownerUserId = ownerUserId,
                ownerName = ownerName,
                contributorUserId = contributorUserId,
                contributorName = contributorName,
            )
        )
        if (entry.text.isBlank() || isDuplicate(entry)) return false
        repo.add(entry)
        val currentSettings = _settings.value.normalized()
        val cleanOwnerName = normalizedCardUserName(entry.ownerName)
        val cleanOwnerUserId = normalizedCardUserId(entry.ownerUserId, cleanOwnerName)
        val cleanContributorName = entry.contributorName.trim().ifBlank { cleanOwnerName }
        val cleanContributorUserId = normalizedCardUserId(entry.contributorUserId, cleanContributorName)
        val ownerChanged = cleanOwnerUserId != currentSettings.cardOwnerUserId
        updateSettings {
            it.withCardOwnerProfile(cleanOwnerUserId, cleanOwnerName).copy(
                activeContributorUserId = cleanContributorUserId,
                activeContributorName = cleanContributorName,
            )
        }
        if (ownerChanged) resetRoundForDeckChange()
        saveCardUserProfiles(
            CardUserEntity(cleanOwnerUserId, cleanOwnerName),
            CardUserEntity(cleanContributorUserId, cleanContributorName),
        )
        return true
    }

    suspend fun updateEntry(entry: DrinkEntry): Boolean {
        val sanitized = sanitizeEntry(entry)
        val currentSettings = _settings.value.normalized()
        val storedEntry = activeLibraryActionEntry(sanitized, currentSettings, uiState.value.entries) ?: return false
        if (sanitized.text.isBlank() || isDuplicate(sanitized, exceptId = sanitized.id)) return false
        repo.update(sanitized)
        val profileUpdate = cardProfileUpdateAfterEdit(currentSettings, storedEntry, sanitized)
        if (profileUpdate.hasChanges) {
            updateSettings {
                profileUpdate.settings
            }
            if (profileUpdate.ownerChanged) resetRoundForDeckChange()
        }
        return true
    }

    fun toggleEntryEnabled(entry: DrinkEntry) {
        val settings = _settings.value.normalized()
        val storedEntry = activeLibraryActionEntry(entry, settings, uiState.value.entries) ?: return
        viewModelScope.launch {
            val nextEnabled = if (storedEntry.isPendingReview) true else !storedEntry.isEnabled
            val updated = sanitizeEntry(
                storedEntry.copy(
                    isEnabled = nextEnabled,
                    isPendingReview = false,
                )
            )
            if (!updated.isEnabled) {
                updateRound { it.removeDeletedCards(setOf(updated.id)) }
            }
            repo.update(updated)
        }
    }

    suspend fun setEntriesEnabled(entries: List<DrinkEntry>, isEnabled: Boolean): Int {
        val settings = _settings.value.normalized()
        val changedEntries = entriesForEnabledChangeAction(
            entries = entries,
            settings = settings,
            existingEntries = uiState.value.entries,
            isEnabled = isEnabled,
        )
            .map {
                sanitizeEntry(
                    it.copy(
                        isEnabled = isEnabled,
                        isPendingReview = it.isPendingReview,
                    )
                )
            }
        if (changedEntries.isEmpty()) return 0

        if (!isEnabled) {
            updateRound { it.removeDeletedCards(changedEntries.map { entry -> entry.id }.toSet()) }
        }
        repo.updateAll(changedEntries)
        return changedEntries.size
    }

    suspend fun approveEntries(entries: List<DrinkEntry>): Int {
        val settings = _settings.value.normalized()
        val changedEntries = activeLibraryActionEntries(entries, settings, uiState.value.entries)
            .filter { it.isPendingReview }
            .map { sanitizeEntry(it.copy(isEnabled = true, isPendingReview = false)) }
        if (changedEntries.isEmpty()) return 0
        repo.updateAll(changedEntries)
        return changedEntries.size
    }

    suspend fun rejectReviewEntries(entries: List<DrinkEntry>): Int {
        val settings = _settings.value.normalized()
        val reviewEntries = activeLibraryActionEntries(entries, settings, uiState.value.entries)
            .filter { it.isPendingReview && it.id > 0L }
        if (reviewEntries.isEmpty()) return 0

        val config = _remoteSyncConfig.value.normalized()
        val remoteReviewIds = reviewEntries
            .map { it.remoteId.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (config.isConfigured && config.role == "admin" && remoteReviewIds.isNotEmpty()) {
            remoteCardSyncApi.deleteCards(
                config = config,
                ownerUserId = uiState.value.cardOwnerUserId,
                remoteIds = remoteReviewIds,
            )
        }

        val ids = reviewEntries.map { it.id }.toSet()
        val state = uiState.value
        val remainingPackNames = state.entries
            .filter { it.id !in ids }
            .filter { state.isActiveLibraryEntry(it) }
            .map { it.packName }
            .toSet()
        updateRound { it.removeDeletedCards(ids) }
        updateSettings { settings ->
            settings.withExcludedPackNamesForActiveLibrary(
                settings.excludedPackNamesForActiveLibrary().intersect(remainingPackNames)
            )
        }
        repo.deleteEntries(reviewEntries)
        return reviewEntries.size
    }

    suspend fun markRemoteEntriesSynced(entries: List<DrinkEntry>): Int {
        val settings = _settings.value.normalized()
        val syncedAtMillis = System.currentTimeMillis()
        val changedEntries = activeLibraryActionEntries(entries, settings, uiState.value.entries)
            .filter {
                it.remoteId.isNotBlank() &&
                    CardSyncStatus.fromId(it.syncStatus) != CardSyncStatus.SYNCED
            }
            .map {
                sanitizeEntry(
                    it.copy(
                        syncStatus = CardSyncStatus.SYNCED.id,
                        updatedAtMillis = maxOf(it.updatedAtMillis, syncedAtMillis),
                    )
                )
            }
        if (changedEntries.isEmpty()) return 0
        repo.replaceImported(changedEntries)
        return changedEntries.size
    }

    fun deleteEntry(entry: DrinkEntry) {
        val settings = _settings.value.normalized()
        val storedEntry = activeLibraryActionEntry(entry, settings, uiState.value.entries) ?: return
        viewModelScope.launch {
            updateRound { it.removeDeletedCards(setOf(storedEntry.id)) }
            repo.delete(storedEntry)
        }
    }

    fun deleteEntries(entries: List<DrinkEntry>) {
        val settings = _settings.value.normalized()
        val entriesToDelete = activeLibraryActionEntries(entries, settings, uiState.value.entries)
        val ids = entriesToDelete.map { it.id }.filter { it > 0L }.toSet()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val state = uiState.value
            val remainingPackNames = state.entries
                .filter { it.id !in ids }
                .filter { state.isActiveLibraryEntry(it) }
                .map { it.packName }
                .toSet()
            updateRound { it.removeDeletedCards(ids) }
            updateSettings { settings ->
                settings.withExcludedPackNamesForActiveLibrary(
                    settings.excludedPackNamesForActiveLibrary().intersect(remainingPackNames)
                )
            }
            repo.deleteEntries(entriesToDelete)
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            updateRound { SavedRoundState() }
            repo.deleteAll()
        }
    }

    fun deletePack(packName: String) {
        val cleanPackName = packName.trim()
        if (cleanPackName.isBlank()) return
        viewModelScope.launch {
            val state = uiState.value
            val deletedIds = state.entries
                .filter { it.packName == cleanPackName && state.isActiveLibraryEntry(it) }
                .map { it.id }
                .toSet()
            if (deletedIds.isEmpty()) return@launch
            updateRound { it.removeDeletedCards(deletedIds) }
            updateSettings {
                it.withExcludedPackNamesForActiveLibrary(
                    it.excludedPackNamesForActiveLibrary() - cleanPackName
                )
            }
            repo.deleteByPackNameForOwner(cleanPackName, state.cardOwnerUserId)
        }
    }

    fun pickRandom() {
        val state = uiState.value
        val list = state.playableEntries
        if (list.isEmpty()) return

        val round = _roundState.value
        val availableCards = list.filterNot { it.id in round.drawnCardIds }
        val completedDeck = availableCards.isEmpty()
        val deck = if (completedDeck) list else availableCards
        val baseDrawnIds = if (completedDeck) emptySet() else round.drawnCardIds
        val picked = deck.random()
        val nextRoundNumber = round.deckFinishedCount + if (completedDeck) 2 else 1
        val record = picked.toDrawnCardRecord(
            playerName = state.currentPlayer,
            roundNumber = nextRoundNumber,
        )

        updateRound {
            it.copy(
                currentCardId = picked.id,
                isCardVisible = true,
                drawnCardIds = baseDrawnIds + picked.id,
                drawHistory = (it.drawHistory + record).takeLast(MAX_DRAW_HISTORY),
                drawsThisRound = it.drawsThisRound + 1,
                drinksThisRound = it.drinksThisRound + picked.drinks,
                deckFinishedCount = it.deckFinishedCount + if (completedDeck) 1 else 0,
                lastCompletedTurn = null,
            )
        }
    }

    fun completeCard() {
        val state = uiState.value
        val card = uiState.value.currentCard
        val playerName = state.currentPlayer
        val previousPlayerIndex = state.currentPlayerIndex
        if (card != null) addResultToCurrentPlayer(card.drinks)
        updateRound {
            it.copy(
                currentCardId = null,
                isCardVisible = false,
                lastCompletedTurn = card?.let {
                    CompletedTurn(
                        cardId = it.id,
                        cardDrinks = it.drinks,
                        playerName = playerName,
                        previousPlayerIndex = previousPlayerIndex,
                    )
                }
            )
        }
        nextPlayer()
    }

    fun skipCurrentCard() {
        val card = uiState.value.currentCard ?: return
        updateRound { it.skipVisibleCard(card.drinks) }
    }

    fun undoLastTurn() {
        val turn = _roundState.value.lastCompletedTurn ?: return
        if (uiState.value.entries.none { it.id == turn.cardId }) {
            updateRound { it.copy(lastCompletedTurn = null) }
            return
        }

        updateSettings { it.undoCompletedTurn(turn) }
        updateRound {
            it.copy(
                currentCardId = turn.cardId,
                isCardVisible = true,
                drawnCardIds = it.drawnCardIds + turn.cardId,
                lastCompletedTurn = null,
            )
        }
    }

    fun resetDeck() {
        updateRound { SavedRoundState() }
    }

    fun addStarterPack() {
        viewModelScope.launch { addBuiltInPack("classic") }
    }

    fun completeFirstRunSetup(
        players: List<String>,
        mode: GameMode,
        intensity: DrinkIntensity,
        addStarterPack: Boolean,
    ) {
        completeFirstRunSetup(
            players = players,
            mode = mode,
            intensity = intensity,
            starterPackId = if (addStarterPack) "classic" else null,
        )
    }

    fun completeFirstRunSetup(
        players: List<String>,
        mode: GameMode,
        intensity: DrinkIntensity,
        starterPackId: String?,
    ) {
        viewModelScope.launch {
            updateSettings {
                it.copy(
                    players = players,
                    currentPlayerIndex = 0,
                    mode = mode,
                    intensity = intensity,
                    ageGateAccepted = true,
                    safetyNoticeAccepted = true,
                    firstRunSetupCompleted = true,
                )
            }
            updateRound { SavedRoundState() }
            if (starterPackId != null) {
                addBuiltInPack(starterPackId)
            }
        }
    }

    suspend fun addBuiltInPack(packId: String): ImportSummary {
        val pack = BuiltInPacks.find(packId) ?: return ImportSummary(0, 0)
        return addEntries(pack.entries)
    }

    suspend fun addAllBuiltInPacks(): ImportSummary =
        addEntries(BuiltInPacks.all.flatMap { it.entries })

    suspend fun addPackTemplate(templateId: String): ImportSummary {
        val template = PackTemplates.find(templateId) ?: return ImportSummary(0, 0)
        return addEntries(template.entries)
    }

    fun getAllEntries(): List<DrinkEntry> = uiState.value.entries

    fun exportBackupJson(): String =
        AppBackup.toJson(
            entries = uiState.value.entries,
            settings = _settings.value.normalized(),
            cardUsers = uiState.value.cardUsers.toCardUserEntities(),
        )

    fun exportCardSyncPackageJson(): String =
        CardSyncPackage.toJson(
            entries = uiState.value.entries,
            libraryOwnerUserId = uiState.value.cardOwnerUserId,
        )

    fun previewCardSync(remoteCards: List<RemoteCardSnapshot>): CardSyncPlan =
        CardSyncEngine.plan(
            localEntries = uiState.value.entries,
            remoteCards = remoteCards,
            libraryOwnerUserId = uiState.value.cardOwnerUserId,
        )

    suspend fun previewRemoteCardSync(): RemoteCardSyncPreview {
        val config = _remoteSyncConfig.value.normalized()
        require(config.isConfigured) { "Remote-Sync ist nicht konfiguriert" }
        val ownerUserId = uiState.value.cardOwnerUserId
        val remoteCards = remoteCardSyncApi.fetchCards(config, ownerUserId)
        return RemoteCardSyncPreview(
            remoteCards = remoteCards,
            plan = previewCardSync(remoteCards)
                .withRemoteWritesAllowed(config.role != "read"),
        )
    }

    suspend fun applyRemoteCardSync(
        remoteCards: List<RemoteCardSnapshot>,
        remoteConflictLocalIds: Set<Long> = emptySet(),
    ): CardSyncApplySummary {
        val plan = previewCardSync(remoteCards)
        return applyCardSyncPlan(plan, remoteConflictLocalIds)
    }

    private suspend fun applyCardSyncPlan(
        plan: CardSyncPlan,
        remoteConflictLocalIds: Set<Long>,
    ): CardSyncApplySummary {
        if (plan.insertLocal.isNotEmpty()) repo.addAll(plan.insertLocal)
        val remoteConflictReplacements = plan.remoteConflictReplacementsFor(remoteConflictLocalIds)
        val localReplacements = plan.updateLocal + plan.markLocalSynced + remoteConflictReplacements
        if (localReplacements.isNotEmpty()) repo.replaceImported(localReplacements)
        if (plan.localApplyEntries.isNotEmpty() || remoteConflictReplacements.isNotEmpty()) {
            updateRound { SavedRoundState() }
        }

        return CardSyncApplySummary(
            remoteCreatesPending = plan.createRemote.size,
            remoteUpdatesPending = plan.updateRemote.size,
            localInserted = plan.insertLocal.size,
            localUpdated = plan.updateLocal.size,
            localMarkedSynced = plan.markLocalSynced.size,
            conflicts = plan.conflicts.size,
            conflictsResolved = remoteConflictReplacements.size,
            skippedRemoteCards = plan.skippedRemoteCards,
        )
    }

    suspend fun syncCardsWithRemote(
        resolveConflictsWithRemote: Boolean = false,
    ): RemoteCardSyncRunSummary {
        val preview = previewRemoteCardSync()
        return syncCardsWithRemote(
            remoteCards = preview.remoteCards,
            remoteConflictLocalIds = if (resolveConflictsWithRemote) {
                preview.plan.conflicts.map { it.local.id }.toSet()
            } else {
                emptySet()
            },
        )
    }

    suspend fun syncCardsWithRemote(
        remoteCards: List<RemoteCardSnapshot>,
        remoteConflictLocalIds: Set<Long> = emptySet(),
    ): RemoteCardSyncRunSummary {
        val config = _remoteSyncConfig.value.normalized()
        require(config.isConfigured) { "Remote-Sync ist nicht konfiguriert" }
        val ownerUserId = uiState.value.cardOwnerUserId
        val plan = previewCardSync(remoteCards)
            .withRemoteWritesAllowed(config.role != "read")
        val pushRequests = plan.createRemote.map { entry ->
            RemoteCardPushRequest(
                clientLocalId = entry.id,
                operation = RemoteCardPushOperation.CREATE,
                entry = entry,
            )
        } + plan.updateRemote.map { entry ->
            RemoteCardPushRequest(
                clientLocalId = entry.id,
                operation = RemoteCardPushOperation.UPDATE,
                entry = entry,
            )
        }

        val pulled = applyCardSyncPlan(
            plan = plan,
            remoteConflictLocalIds = remoteConflictLocalIds,
        )
        val pushed = remoteCardSyncApi.pushCards(config, ownerUserId, pushRequests)
        val requestsByLocalId = pushRequests.associateBy { it.clientLocalId }
        val pushedLocalReplacements = pushed.mapNotNull { result ->
            val request = requestsByLocalId[result.clientLocalId] ?: return@mapNotNull null
            result.card.toLocalEntry(request.entry.id)
        }
        if (pushedLocalReplacements.isNotEmpty()) {
            repo.replaceImported(pushedLocalReplacements)
        }
        val pushedOperations = pushed.mapNotNull { result ->
            requestsByLocalId[result.clientLocalId]?.operation
        }
        return RemoteCardSyncRunSummary(
            pulled = pulled,
            remoteCreated = pushedOperations.count { it == RemoteCardPushOperation.CREATE },
            remoteUpdated = pushedOperations.count { it == RemoteCardPushOperation.UPDATE },
        )
    }

    private fun CardSyncPlan.withRemoteWritesAllowed(allowed: Boolean): CardSyncPlan =
        if (allowed) {
            this
        } else {
            copy(createRemote = emptyList(), updateRemote = emptyList())
        }

    fun previewBackupJson(json: String): BackupImportPreview {
        val backup = AppBackup.fromJson(json)
        val settings = backup.settings
        return BackupImportPreview(
            cards = previewCardImport(backup.cards, preserveUserMetadata = true),
            settingsFound = settings != null,
            playersFound = settings?.players?.size ?: 0,
            cardUsersFound = cardUserEntitiesForBackupRestore(backup).size,
            mode = settings?.mode,
            intensity = settings?.intensity,
        )
    }

    suspend fun restoreBackupJson(
        json: String,
        pauseImportedCards: Boolean = false,
    ): BackupRestoreSummary {
        val backup = AppBackup.fromJson(json)
        val cardSummary = addEntries(
            entries = backup.cards,
            preserveUserMetadata = true,
            pauseImportedCards = pauseImportedCards,
        )
        val settings = backup.settings

        if (settings != null) {
            updateSettings { settings }
            updateRound { SavedRoundState() }
        } else if (cardSummary.added > 0 || cardSummary.updated > 0) {
            updateRound { SavedRoundState() }
        }
        val restoredCardUsers = cardUserEntitiesForBackupRestore(backup)
        if (restoredCardUsers.isNotEmpty()) {
            repo.upsertCardUsers(restoredCardUsers)
        }

        return BackupRestoreSummary(
            cards = cardSummary,
            settingsRestored = settings != null,
            cardUsersRestored = restoredCardUsers.size,
        )
    }

    fun previewCardImport(
        entries: List<DrinkEntry>,
        preserveUserMetadata: Boolean = false,
    ): CardImportPreview {
        val prepared = prepareImport(entries, preserveUserMetadata)
        return CardImportPreview.fromEntries(
            totalCards = prepared.sanitizedEntries.size,
            skippedCards = prepared.skippedCards,
            sanitizedEntries = prepared.sanitizedEntries,
            uniqueEntries = prepared.uniqueEntries,
            updatedEntries = prepared.updatedEntries,
        )
    }

    suspend fun addEntries(
        entries: List<DrinkEntry>,
        preserveUserMetadata: Boolean = false,
        pauseImportedCards: Boolean = false,
    ): ImportSummary {
        val prepared = prepareImport(entries, preserveUserMetadata, pauseImportedCards)
        if (prepared.uniqueEntries.isNotEmpty()) repo.addAll(prepared.uniqueEntries)
        if (prepared.updatedEntries.isNotEmpty()) repo.replaceImported(prepared.updatedEntries)
        return ImportSummary(
            added = prepared.uniqueEntries.size,
            skippedDuplicates = prepared.skippedCards,
            updated = prepared.updatedEntries.size,
        )
    }

    private fun prepareImport(
        entries: List<DrinkEntry>,
        preserveUserMetadata: Boolean,
        pauseImportedCards: Boolean = false,
    ): PreparedImport {
        return prepareCardImport(
            entries = entries,
            existingEntries = uiState.value.entries,
            settings = _settings.value.normalized(),
            preserveUserMetadata = preserveUserMetadata,
            pauseImportedCards = pauseImportedCards,
        )
    }

    fun addPlayer(name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        updateSettings {
            if (it.players.any { player -> player.equals(cleanName, ignoreCase = true) }) {
                it
            } else {
                it.copy(players = it.players + cleanName).normalized()
            }
        }
    }

    fun removePlayer(index: Int) {
        updateSettings {
            if (index !in it.players.indices) return@updateSettings it
            val players = it.players.toMutableList().also { list -> list.removeAt(index) }
            it.copy(players = players, currentPlayerIndex = it.currentPlayerIndex.coerceAtMost(players.lastIndex.coerceAtLeast(0))).normalized()
        }
    }

    fun renamePlayer(index: Int, newName: String) {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return
        val settings = _settings.value.normalized()
        val oldName = settings.players.getOrNull(index) ?: return
        if (settings.players.withIndex().any { it.index != index && it.value.equals(cleanName, ignoreCase = true) }) {
            return
        }

        updateSettings { it.renamePlayer(index, cleanName) }
        updateRound { round ->
            round.copy(
                lastCompletedTurn = round.lastCompletedTurn?.let { turn ->
                    if (turn.playerName.equals(oldName, ignoreCase = true)) {
                        turn.copy(playerName = cleanName)
                    } else {
                        turn
                    }
                }
            )
        }
    }

    fun cyclePlayerTeam(index: Int) {
        updatePlayerStats(index) { stats ->
            stats.copy(team = TeamOption.next(stats.team.id))
        }
    }

    fun adjustPlayerPoints(index: Int, delta: Int) {
        updatePlayerStats(index) { stats ->
            stats.copy(points = (stats.points + delta).coerceAtLeast(0))
        }
    }

    fun adjustPlayerDrinks(index: Int, delta: Int) {
        updatePlayerStats(index) { stats ->
            stats.copy(drinks = (stats.drinks + delta).coerceAtLeast(0))
        }
    }

    fun selectPlayer(index: Int) {
        updateSettings {
            if (index in it.players.indices) it.copy(currentPlayerIndex = index) else it
        }
    }

    fun nextPlayer() {
        updateSettings {
            if (it.players.isEmpty()) {
                it
            } else {
                it.copy(currentPlayerIndex = (it.currentPlayerIndex + 1) % it.players.size)
            }
        }
    }

    fun selectMode(mode: GameMode) {
        updateSettings { it.copy(mode = mode) }
        resetRoundForDeckChange()
    }

    fun selectIntensity(intensity: DrinkIntensity) {
        updateSettings { it.copy(intensity = intensity) }
        resetRoundForDeckChange()
    }

    fun toggleQuestionLevel(level: QuestionLevel) {
        updateSettings { settings ->
            val nextLevels = if (level.id in settings.enabledQuestionLevelIds) {
                settings.enabledQuestionLevelIds - level.id
            } else {
                settings.enabledQuestionLevelIds + level.id
            }
            settings.copy(enabledQuestionLevelIds = nextLevels)
        }
        resetRoundForDeckChange()
    }

    fun togglePackEnabled(packName: String) {
        val cleanPackName = packName.trim()
        if (cleanPackName.isBlank()) return
        updateSettings { settings ->
            val activeExcludedPackNames = settings.excludedPackNamesForActiveLibrary()
            val excluded = if (cleanPackName in activeExcludedPackNames) {
                activeExcludedPackNames - cleanPackName
            } else {
                activeExcludedPackNames + cleanPackName
            }
            settings.withExcludedPackNamesForActiveLibrary(excluded)
        }
        resetRoundForDeckChange()
    }

    fun toggleCustomCategory(category: CardCategory) {
        updateSettings { settings ->
            val nextCategories = if (category.id in settings.customCategoryIds) {
                settings.customCategoryIds - category.id
            } else {
                settings.customCategoryIds + category.id
            }
            settings.copy(customCategoryIds = nextCategories)
        }
        resetRoundForDeckChange()
    }

    fun acceptSafetyNotice() {
        updateSettings { it.copy(safetyNoticeAccepted = true, ageGateAccepted = true) }
    }

    fun resetSafetyNotice() {
        updateSettings {
            it.copy(
                ageGateAccepted = false,
                safetyNoticeAccepted = false,
                firstRunSetupCompleted = false,
            )
        }
    }

    fun selectThemeMode(themeMode: ThemeMode) {
        updateSettings { it.copy(themeMode = themeMode) }
    }

    fun setDynamicColors(enabled: Boolean) {
        updateSettings { it.copy(dynamicColors = enabled) }
    }

    fun updateDrinkWording(singular: String, plural: String) {
        updateSettings {
            it.copy(
                drinkSingular = singular,
                drinkPlural = plural,
            )
        }
    }

    fun updateCardUserProfile(ownerName: String, contributorName: String) {
        val current = _settings.value.normalized()
        val cleanOwner = normalizedCardUserName(ownerName)
        val cleanContributor = contributorName.trim().ifBlank { cleanOwner }
        val ownerUserId = if (cleanOwner.equals(current.cardOwnerName, ignoreCase = true)) {
            current.cardOwnerUserId
        } else {
            cardUserIdForName(cleanOwner)
        }
        val contributorUserId = if (cleanContributor.equals(current.activeContributorName, ignoreCase = true)) {
            current.activeContributorUserId
        } else {
            cardUserIdForName(cleanContributor)
        }
        val cleanOwnerUserId = normalizedCardUserId(ownerUserId, cleanOwner)
        val cleanContributorUserId = normalizedCardUserId(contributorUserId, cleanContributor)
        val ownerChanged = cleanOwnerUserId != current.cardOwnerUserId
        updateSettings {
            it.withCardOwnerProfile(cleanOwnerUserId, cleanOwner).copy(
                activeContributorUserId = cleanContributorUserId,
                activeContributorName = cleanContributor,
            )
        }
        if (ownerChanged) resetRoundForDeckChange()
        saveCardUserProfiles(
            CardUserEntity(cleanOwnerUserId, cleanOwner),
            CardUserEntity(cleanContributorUserId, cleanContributor),
        )
    }

    fun selectCardOwnerProfile(userId: String, displayName: String) {
        val current = _settings.value.normalized()
        val cleanName = normalizedCardUserName(displayName)
        val cleanUserId = normalizedCardUserId(userId, cleanName)
        val ownerChanged = cleanUserId != current.cardOwnerUserId
        updateSettings {
            it.withCardOwnerProfile(cleanUserId, cleanName)
        }
        if (ownerChanged) resetRoundForDeckChange()
        saveCardUserProfiles(CardUserEntity(cleanUserId, cleanName))
    }

    fun selectActiveContributorProfile(userId: String, displayName: String) {
        val cleanName = normalizedCardUserName(displayName)
        val cleanUserId = normalizedCardUserId(userId, cleanName)
        updateSettings {
            it.copy(
                activeContributorUserId = cleanUserId,
                activeContributorName = cleanName,
            )
        }
        saveCardUserProfiles(CardUserEntity(cleanUserId, cleanName))
    }

    fun updateRemoteSyncConfig(endpointUrl: String, accessToken: String, role: String = "") {
        val next = RemoteSyncConfig(
            endpointUrl = endpointUrl,
            accessToken = accessToken,
            role = role,
        ).normalized()
        _remoteSyncConfig.value = next
        preferences.saveRemoteSyncConfig(next)
    }

    fun applyBackendSyncInvite(invite: BackendSyncInvite) {
        val current = _settings.value.normalized()
        val normalizedInvite = invite.normalized()
        val ownerChanged = normalizedInvite.libraryOwnerUserId != current.cardOwnerUserId
        updateRemoteSyncConfig(
            endpointUrl = normalizedInvite.endpointUrl,
            accessToken = normalizedInvite.accessToken,
            role = normalizedInvite.role,
        )
        updateSettings {
            it.copy(
                cardOwnerUserId = normalizedInvite.libraryOwnerUserId,
                cardOwnerName = normalizedInvite.libraryOwnerName,
                activeContributorUserId = normalizedInvite.contributorUserId,
                activeContributorName = normalizedInvite.contributorName,
            )
        }
        if (ownerChanged) resetRoundForDeckChange()
        saveCardUserProfiles(cardUserEntitiesForBackendInvite(normalizedInvite))
    }

    suspend fun createBackendSyncInvite(
        contributorName: String,
        role: String,
    ): BackendSyncInvite {
        val config = _remoteSyncConfig.value.normalized()
        require(config.isConfigured) { "Remote-Sync ist nicht konfiguriert" }
        require(config.accessToken.isNotBlank()) { "Admin-Token fehlt" }
        val state = uiState.value
        val cleanContributorName = contributorName.trim().ifBlank { state.activeContributorName }
        val invite = remoteCardSyncApi.createBackendInvite(
            config = config,
            ownerUserId = state.cardOwnerUserId,
            ownerName = state.cardOwnerName,
            contributorName = cleanContributorName,
            role = role.normalizedRemoteSyncRole().ifBlank { "write" },
        )
        val normalizedBackendInvite = BackendSyncInvite(
            endpointUrl = invite.endpointUrl.ifBlank { config.endpointUrl },
            accessToken = invite.accessToken,
            libraryOwnerUserId = invite.libraryOwnerUserId,
            libraryOwnerName = invite.libraryOwnerName.ifBlank { state.cardOwnerName },
            contributorUserId = invite.contributorUserId,
            contributorName = invite.contributorName.ifBlank { cleanContributorName },
            role = invite.role,
        ).normalized()
        saveCardUserProfiles(cardUserEntitiesForBackendInvite(normalizedBackendInvite))
        return normalizedBackendInvite
    }

    suspend fun fetchRemoteLibraryMemberships(): List<RemoteLibraryMembership> {
        val config = _remoteSyncConfig.value.normalized()
        require(config.isConfigured) { "Remote-Sync ist nicht konfiguriert" }
        require(config.accessToken.isNotBlank()) { "Admin-Token fehlt" }
        return remoteCardSyncApi.fetchLibraryMemberships(
            config = config,
            ownerUserId = uiState.value.cardOwnerUserId,
        )
    }

    suspend fun revokeRemoteLibraryMembership(tokenId: String): Boolean {
        val config = _remoteSyncConfig.value.normalized()
        require(config.isConfigured) { "Remote-Sync ist nicht konfiguriert" }
        require(config.accessToken.isNotBlank()) { "Admin-Token fehlt" }
        require(tokenId.trim().isNotBlank()) { "Membership-ID fehlt" }
        return remoteCardSyncApi.revokeLibraryMembership(
            config = config,
            ownerUserId = uiState.value.cardOwnerUserId,
            tokenId = tokenId,
        )
    }

    fun clearPlayers() {
        updateSettings { it.copy(players = emptyList(), playerStats = emptyMap(), currentPlayerIndex = 0) }
    }

    fun resetScores() {
        updateSettings {
            it.copy(
                playerStats = it.scoreboard.associate { stats ->
                    stats.name to stats.copy(points = 0, drinks = 0)
                }
            )
        }
    }

    private fun updateRound(transform: (SavedRoundState) -> SavedRoundState) {
        val next = transform(_roundState.value)
        _roundState.value = next
        preferences.saveRoundState(next)
    }

    private fun resetRoundForDeckChange() {
        updateRound {
            it.copy(
                drawnCardIds = emptySet(),
                currentCardId = null,
                isCardVisible = false,
                lastCompletedTurn = null,
            )
        }
    }

    private fun updateSettings(transform: (GameSettings) -> GameSettings) {
        val next = transform(_settings.value).normalized()
        _settings.value = next
        preferences.saveSettings(next)
    }

    private fun addResultToCurrentPlayer(drinks: Int) {
        val index = _settings.value.normalized().currentPlayerIndex
        updatePlayerStats(index) { stats ->
            stats.copy(
                points = stats.points + 1,
                drinks = stats.drinks + drinks.coerceAtLeast(0),
            )
        }
    }

    private fun updatePlayerStats(index: Int, transform: (PlayerStats) -> PlayerStats) {
        updateSettings { settings ->
            val normalized = settings.normalized()
            val player = normalized.players.getOrNull(index) ?: return@updateSettings normalized
            val current = normalized.playerStats[player] ?: PlayerStats(name = player)
            normalized.copy(
                playerStats = normalized.playerStats + (player to transform(current).copy(name = player))
            )
        }
    }

    private fun List<DrinkEntry>.filterPlayable(settings: GameSettings): List<DrinkEntry> =
        filter { entry ->
            entry.isEnabled &&
                !entry.isPendingReview &&
                settings.isCardInActiveLibrary(entry.ownerUserId, entry.ownerName) &&
                settings.isCategoryEnabled(entry.category) &&
                settings.isQuestionLevelEnabled(entry.questionLevel) &&
                entry.drinks <= settings.intensity.maxDrinks &&
                settings.isPackEnabled(entry.packName)
        }

    private fun sanitizeEntry(entry: DrinkEntry): DrinkEntry {
        return entry.sanitizedCardEntry()
    }

    private fun isDuplicate(entry: DrinkEntry, exceptId: Long? = null): Boolean =
        uiState.value.entries.any { existing ->
            existing.id != exceptId && duplicateKey(existing) == duplicateKey(entry)
        }

    private fun duplicateKey(entry: DrinkEntry): String =
        duplicateCardKey(entry)

    private fun DrinkEntry.toDrawnCardRecord(
        playerName: String?,
        roundNumber: Int,
    ): DrawnCardRecord =
        DrawnCardRecord(
            cardId = id,
            text = text,
            drinks = drinks.coerceAtLeast(1),
            category = CardCategory.fromId(category).id,
            packName = packName,
            playerName = playerName,
            roundNumber = roundNumber.coerceAtLeast(1),
            questionLevel = QuestionLevel.fromId(questionLevel).id,
            ownerUserId = ownerUserId,
            ownerName = ownerName,
            contributorUserId = contributorUserId,
            contributorName = contributorName,
        )

    private fun GameSettings.normalized(): GameSettings {
        val players = players
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        val currentIndex = when {
            players.isEmpty() -> 0
            currentPlayerIndex in players.indices -> currentPlayerIndex
            else -> 0
        }
        val byName = playerStats.values.associateBy { it.name.lowercase() }
        val normalizedStats = players.associateWith { player ->
            val existing = playerStats[player] ?: byName[player.lowercase()]
            PlayerStats(
                name = player,
                team = existing?.team ?: TeamOption.SOLO,
                points = existing?.points?.coerceAtLeast(0) ?: 0,
                drinks = existing?.drinks?.coerceAtLeast(0) ?: 0,
            )
        }
        val cleanOwnerName = normalizedCardUserName(cardOwnerName)
        val cleanContributorName = activeContributorName.trim().ifBlank { cleanOwnerName }
        return copy(
            players = players,
            playerStats = normalizedStats,
            currentPlayerIndex = currentIndex,
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
            excludedPackNamesByOwner = excludedPackNamesByOwner
                .mapNotNull { (ownerUserId, packNames) ->
                    val cleanOwnerUserId = ownerUserId.trim().takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    val cleanPackNames = packNames
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .toSet()
                    cleanOwnerUserId to cleanPackNames
                }
                .filter { (_, packNames) -> packNames.isNotEmpty() }
                .toMap(),
            customCategoryIds = customCategoryIds
                .map { CardCategory.fromId(it).id }
                .toSet(),
            enabledQuestionLevelIds = enabledQuestionLevelIds
                .mapNotNull { id -> QuestionLevel.entries.firstOrNull { it.id == id }?.id }
                .toSet(),
            ).withNormalizedPackExclusions()
    }

    private companion object {
        const val MAX_DRAW_HISTORY = 120
    }

    private fun saveCardUserProfiles(vararg users: CardUserEntity) {
        saveCardUserProfiles(users.toList())
    }

    private fun saveCardUserProfiles(users: List<CardUserEntity>) {
        val normalizedUsers = users
            .map { user ->
                val displayName = normalizedCardUserName(user.displayName)
                CardUserEntity(
                    id = normalizedCardUserId(user.id, displayName),
                    displayName = displayName,
                )
            }
            .distinctBy { it.id }
        if (normalizedUsers.isEmpty()) return
        viewModelScope.launch {
            repo.upsertCardUsers(normalizedUsers)
        }
    }
}

internal fun cardUserEntitiesForBackendInvite(invite: BackendSyncInvite): List<CardUserEntity> {
    val normalizedInvite = invite.normalized()
    return listOf(
        CardUserEntity(
            id = normalizedCardUserId(
                normalizedInvite.libraryOwnerUserId,
                normalizedInvite.libraryOwnerName,
            ),
            displayName = normalizedCardUserName(normalizedInvite.libraryOwnerName),
        ),
        CardUserEntity(
            id = normalizedCardUserId(
                normalizedInvite.contributorUserId,
                normalizedInvite.contributorName,
            ),
            displayName = normalizedCardUserName(normalizedInvite.contributorName),
        ),
    ).distinctBy { it.id }
}

internal fun cardUserEntitiesForBackupRestore(backup: AppBackupData): List<CardUserEntity> {
    val settingsUsers = backup.settings?.let { settings ->
        listOf(
            CardUserEntity(settings.cardOwnerUserId, settings.cardOwnerName),
            CardUserEntity(settings.activeContributorUserId, settings.activeContributorName),
        )
    }.orEmpty()
    val entryUsers = backup.cards.flatMap { entry ->
        listOf(
            CardUserEntity(entry.ownerUserId, entry.ownerName),
            CardUserEntity(entry.contributorUserId, entry.contributorName),
        )
    }
    return (backup.cardUsers + settingsUsers + entryUsers)
        .map { user ->
            val displayName = normalizedCardUserName(user.displayName)
            CardUserEntity(
                id = normalizedCardUserId(user.id, displayName),
                displayName = displayName,
            )
        }
        .distinctBy { it.id }
}

internal fun buildCardUserSummaries(
    entries: List<DrinkEntry>,
    savedUsers: List<CardUserEntity>,
    settings: GameSettings,
): List<CardUserSummary> {
    val settingUsers = listOf(
        CardUserEntity(settings.cardOwnerUserId, settings.cardOwnerName),
        CardUserEntity(settings.activeContributorUserId, settings.activeContributorName),
    )
    val allSavedUsers = savedUsers + settingUsers
    val displayNamesById = allSavedUsers
        .map { user ->
            val displayName = normalizedCardUserName(user.displayName)
            normalizedCardUserId(user.id, displayName) to displayName
        }
        .toMap()
    val entryNamesById = buildMap<String, String> {
        entries.forEach { entry ->
            val ownerName = normalizedCardUserName(entry.ownerName)
            val contributorName = normalizedCardUserName(entry.contributorName)
            putIfAbsent(normalizedCardUserId(entry.ownerUserId, ownerName), ownerName)
            putIfAbsent(normalizedCardUserId(entry.contributorUserId, contributorName), contributorName)
        }
    }
    val ownedCounts = entries.groupingBy { entry ->
        normalizedCardUserId(entry.ownerUserId, entry.ownerName)
    }.eachCount()
    val contributedCounts = entries.groupingBy { entry ->
        normalizedCardUserId(entry.contributorUserId, entry.contributorName)
    }.eachCount()
    val userIds = (displayNamesById.keys + entryNamesById.keys + ownedCounts.keys + contributedCounts.keys)
        .filter { it.isNotBlank() }
        .toSet()

    return userIds
        .map { userId ->
            CardUserSummary(
                userId = userId,
                displayName = displayNamesById[userId]
                    ?: entryNamesById[userId]
                    ?: DEFAULT_CARD_USER_NAME,
                ownedCount = ownedCounts[userId] ?: 0,
                contributedCount = contributedCounts[userId] ?: 0,
            )
        }
        .sortedWith(
            compareByDescending<CardUserSummary> { it.totalCount }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
        )
}

internal fun entriesForActiveLibrary(
    entries: List<DrinkEntry>,
    settings: GameSettings,
): List<DrinkEntry> =
    entries.filter { entry ->
        settings.isCardInActiveLibrary(entry.ownerUserId, entry.ownerName)
    }

internal fun questionLevelCountsFor(entries: List<DrinkEntry>): Map<Int, Int> =
    entries
        .groupingBy { QuestionLevel.fromId(it.questionLevel).id }
        .eachCount()

internal fun syncStatusCountsFor(entries: List<DrinkEntry>): Map<String, Int> =
    entries
        .groupingBy { CardSyncStatus.fromId(it.syncStatus).id }
        .eachCount()

internal fun activeLibraryActionEntries(
    entries: List<DrinkEntry>,
    settings: GameSettings,
    existingEntries: List<DrinkEntry> = entries,
): List<DrinkEntry> =
    entries
        .map { it.id }
        .filter { it > 0L }
        .toSet()
        .let { requestedIds ->
            existingEntries.filter { entry ->
                entry.id in requestedIds &&
                    settings.isCardInActiveLibrary(entry.ownerUserId, entry.ownerName)
            }
        }

internal fun activeLibraryActionEntry(
    entry: DrinkEntry,
    settings: GameSettings,
    existingEntries: List<DrinkEntry>,
): DrinkEntry? =
    activeLibraryActionEntries(listOf(entry), settings, existingEntries).firstOrNull()

internal fun entriesForEnabledChangeAction(
    entries: List<DrinkEntry>,
    settings: GameSettings,
    existingEntries: List<DrinkEntry>,
    isEnabled: Boolean,
): List<DrinkEntry> =
    activeLibraryActionEntries(entries, settings, existingEntries)
        .filter { entry ->
            !entry.isPendingReview && entry.isEnabled != isEnabled
        }

internal fun entryCanBeUpdatedFromActiveLibrary(
    existingEntries: List<DrinkEntry>,
    candidate: DrinkEntry,
    settings: GameSettings,
): Boolean {
    if (candidate.id <= 0L) return false
    val existingEntry = existingEntries.firstOrNull { it.id == candidate.id } ?: return false
    return settings.isCardInActiveLibrary(existingEntry.ownerUserId, existingEntry.ownerName)
}

internal data class CardProfileUpdate(
    val settings: GameSettings,
    val ownerChanged: Boolean,
    val contributorChanged: Boolean,
) {
    val hasChanges: Boolean
        get() = ownerChanged || contributorChanged
}

internal fun cardProfileUpdateAfterEdit(
    currentSettings: GameSettings,
    storedEntry: DrinkEntry,
    editedEntry: DrinkEntry,
): CardProfileUpdate {
    val cleanOwnerName = normalizedCardUserName(editedEntry.ownerName)
    val cleanOwnerUserId = normalizedCardUserId(editedEntry.ownerUserId, cleanOwnerName)
    val cleanContributorName = editedEntry.contributorName.trim().ifBlank { cleanOwnerName }
    val cleanContributorUserId = normalizedCardUserId(editedEntry.contributorUserId, cleanContributorName)

    val activeOwnerName = normalizedCardUserName(currentSettings.cardOwnerName)
    val activeOwnerUserId = normalizedCardUserId(currentSettings.cardOwnerUserId, activeOwnerName)
    val storedOwnerName = normalizedCardUserName(storedEntry.ownerName)
    val storedContributorName = storedEntry.contributorName.trim().ifBlank { storedOwnerName }
    val storedContributorUserId = normalizedCardUserId(storedEntry.contributorUserId, storedContributorName)

    val ownerChanged = cleanOwnerUserId != activeOwnerUserId
    val contributorChanged = cleanContributorUserId != storedContributorUserId ||
        cleanContributorName != storedContributorName
    val ownerSettings = if (ownerChanged) {
        currentSettings.withCardOwnerProfile(cleanOwnerUserId, cleanOwnerName)
    } else {
        currentSettings
    }
    val nextSettings = if (ownerChanged || contributorChanged) {
        ownerSettings.copy(
            activeContributorUserId = cleanContributorUserId,
            activeContributorName = cleanContributorName,
        )
    } else {
        currentSettings
    }
    return CardProfileUpdate(
        settings = nextSettings,
        ownerChanged = ownerChanged,
        contributorChanged = contributorChanged,
    )
}

private fun List<CardUserSummary>.toCardUserEntities(): List<CardUserEntity> =
    map { summary ->
        CardUserEntity(summary.userId, summary.displayName)
    }

class DrinkViewModelFactory(
    private val repo: DrinkRepository,
    private val preferences: GamePreferences,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DrinkViewModel(repo, preferences) as T
}
