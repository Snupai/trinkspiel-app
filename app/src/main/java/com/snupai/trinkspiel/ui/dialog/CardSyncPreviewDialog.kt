package com.snupai.trinkspiel.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import com.snupai.trinkspiel.sync.CardSyncConflict
import com.snupai.trinkspiel.sync.CardSyncPlan
import com.snupai.trinkspiel.viewmodel.CardSyncApplySummary

@Composable
fun CardSyncPreviewDialog(
    plan: CardSyncPlan,
    onConfirm: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Karten-Sync-Paket prüfen",
    introText: String = "Die Datei wird erst nach deiner Bestätigung übernommen.",
    confirmLabel: String = "Lokale Änderungen übernehmen",
    allowBackendActions: Boolean = false,
    emptyMessage: String = "In dieser Datei wurden keine lokal übernehmbaren Karten gefunden.",
) {
    var selectedConflictLocalIds by remember(plan) { mutableStateOf<Set<Long>>(emptySet()) }
    val localChanges = plan.insertLocal.size +
        plan.updateLocal.size +
        plan.markLocalSynced.size +
        selectedConflictLocalIds.size
    val backendActions = plan.createRemote.size + plan.updateRemote.size
    val canConfirm = localChanges > 0 || (allowBackendActions && backendActions > 0)
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
                Text("Neue Karten lokal: ${plan.insertLocal.size}")
                Text("Remote-Updates lokal: ${plan.updateLocal.size}")
                Text("Als synchron markieren: ${plan.markLocalSynced.size}")
                Text("Konflikte: ${plan.conflicts.size}")
                Text("Ungültig/fremde Bibliothek: ${plan.skippedRemoteCards}")
                if (plan.createRemote.isNotEmpty() || plan.updateRemote.isNotEmpty()) {
                    Text(
                        "Mit Backend hochzuladen: $backendActions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (plan.conflicts.isNotEmpty()) {
                    Text(
                        "Konflikte werden standardmäßig nicht überschrieben. Wähle einzelne Remote-Versionen aus, wenn du sie lokal ersetzen willst.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (plan.conflicts.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selectedConflictLocalIds.size == plan.conflicts.size,
                                onCheckedChange = { checked ->
                                    selectedConflictLocalIds = if (checked) {
                                        plan.conflicts.map { it.local.id }.toSet()
                                    } else {
                                        emptySet()
                                    }
                                },
                            )
                            Text(
                                "Alle Remote-Konflikte übernehmen (${selectedConflictLocalIds.size}/${plan.conflicts.size})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        plan.conflicts.forEachIndexed { index, conflict ->
                            ConflictPreview(
                                conflict = conflict,
                                index = index + 1,
                                selected = conflict.local.id in selectedConflictLocalIds,
                                onSelectedChange = { selected ->
                                    selectedConflictLocalIds = if (selected) {
                                        selectedConflictLocalIds + conflict.local.id
                                    } else {
                                        selectedConflictLocalIds - conflict.local.id
                                    }
                                },
                            )
                        }
                    }
                }
                if (!canConfirm) {
                    Text(
                        emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedConflictLocalIds) },
                enabled = canConfirm,
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        },
    )
}

@Composable
private fun ConflictPreview(
    conflict: CardSyncConflict,
    index: Int,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (index > 1) HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = onSelectedChange,
            )
            Text(
                "Konflikt $index: Remote übernehmen",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            "Lokal: ${conflict.local.text.take(80)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Remote: ${conflict.remote.text.take(80)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Remote-Stufe: ${QuestionLevel.fromId(conflict.remote.questionLevel).label}, " +
                "${conflict.remote.drinks.coerceAtLeast(1)} Schlucke, von ${conflict.remote.contributorName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun CardSyncApplySummary.cardSyncMessage(): String =
    buildString {
        append("$localInserted neu")
        append(", $localUpdated aktualisiert")
        append(", $localMarkedSynced synchron markiert")
        if (conflicts > 0) {
            append(", $conflicts Konflikte")
        }
        if (conflictsResolved > 0) {
            append(", $conflictsResolved remote übernommen")
        }
        if (skippedRemoteCards > 0) {
            append(", $skippedRemoteCards übersprungen")
        }
        if (remoteCreatesPending + remoteUpdatesPending > 0) {
            append(", ${remoteCreatesPending + remoteUpdatesPending} Backend-Aktionen offen")
        }
    }
