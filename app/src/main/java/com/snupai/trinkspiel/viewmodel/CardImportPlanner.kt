package com.snupai.trinkspiel.viewmodel

import com.snupai.trinkspiel.data.DrinkEntry
import com.snupai.trinkspiel.model.CardCategory
import com.snupai.trinkspiel.model.CardSyncStatus
import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_NAME
import com.snupai.trinkspiel.model.GameSettings
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.model.normalizedCardUserId
import com.snupai.trinkspiel.model.normalizedCardUserName
import com.snupai.trinkspiel.model.shouldReviewContribution

internal data class PreparedImport(
    val sanitizedEntries: List<DrinkEntry>,
    val uniqueEntries: List<DrinkEntry>,
    val updatedEntries: List<DrinkEntry>,
    val skippedCards: Int,
) {
    val hasCardChanges: Boolean
        get() = uniqueEntries.isNotEmpty() || updatedEntries.isNotEmpty()
}

internal fun prepareCardImport(
    entries: List<DrinkEntry>,
    existingEntries: List<DrinkEntry>,
    settings: GameSettings,
    preserveUserMetadata: Boolean,
    pauseImportedCards: Boolean = false,
): PreparedImport {
    val normalizedSettings = settings.normalizedCardUserSettings()
    val existingTextKeys = existingEntries.map(::duplicateCardKey).toMutableSet()
    val existingByRemoteId = existingEntries
        .filter { it.remoteId.isNotBlank() }
        .associateBy(::remoteDuplicateKey)
    val touchedExistingIds = mutableSetOf<Long>()
    val uniqueEntries = mutableListOf<DrinkEntry>()
    val updatedEntries = mutableListOf<DrinkEntry>()
    var skippedCards = 0

    val sanitizedEntries = entries
        .map { entry -> entry.withImportUserMetadata(preserveUserMetadata, normalizedSettings) }
        .map { entry -> entry.withImportReviewState(preserveUserMetadata, pauseImportedCards) }
        .map { it.sanitizedCardEntry() }

    sanitizedEntries.forEach { entry ->
        if (entry.text.isBlank()) {
            skippedCards += 1
            return@forEach
        }

        val existingRemoteEntry = if (entry.remoteId.trim().isNotBlank()) {
            existingByRemoteId[remoteDuplicateKey(entry)]
        } else {
            null
        }
        if (existingRemoteEntry != null) {
            if (existingRemoteEntry.id in touchedExistingIds || !entry.shouldReplaceRemote(existingRemoteEntry)) {
                skippedCards += 1
            } else {
                touchedExistingIds += existingRemoteEntry.id
                val updatedEntry = entry.copy(id = existingRemoteEntry.id)
                updatedEntries += updatedEntry
                existingTextKeys += duplicateCardKey(updatedEntry)
            }
            return@forEach
        }

        val key = duplicateCardKey(entry)
        if (key in existingTextKeys) {
            skippedCards += 1
        } else {
            existingTextKeys += key
            uniqueEntries += entry.copy(id = 0)
        }
    }

    return PreparedImport(
        sanitizedEntries = sanitizedEntries,
        uniqueEntries = uniqueEntries,
        updatedEntries = updatedEntries,
        skippedCards = skippedCards,
    )
}

internal fun DrinkEntry.sanitizedCardEntry(): DrinkEntry {
    val ownerName = normalizedCardUserName(ownerName)
    val contributorName = contributorName.trim().ifBlank { ownerName }
    return copy(
        text = text.trim(),
        drinks = drinks.coerceAtLeast(1),
        category = CardCategory.fromId(category).id,
        packName = packName.trim().ifBlank { "Eigene Karten" },
        isPendingReview = isPendingReview,
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

internal fun duplicateCardKey(entry: DrinkEntry): String =
    "${entry.ownerDuplicateKey()}|${entry.text.trim().lowercase()}"

private fun remoteDuplicateKey(entry: DrinkEntry): String =
    "${entry.ownerDuplicateKey()}|${entry.remoteId.trim()}"

private fun DrinkEntry.ownerDuplicateKey(): String {
    val ownerName = normalizedCardUserName(ownerName)
    return normalizedCardUserId(ownerUserId, ownerName)
}

private fun GameSettings.normalizedCardUserSettings(): GameSettings {
    val cleanOwnerName = normalizedCardUserName(cardOwnerName)
    val cleanContributorName = activeContributorName.trim().ifBlank { cleanOwnerName }
    return copy(
        cardOwnerUserId = normalizedCardUserId(cardOwnerUserId, cleanOwnerName),
        cardOwnerName = cleanOwnerName,
        activeContributorUserId = normalizedCardUserId(activeContributorUserId, cleanContributorName),
        activeContributorName = cleanContributorName,
    )
}

private fun DrinkEntry.withImportUserMetadata(
    preserveUserMetadata: Boolean,
    settings: GameSettings,
): DrinkEntry {
    if (preserveUserMetadata) return this
    val importedContributorName = contributorName.cardUserOrNull()
        ?: ownerName.cardUserOrNull()
        ?: settings.activeContributorName
    val importedContributorUserId = when {
        contributorName.cardUserOrNull() != null -> normalizedCardUserId(contributorUserId, contributorName)
        ownerName.cardUserOrNull() != null -> normalizedCardUserId(ownerUserId, ownerName)
        else -> settings.activeContributorUserId
    }
    return copy(
        ownerUserId = settings.cardOwnerUserId,
        ownerName = settings.cardOwnerName,
        contributorUserId = importedContributorUserId,
        contributorName = importedContributorName,
        remoteId = "",
        updatedAtMillis = 0L,
        syncStatus = CardSyncStatus.LOCAL.id,
    )
}

private fun DrinkEntry.withImportReviewState(
    preserveUserMetadata: Boolean,
    pauseImportedCards: Boolean,
): DrinkEntry {
    val requiresContributorReview = !preserveUserMetadata && shouldReviewContribution(
        ownerUserId = ownerUserId,
        ownerName = ownerName,
        contributorUserId = contributorUserId,
        contributorName = contributorName,
    )
    return if (pauseImportedCards || requiresContributorReview) {
        copy(isEnabled = false, isPendingReview = true)
    } else {
        this
    }
}

private fun DrinkEntry.shouldReplaceRemote(existing: DrinkEntry): Boolean {
    if (CardSyncStatus.fromId(existing.syncStatus) == CardSyncStatus.DIRTY) return false
    return when {
        updatedAtMillis > existing.updatedAtMillis -> true
        updatedAtMillis < existing.updatedAtMillis -> false
        else -> contentFingerprint() != existing.contentFingerprint()
    }
}

private fun DrinkEntry.contentFingerprint(): String {
    val cleanOwnerName = normalizedCardUserName(ownerName)
    val cleanContributorName = contributorName.trim().ifBlank { cleanOwnerName }
    return listOf(
        text.trim(),
        drinks.coerceAtLeast(1).toString(),
        CardCategory.fromId(category).id,
        packName.trim().ifBlank { "Eigene Karten" },
        isEnabled.toString(),
        isPendingReview.toString(),
        QuestionLevel.fromId(questionLevel).id.toString(),
        normalizedCardUserId(ownerUserId, cleanOwnerName),
        cleanOwnerName,
        normalizedCardUserId(contributorUserId, cleanContributorName),
        cleanContributorName,
    ).joinToString("|")
}

private fun String.cardUserOrNull(): String? {
    val clean = trim()
    return clean.takeIf {
        it.isNotBlank() && !it.equals(DEFAULT_CARD_USER_NAME, ignoreCase = true)
    }
}
