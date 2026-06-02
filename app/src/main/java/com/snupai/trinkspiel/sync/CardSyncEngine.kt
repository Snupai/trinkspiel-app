package com.snupai.trinkspiel.sync

import com.snupai.trinkspiel.data.DrinkEntry
import com.snupai.trinkspiel.model.CardCategory
import com.snupai.trinkspiel.model.CardSyncStatus
import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_ID
import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_NAME
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.model.normalizedCardUserId
import com.snupai.trinkspiel.model.normalizedCardUserName

data class RemoteCardSnapshot(
    val remoteId: String,
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
    val updatedAtMillis: Long = 0L,
) {
    fun toLocalEntry(localId: Long = 0L): DrinkEntry =
        DrinkEntry(
            id = localId,
            text = text,
            drinks = drinks,
            category = category,
            packName = packName,
            isEnabled = isEnabled,
            isPendingReview = isPendingReview,
            questionLevel = questionLevel,
            ownerUserId = ownerUserId,
            ownerName = ownerName,
            contributorUserId = contributorUserId,
            contributorName = contributorName,
            remoteId = remoteId,
            updatedAtMillis = updatedAtMillis,
            syncStatus = CardSyncStatus.SYNCED.id,
        ).sanitizedForSync()

    companion object {
        fun fromEntry(entry: DrinkEntry): RemoteCardSnapshot? {
            val cleanEntry = entry.sanitizedForSync()
            val remoteId = cleanEntry.remoteId.takeIf { it.isNotBlank() } ?: return null
            return RemoteCardSnapshot(
                remoteId = remoteId,
                text = cleanEntry.text,
                drinks = cleanEntry.drinks,
                category = cleanEntry.category,
                packName = cleanEntry.packName,
                isEnabled = cleanEntry.isEnabled,
                isPendingReview = cleanEntry.isPendingReview,
                questionLevel = cleanEntry.questionLevel,
                ownerUserId = cleanEntry.ownerUserId,
                ownerName = cleanEntry.ownerName,
                contributorUserId = cleanEntry.contributorUserId,
                contributorName = cleanEntry.contributorName,
                updatedAtMillis = cleanEntry.updatedAtMillis,
            )
        }
    }
}

data class CardSyncPlan(
    val createRemote: List<DrinkEntry> = emptyList(),
    val updateRemote: List<DrinkEntry> = emptyList(),
    val insertLocal: List<DrinkEntry> = emptyList(),
    val updateLocal: List<DrinkEntry> = emptyList(),
    val markLocalSynced: List<DrinkEntry> = emptyList(),
    val conflicts: List<CardSyncConflict> = emptyList(),
    val skippedRemoteCards: Int = 0,
) {
    val hasWork: Boolean
        get() = createRemote.isNotEmpty() ||
            updateRemote.isNotEmpty() ||
            insertLocal.isNotEmpty() ||
            updateLocal.isNotEmpty() ||
            markLocalSynced.isNotEmpty() ||
            conflicts.isNotEmpty()

    val localApplyEntries: List<DrinkEntry>
        get() = insertLocal + updateLocal + markLocalSynced

    val remoteConflictReplacements: List<DrinkEntry>
        get() = remoteConflictReplacementsFor(conflicts.map { it.local.id }.toSet())

    fun remoteConflictReplacementsFor(localIds: Set<Long>): List<DrinkEntry> =
        conflicts
            .filter { it.local.id in localIds }
            .map { conflict ->
                conflict.remote.toLocalEntry(conflict.local.id)
            }
}

data class CardSyncConflict(
    val local: DrinkEntry,
    val remote: RemoteCardSnapshot,
    val reason: CardSyncConflictReason,
)

enum class CardSyncConflictReason {
    LOCAL_AND_REMOTE_CHANGED,
}

object CardSyncEngine {
    fun plan(
        localEntries: List<DrinkEntry>,
        remoteCards: List<RemoteCardSnapshot>,
        libraryOwnerUserId: String? = null,
    ): CardSyncPlan {
        val ownerFilter = libraryOwnerUserId?.trim()?.takeIf { it.isNotBlank() }
        val scopedLocalEntries = localEntries
            .map { it.sanitizedForSync() }
            .filter { entry -> ownerFilter == null || entry.ownerUserId == ownerFilter }
        val localByRemoteId = scopedLocalEntries
            .filter { it.remoteId.isNotBlank() }
            .associateBy { it.remoteId }
        val normalizedRemoteCards = remoteCards
            .mapNotNull { it.normalizedOrNull() }
            .filter { snapshot -> ownerFilter == null || snapshot.ownerUserId == ownerFilter }
        val remoteById = normalizedRemoteCards
            .groupBy { it.remoteId }
            .mapValues { (_, snapshots) -> snapshots.maxBy { it.updatedAtMillis } }
        val skippedRemoteCards = remoteCards.size - normalizedRemoteCards.size

        val createRemote = scopedLocalEntries
            .filter { it.remoteId.isBlank() && CardSyncStatus.fromId(it.syncStatus) != CardSyncStatus.SYNCED }
            .sortedBy { it.id }
        val updateRemote = scopedLocalEntries
            .filter { it.needsRemoteUpdate() }
            .toMutableList()
        val insertLocal = mutableListOf<DrinkEntry>()
        val updateLocal = mutableListOf<DrinkEntry>()
        val markLocalSynced = mutableListOf<DrinkEntry>()
        val conflicts = mutableListOf<CardSyncConflict>()

        remoteById.values.forEach { remote ->
            val local = localByRemoteId[remote.remoteId]
            if (local == null) {
                insertLocal += remote.toLocalEntry()
                return@forEach
            }

            val remoteEntry = remote.toLocalEntry(local.id)
            val sameContent = local.contentFingerprint() == remoteEntry.contentFingerprint()
            val localDirty = local.needsRemoteUpdate()
            when {
                sameContent -> {
                    if (CardSyncStatus.fromId(local.syncStatus) != CardSyncStatus.SYNCED ||
                        remote.updatedAtMillis > local.updatedAtMillis
                    ) {
                        markLocalSynced += remoteEntry
                    }
                }
                localDirty && remote.updatedAtMillis > local.updatedAtMillis -> {
                    conflicts += CardSyncConflict(
                        local = local,
                        remote = remote,
                        reason = CardSyncConflictReason.LOCAL_AND_REMOTE_CHANGED,
                    )
                }
                localDirty -> Unit
                remote.updatedAtMillis >= local.updatedAtMillis -> {
                    updateLocal += remoteEntry
                }
                else -> {
                    updateRemote += local.copy(syncStatus = CardSyncStatus.DIRTY.id)
                }
            }
        }

        val resolvedLocalIds = (
            conflicts.map { it.local.id } +
                updateLocal.map { it.id } +
                markLocalSynced.map { it.id }
            ).toSet()
        return CardSyncPlan(
            createRemote = createRemote.distinctBy { it.id },
            updateRemote = updateRemote
                .filterNot { it.id in resolvedLocalIds }
                .distinctBy { it.id },
            insertLocal = insertLocal.distinctBy { it.remoteId },
            updateLocal = updateLocal.distinctBy { it.id },
            markLocalSynced = markLocalSynced.distinctBy { it.id },
            conflicts = conflicts,
            skippedRemoteCards = skippedRemoteCards,
        )
    }
}

private fun RemoteCardSnapshot.normalizedOrNull(): RemoteCardSnapshot? {
    val remoteId = remoteId.trim()
    if (remoteId.isBlank()) return null
    val ownerName = normalizedCardUserName(ownerName)
    val contributorName = contributorName.trim().ifBlank { ownerName }
    return copy(
        remoteId = remoteId,
        text = text.trim(),
        drinks = drinks.coerceAtLeast(1),
        category = CardCategory.fromId(category).id,
        packName = packName.trim().ifBlank { "Eigene Karten" },
        questionLevel = QuestionLevel.fromId(questionLevel).id,
        ownerUserId = normalizedCardUserId(ownerUserId, ownerName),
        ownerName = ownerName,
        contributorUserId = normalizedCardUserId(contributorUserId, contributorName),
        contributorName = contributorName,
        updatedAtMillis = updatedAtMillis.coerceAtLeast(0L),
    )
}

private fun DrinkEntry.sanitizedForSync(): DrinkEntry {
    val ownerName = normalizedCardUserName(ownerName)
    val contributorName = contributorName.trim().ifBlank { ownerName }
    return copy(
        text = text.trim(),
        drinks = drinks.coerceAtLeast(1),
        category = CardCategory.fromId(category).id,
        packName = packName.trim().ifBlank { "Eigene Karten" },
        questionLevel = QuestionLevel.fromId(questionLevel).id,
        ownerUserId = normalizedCardUserId(ownerUserId, ownerName),
        ownerName = ownerName,
        contributorUserId = normalizedCardUserId(contributorUserId, contributorName),
        contributorName = contributorName,
        remoteId = remoteId.trim(),
        updatedAtMillis = updatedAtMillis.coerceAtLeast(0L),
        syncStatus = CardSyncStatus.fromId(syncStatus).id,
    )
}

private fun DrinkEntry.needsRemoteUpdate(): Boolean =
    remoteId.isNotBlank() && CardSyncStatus.fromId(syncStatus) == CardSyncStatus.DIRTY

private fun DrinkEntry.contentFingerprint(): String =
    listOf(
        text.trim(),
        drinks.coerceAtLeast(1).toString(),
        CardCategory.fromId(category).id,
        packName.trim().ifBlank { "Eigene Karten" },
        isEnabled.toString(),
        isPendingReview.toString(),
        QuestionLevel.fromId(questionLevel).id.toString(),
        ownerUserId,
        ownerName,
        contributorUserId,
        contributorName,
    ).joinToString("|")
