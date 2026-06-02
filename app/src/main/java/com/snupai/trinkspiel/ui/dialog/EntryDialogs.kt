package com.snupai.trinkspiel.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snupai.trinkspiel.data.DrinkEntry
import com.snupai.trinkspiel.model.CardCategory
import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_ID
import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_NAME
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.model.cardUserIdForName
import com.snupai.trinkspiel.model.normalizedCardUserId
import com.snupai.trinkspiel.model.normalizedCardUserName
import com.snupai.trinkspiel.model.shouldReviewContribution
import com.snupai.trinkspiel.viewmodel.CardUserSummary

@Composable
fun AddEntryDialog(
    onDismiss: () -> Unit,
    defaultOwnerUserId: String = DEFAULT_CARD_USER_ID,
    defaultOwnerName: String = DEFAULT_CARD_USER_NAME,
    defaultContributorUserId: String = defaultOwnerUserId,
    defaultContributorName: String = defaultOwnerName,
    cardUsers: List<CardUserSummary> = emptyList(),
    onSave: (String, Int, String, String, Boolean, Boolean, Int, String, String, String, String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var drinks by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CardCategory.CHALLENGE.id) }
    var packName by remember { mutableStateOf("Eigene Karten") }
    var isEnabled by remember { mutableStateOf(true) }
    var questionLevel by remember { mutableIntStateOf(QuestionLevel.LEVEL_1.id) }
    var levelManuallySelected by remember { mutableStateOf(false) }
    var ownerName by remember(defaultOwnerName) {
        mutableStateOf(defaultOwnerName.ifBlank { DEFAULT_CARD_USER_NAME })
    }
    var ownerUserId by remember(defaultOwnerUserId, defaultOwnerName) {
        mutableStateOf(defaultOwnerUserId)
    }
    var contributorName by remember(defaultContributorName, defaultOwnerName) {
        mutableStateOf(defaultContributorName.ifBlank { defaultOwnerName.ifBlank { DEFAULT_CARD_USER_NAME } })
    }
    var contributorUserId by remember(defaultContributorUserId, defaultContributorName) {
        mutableStateOf(defaultContributorUserId)
    }
    var isPendingReview by remember(
        defaultOwnerUserId,
        defaultOwnerName,
        defaultContributorUserId,
        defaultContributorName,
    ) {
        mutableStateOf(
            shouldReviewContribution(
                defaultOwnerUserId,
                defaultOwnerName,
                defaultContributorUserId,
                defaultContributorName,
            )
        )
    }
    var reviewManuallySelected by remember { mutableStateOf(false) }
    var drinksError by remember { mutableStateOf(false) }
    var textError by remember { mutableStateOf(false) }

    val profileChoices = remember(
        cardUsers,
        defaultOwnerUserId,
        defaultOwnerName,
        defaultContributorUserId,
        defaultContributorName,
    ) {
        (
            cardUsers +
                CardUserSummary(defaultOwnerUserId, normalizedCardUserName(defaultOwnerName), 0, 0) +
                CardUserSummary(defaultContributorUserId, normalizedCardUserName(defaultContributorName), 0, 0)
            )
            .map { user ->
                val displayName = normalizedCardUserName(user.displayName)
                user.copy(
                    userId = normalizedCardUserId(user.userId, displayName),
                    displayName = displayName,
                )
            }
            .distinctBy { it.userId }
            .sortedWith(
                compareByDescending<CardUserSummary> { it.totalCount }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
            )
    }

    fun knownUserIdForName(name: String, defaultUserId: String, defaultName: String): String {
        val cleanName = normalizedCardUserName(name)
        return if (cleanName.equals(normalizedCardUserName(defaultName), ignoreCase = true)) {
            defaultUserId
        } else {
            profileChoices
                .firstOrNull { it.displayName.equals(cleanName, ignoreCase = true) }
                ?.userId
                ?: cardUserIdForName(cleanName)
        }
    }

    fun ownerUserIdFor(nextOwnerName: String): String =
        knownUserIdForName(nextOwnerName, defaultOwnerUserId, defaultOwnerName)

    fun contributorUserIdFor(nextContributorName: String, nextOwnerName: String): String {
        val cleanOwnerName = normalizedCardUserName(nextOwnerName)
        val cleanName = nextContributorName.trim().ifBlank { cleanOwnerName }
        return if (cleanName.equals(normalizedCardUserName(defaultContributorName), ignoreCase = true)) {
            defaultContributorUserId
        } else if (cleanName.equals(cleanOwnerName, ignoreCase = true)) {
            ownerUserIdFor(cleanOwnerName)
        } else {
            knownUserIdForName(cleanName, defaultContributorUserId, defaultContributorName)
        }
    }

    fun updateReviewDefault(
        nextOwnerUserId: String,
        nextOwnerName: String,
        nextContributorUserId: String,
        nextContributorName: String,
    ) {
        if (!reviewManuallySelected) {
            val cleanOwnerName = normalizedCardUserName(nextOwnerName)
            val cleanContributorName = nextContributorName.trim().ifBlank { cleanOwnerName }
            val nextPendingReview = shouldReviewContribution(
                nextOwnerUserId,
                cleanOwnerName,
                nextContributorUserId,
                cleanContributorName,
            )
            isPendingReview = nextPendingReview
            isEnabled = !nextPendingReview
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val drinksInt = drinks.toIntOrNull()
                textError = text.isBlank()
                drinksError = drinksInt == null || drinksInt <= 0
                if (!textError && !drinksError && drinksInt != null) {
                    onSave(
                        text.trim(),
                        drinksInt,
                        category,
                        packName.trim().ifBlank { "Eigene Karten" },
                        isEnabled && !isPendingReview,
                        isPendingReview,
                        questionLevel,
                        ownerUserId,
                        ownerName.trim().ifBlank { DEFAULT_CARD_USER_NAME },
                        contributorUserId,
                        contributorName.trim().ifBlank { ownerName.trim().ifBlank { DEFAULT_CARD_USER_NAME } },
                    )
                }
            }) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
        title = { Text("Neuer Eintrag") },
        text = {
            EntryForm(
                text = text,
                onTextChange = {
                    text = it
                    textError = false
                },
                drinks = drinks,
                onDrinksChange = {
                    drinks = it
                    drinksError = false
                    if (!levelManuallySelected) {
                        val nextDrinks = it.toIntOrNull()
                        if (nextDrinks != null) {
                            questionLevel = QuestionLevel.fromDrinks(nextDrinks).id
                        }
                    }
                },
                category = category,
                onCategoryChange = { category = it },
                packName = packName,
                onPackNameChange = { packName = it },
                isEnabled = isEnabled,
                onEnabledChange = { isEnabled = it },
                questionLevel = questionLevel,
                onQuestionLevelChange = {
                    questionLevel = it
                    levelManuallySelected = true
                },
                ownerName = ownerName,
                onOwnerNameChange = {
                    ownerName = it
                    ownerUserId = ownerUserIdFor(it)
                    updateReviewDefault(ownerUserId, it, contributorUserId, contributorName)
                },
                contributorName = contributorName,
                onContributorNameChange = {
                    contributorName = it
                    contributorUserId = contributorUserIdFor(it, ownerName)
                    updateReviewDefault(ownerUserId, ownerName, contributorUserId, it)
                },
                cardUsers = profileChoices,
                ownerUserId = ownerUserId,
                contributorUserId = contributorUserId,
                onOwnerProfileSelected = { user ->
                    ownerUserId = user.userId
                    ownerName = user.displayName
                    updateReviewDefault(user.userId, user.displayName, contributorUserId, contributorName)
                },
                onContributorProfileSelected = { user ->
                    contributorUserId = user.userId
                    contributorName = user.displayName
                    updateReviewDefault(ownerUserId, ownerName, user.userId, user.displayName)
                },
                isPendingReview = isPendingReview,
                onPendingReviewChange = {
                    reviewManuallySelected = true
                    isPendingReview = it
                    isEnabled = !it
                },
                textError = textError,
                drinksError = drinksError,
            )
        }
    )
}

@Composable
fun EditEntryDialog(
    entry: DrinkEntry,
    onDismiss: () -> Unit,
    cardUsers: List<CardUserSummary> = emptyList(),
    onSave: (DrinkEntry) -> Unit
) {
    var text by remember { mutableStateOf(entry.text) }
    var drinks by remember { mutableStateOf(entry.drinks.toString()) }
    var category by remember { mutableStateOf(CardCategory.fromId(entry.category).id) }
    var packName by remember { mutableStateOf(entry.packName) }
    var isEnabled by remember { mutableStateOf(entry.isEnabled) }
    var isPendingReview by remember { mutableStateOf(entry.isPendingReview) }
    var questionLevel by remember { mutableIntStateOf(QuestionLevel.fromId(entry.questionLevel).id) }
    var ownerName by remember { mutableStateOf(entry.ownerName.ifBlank { DEFAULT_CARD_USER_NAME }) }
    var ownerUserId by remember(entry.id, entry.ownerUserId, entry.ownerName) {
        mutableStateOf(normalizedCardUserId(entry.ownerUserId, entry.ownerName))
    }
    var contributorName by remember { mutableStateOf(entry.contributorName.ifBlank { ownerName }) }
    var contributorUserId by remember(entry.id, entry.contributorUserId, entry.contributorName) {
        mutableStateOf(normalizedCardUserId(entry.contributorUserId, entry.contributorName))
    }
    var reviewManuallySelected by remember { mutableStateOf(false) }
    var drinksError by remember { mutableStateOf(false) }
    var textError by remember { mutableStateOf(false) }

    val profileChoices = remember(cardUsers, entry.ownerUserId, entry.ownerName, entry.contributorUserId, entry.contributorName) {
        (
            cardUsers +
                CardUserSummary(entry.ownerUserId, normalizedCardUserName(entry.ownerName), 0, 0) +
                CardUserSummary(entry.contributorUserId, normalizedCardUserName(entry.contributorName), 0, 0)
            )
            .map { user ->
                val displayName = normalizedCardUserName(user.displayName)
                user.copy(
                    userId = normalizedCardUserId(user.userId, displayName),
                    displayName = displayName,
                )
            }
            .distinctBy { it.userId }
            .sortedWith(
                compareByDescending<CardUserSummary> { it.totalCount }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
            )
    }

    fun knownUserIdForName(name: String, defaultUserId: String, defaultName: String): String {
        val cleanName = normalizedCardUserName(name)
        return if (cleanName.equals(normalizedCardUserName(defaultName), ignoreCase = true)) {
            defaultUserId
        } else {
            profileChoices
                .firstOrNull { it.displayName.equals(cleanName, ignoreCase = true) }
                ?.userId
                ?: cardUserIdForName(cleanName)
        }
    }

    fun ownerUserIdFor(nextOwnerName: String): String =
        knownUserIdForName(nextOwnerName, entry.ownerUserId, entry.ownerName)

    fun contributorUserIdFor(nextContributorName: String, nextOwnerName: String): String {
        val cleanOwnerName = normalizedCardUserName(nextOwnerName)
        val cleanName = nextContributorName.trim().ifBlank { cleanOwnerName }
        return if (cleanName.equals(normalizedCardUserName(entry.contributorName), ignoreCase = true)) {
            entry.contributorUserId
        } else if (cleanName.equals(cleanOwnerName, ignoreCase = true)) {
            ownerUserIdFor(cleanOwnerName)
        } else {
            knownUserIdForName(cleanName, entry.contributorUserId, entry.contributorName)
        }
    }

    fun updateReviewDefault(
        nextOwnerUserId: String,
        nextOwnerName: String,
        nextContributorUserId: String,
        nextContributorName: String,
    ) {
        if (!reviewManuallySelected) {
            val cleanOwnerName = normalizedCardUserName(nextOwnerName)
            val cleanContributorName = nextContributorName.trim().ifBlank { cleanOwnerName }
            val nextPendingReview = shouldReviewContribution(
                nextOwnerUserId,
                cleanOwnerName,
                nextContributorUserId,
                cleanContributorName,
            )
            isPendingReview = nextPendingReview
            isEnabled = !nextPendingReview
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val drinksInt = drinks.toIntOrNull()
                textError = text.isBlank()
                drinksError = drinksInt == null || drinksInt <= 0
                if (!textError && !drinksError && drinksInt != null) {
                    onSave(
                        entry.copy(
                            text = text.trim(),
                            drinks = drinksInt,
                            category = category,
                            packName = packName.trim().ifBlank { "Eigene Karten" },
                            isEnabled = isEnabled && !isPendingReview,
                            isPendingReview = isPendingReview,
                            questionLevel = questionLevel,
                            ownerUserId = ownerUserId,
                            ownerName = ownerName.trim().ifBlank { DEFAULT_CARD_USER_NAME },
                            contributorUserId = contributorUserId,
                            contributorName = contributorName.trim().ifBlank {
                                ownerName.trim().ifBlank { DEFAULT_CARD_USER_NAME }
                            },
                        )
                    )
                }
            }) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
        title = { Text("Eintrag bearbeiten") },
        text = {
            EntryForm(
                text = text,
                onTextChange = {
                    text = it
                    textError = false
                },
                drinks = drinks,
                onDrinksChange = {
                    drinks = it
                    drinksError = false
                },
                category = category,
                onCategoryChange = { category = it },
                packName = packName,
                onPackNameChange = { packName = it },
                isEnabled = isEnabled,
                onEnabledChange = { isEnabled = it },
                questionLevel = questionLevel,
                onQuestionLevelChange = { questionLevel = it },
                ownerName = ownerName,
                onOwnerNameChange = {
                    ownerName = it
                    ownerUserId = ownerUserIdFor(it)
                    updateReviewDefault(ownerUserId, it, contributorUserId, contributorName)
                },
                contributorName = contributorName,
                onContributorNameChange = {
                    contributorName = it
                    contributorUserId = contributorUserIdFor(it, ownerName)
                    updateReviewDefault(ownerUserId, ownerName, contributorUserId, it)
                },
                cardUsers = profileChoices,
                ownerUserId = ownerUserId,
                contributorUserId = contributorUserId,
                onOwnerProfileSelected = { user ->
                    ownerUserId = user.userId
                    ownerName = user.displayName
                    updateReviewDefault(user.userId, user.displayName, contributorUserId, contributorName)
                },
                onContributorProfileSelected = { user ->
                    contributorUserId = user.userId
                    contributorName = user.displayName
                    updateReviewDefault(ownerUserId, ownerName, user.userId, user.displayName)
                },
                isPendingReview = isPendingReview,
                onPendingReviewChange = {
                    reviewManuallySelected = true
                    isPendingReview = it
                    isEnabled = !it
                },
                textError = textError,
                drinksError = drinksError,
            )
        }
    )
}

@Composable
private fun EntryForm(
    text: String,
    onTextChange: (String) -> Unit,
    drinks: String,
    onDrinksChange: (String) -> Unit,
    category: String,
    onCategoryChange: (String) -> Unit,
    packName: String,
    onPackNameChange: (String) -> Unit,
    isEnabled: Boolean = true,
    onEnabledChange: (Boolean) -> Unit = {},
    questionLevel: Int,
    onQuestionLevelChange: (Int) -> Unit,
    ownerName: String,
    onOwnerNameChange: (String) -> Unit,
    contributorName: String,
    onContributorNameChange: (String) -> Unit,
    cardUsers: List<CardUserSummary> = emptyList(),
    ownerUserId: String = "",
    contributorUserId: String = "",
    onOwnerProfileSelected: (CardUserSummary) -> Unit = {},
    onContributorProfileSelected: (CardUserSummary) -> Unit = {},
    isPendingReview: Boolean,
    onPendingReviewChange: (Boolean) -> Unit,
    textError: Boolean,
    drinksError: Boolean,
) {
    Column(
        modifier = Modifier
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text("Frage / Aufgabe") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("entry_text_field"),
            minLines = 2,
            maxLines = 4,
            isError = textError,
            supportingText = if (textError) ({ Text("Text fehlt") }) else null,
        )
        OutlinedTextField(
            value = drinks,
            onValueChange = onDrinksChange,
            label = { Text("Schlucke") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = drinksError,
            supportingText = if (drinksError) ({ Text("Mindestens 1") }) else null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("entry_drinks_field"),
            singleLine = true,
        )
        OutlinedTextField(
            value = packName,
            onValueChange = onPackNameChange,
            label = { Text("Pack") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("entry_pack_field"),
            singleLine = true,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val selectedLevel = QuestionLevel.fromId(questionLevel)
            Text("Stufe", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuestionLevel.entries.forEach { option ->
                    FilterChip(
                        selected = option.id == questionLevel,
                        onClick = { onQuestionLevelChange(option.id) },
                        label = { Text(option.label) }
                    )
                }
            }
            Text(
                selectedLevel.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = ownerName,
            onValueChange = onOwnerNameChange,
            label = { Text("Besitzer / User") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("entry_owner_field"),
            singleLine = true,
        )
        CardUserProfileChips(
            title = "Besitzer-Profil",
            cardUsers = cardUsers,
            selectedUserId = ownerUserId,
            onSelected = onOwnerProfileSelected,
        )
        OutlinedTextField(
            value = contributorName,
            onValueChange = onContributorNameChange,
            label = { Text("Hinzugefügt von") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("entry_contributor_field"),
            singleLine = true,
        )
        CardUserProfileChips(
            title = "Beiträger-Profil",
            cardUsers = cardUsers,
            selectedUserId = contributorUserId,
            onSelected = onContributorProfileSelected,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Review", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Review-Karten bleiben bis zur Freigabe aus dem Spiel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isPendingReview,
                onCheckedChange = onPendingReviewChange,
                modifier = Modifier.testTag("entry_review_switch"),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Aktiv", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Aktive Karten können im Spiel gezogen werden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isEnabled && !isPendingReview,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.testTag("entry_active_switch"),
                enabled = !isPendingReview,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Kategorie", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CardCategory.entries.forEach { option ->
                    FilterChip(
                        selected = option.id == category,
                        onClick = { onCategoryChange(option.id) },
                        label = { Text(option.label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CardUserProfileChips(
    title: String,
    cardUsers: List<CardUserSummary>,
    selectedUserId: String,
    onSelected: (CardUserSummary) -> Unit,
) {
    if (cardUsers.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            cardUsers.forEach { user ->
                FilterChip(
                    selected = user.userId == selectedUserId,
                    onClick = { onSelected(user) },
                    label = {
                        Text(
                            user.profileLabel(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

private fun CardUserSummary.profileLabel(): String =
    if (totalCount > 0) "$displayName ($totalCount)" else displayName

@Composable
fun ConfirmDeleteDialog(
    title: String = "Löschen?",
    message: String = "Dieser Eintrag wird unwiderruflich gelöscht.",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Löschen", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
        title = { Text(title) },
        text = { Text(message) }
    )
}
