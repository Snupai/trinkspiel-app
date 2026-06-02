package com.snupai.trinkspiel.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.viewmodel.BackupImportPreview
import com.snupai.trinkspiel.viewmodel.BackupRestoreSummary

@Composable
fun BackupImportPreviewDialog(
    preview: BackupImportPreview,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Backup prüfen",
    introText: String = "Das Backup wird erst nach deiner Bestätigung übernommen.",
    showReviewOption: Boolean = false,
    reviewByDefault: Boolean = false,
) {
    var pauseImportedCards by remember(preview, reviewByDefault) {
        mutableStateOf(reviewByDefault && preview.cards.hasCardChanges)
    }
    val externalReviewCards = preview.cards.externalContributorCounts.sumOf { it.cardCount }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    introText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Karten in Datei: ${preview.cards.totalCards}")
                Text("Neue Karten: ${preview.cards.newCards}")
                if (preview.cards.updatedCards > 0) {
                    Text("Aktualisiert: ${preview.cards.updatedCards}")
                }
                Text("Übersprungen: ${preview.cards.skippedCards}")
                if (preview.cards.packNames.isNotEmpty()) {
                    Text("Packs: ${preview.cards.packNames.previewList()}")
                }
                if (preview.cards.questionLevelCounts.isNotEmpty()) {
                    Text("Stufen: ${preview.cards.questionLevelCounts.levelPreview()}")
                }
                if (preview.cards.contributorCounts.isNotEmpty()) {
                    Text(
                        "Beiträger: ${
                            preview.cards.contributorCounts.previewList {
                                "${it.displayName} (${it.cardCount})"
                            }
                        }"
                    )
                }
                if (externalReviewCards > 0) {
                    Text(
                        "Externe Beiträge: $externalReviewCards",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (preview.cards.pendingReviewCards > 0) {
                    Text(
                        "Karten im Review: ${preview.cards.pendingReviewCards}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showReviewOption) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = pauseImportedCards,
                            onCheckedChange = { pauseImportedCards = it },
                            enabled = preview.cards.hasCardChanges,
                        )
                        Text(
                            "Zusätzlich alle importierten Karten pausieren",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (preview.settingsFound) {
                    Text("Einstellungen: werden übernommen")
                    Text("Spieler im Backup: ${preview.playersFound}")
                    preview.mode?.let { Text("Modus: ${it.label}") }
                    preview.intensity?.let { Text("Intensität: ${it.label}") }
                } else {
                    Text("Einstellungen: nicht enthalten")
                }
                if (preview.cardUsersFound > 0) {
                    Text("Karten-User: ${preview.cardUsersFound}")
                }
                if (!preview.hasImportableContent) {
                    Text(
                        "In dieser Datei wurden keine neuen oder aktualisierbaren Karten, Einstellungen oder Karten-User gefunden.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(if (showReviewOption) pauseImportedCards else false) },
                enabled = preview.hasImportableContent,
            ) {
                Text("Import übernehmen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        },
    )
}

fun BackupRestoreSummary.backupRestoreMessage(cardsPaused: Boolean = false): String = buildString {
    append("${cards.added} Karten importiert")
    if (cards.updated > 0) {
        append(", ${cards.updated} aktualisiert")
    }
    if (cards.skippedDuplicates > 0) {
        append(", ${cards.skippedDuplicates} Duplikate übersprungen")
    }
    if (cardsPaused && cards.added > 0) {
        append(", neue Karten pausiert")
    }
    if (settingsRestored) {
        append(", Einstellungen übernommen")
    }
    if (cardUsersRestored > 0) {
        append(", $cardUsersRestored Karten-User übernommen")
    }
}

private fun List<String>.previewList(limit: Int = 3): String {
    val visible = take(limit).joinToString(", ")
    val hiddenCount = size - limit
    return if (hiddenCount > 0) "$visible +$hiddenCount" else visible
}

private fun Map<Int, Int>.levelPreview(): String =
    QuestionLevel.entries
        .mapNotNull { level ->
            val count = this[level.id] ?: return@mapNotNull null
            "${level.shortLabel}: $count"
        }
        .joinToString(", ")

private fun <T> List<T>.previewList(
    limit: Int = 3,
    transform: (T) -> String,
): String {
    val visible = take(limit).joinToString(", ", transform = transform)
    val hiddenCount = size - limit
    return if (hiddenCount > 0) "$visible +$hiddenCount" else visible
}
