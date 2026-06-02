package com.snupai.trinkspiel.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snupai.trinkspiel.model.DrinkIntensity
import com.snupai.trinkspiel.model.GameMode
import com.snupai.trinkspiel.viewmodel.DrinkViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstRunSetupScreen(vm: DrinkViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var ageConfirmed by remember(state.ageGateAccepted) { mutableStateOf(state.ageGateAccepted) }
    var safetyConfirmed by remember(state.safetyNoticeAccepted) { mutableStateOf(state.safetyNoticeAccepted) }
    var selectedMode by remember(state.mode) { mutableStateOf(state.mode) }
    val firstRunDefaultIntensity = if (state.firstRunSetupCompleted) {
        state.intensity
    } else {
        DrinkIntensity.LOW
    }
    var selectedIntensity by remember(firstRunDefaultIntensity) {
        mutableStateOf(firstRunDefaultIntensity)
    }
    val starterPacks = vm.builtInPacks
    val defaultStarterPackId = starterPacks.firstOrNull { it.id == "classic" }?.id
        ?: starterPacks.firstOrNull()?.id
    var selectedStarterPackId by remember(defaultStarterPackId) {
        mutableStateOf(defaultStarterPackId)
    }
    val selectedStarterPack = starterPacks.firstOrNull { it.id == selectedStarterPackId }
    var playerName by remember { mutableStateOf("") }
    var players by remember(state.players) { mutableStateOf(state.players) }

    fun addPlayer() {
        val cleanName = playerName.trim()
        if (cleanName.isBlank() || players.any { it.equals(cleanName, ignoreCase = true) }) return
        players = players + cleanName
        playerName = ""
    }

    val canStart = ageConfirmed && safetyConfirmed

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Seemops Trinkspiel") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.Casino,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Bereit machen",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                "Ein kurzer Pflichtcheck, dann startet das Spiel.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            SetupSection(title = "18+ und sicher spielen") {
                ConfirmationRow(
                    checked = ageConfirmed,
                    onCheckedChange = { ageConfirmed = it },
                    contentDescription = "18 plus bestaetigen",
                    text = "Ich bin mindestens 18 Jahre alt.",
                )
                ConfirmationRow(
                    checked = safetyConfirmed,
                    onCheckedChange = { safetyConfirmed = it },
                    contentDescription = "Sicher spielen bestaetigen",
                    text = "Wir trinken verantwortungsvoll, machen Pausen und hören jederzeit auf.",
                )
            }

            SetupSection(title = "Spielstandard") {
                Text("Modus", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GameMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode.label) }
                        )
                    }
                }

                Text("Intensität", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DrinkIntensity.entries.forEach { intensity ->
                        FilterChip(
                            selected = selectedIntensity == intensity,
                            onClick = { selectedIntensity = intensity },
                            label = { Text(intensity.label) }
                        )
                    }
                }
                Text(
                    "Startet bewusst mit Locker. Ihr könnt später jederzeit erhöhen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (starterPacks.isNotEmpty()) {
                SetupSection(title = "Startpack") {
                    Text(
                        "Wählt den Einstieg, der zu eurer Runde passt.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        starterPacks.forEach { pack ->
                            FilterChip(
                                selected = selectedStarterPackId == pack.id,
                                onClick = { selectedStarterPackId = pack.id },
                                label = { Text(pack.name) }
                            )
                        }
                    }
                    selectedStarterPack?.let { pack ->
                        Text(
                            "${pack.entries.size} Karten werden beim Start geladen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SetupSection(title = "Spieler") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = playerName,
                        onValueChange = { playerName = it },
                        label = { Text("Spielername") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Button(
                        onClick = ::addPlayer,
                        enabled = playerName.trim().isNotBlank(),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Spieler hinzufügen")
                    }
                }
                if (players.isEmpty()) {
                    Text(
                        "Du kannst auch ohne Spielernamen starten.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        players.forEach { player ->
                            InputChip(
                                selected = false,
                                onClick = { players = players - player },
                                label = {
                                    Text(
                                        player,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                modifier = Modifier.semantics {
                                    contentDescription = "$player entfernen"
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(2.dp))
            Button(
                onClick = {
                    vm.completeFirstRunSetup(
                        players = players,
                        mode = selectedMode,
                        intensity = selectedIntensity,
                        starterPackId = selectedStarterPackId,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                enabled = canStart,
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(
                    "Mit ${selectedStarterPack?.name ?: "Startpack"} starten",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            OutlinedButton(
                onClick = {
                    vm.completeFirstRunSetup(
                        players = players,
                        mode = selectedMode,
                        intensity = selectedIntensity,
                        addStarterPack = false,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                enabled = canStart,
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(
                    "Ohne Karten starten",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ConfirmationRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    text: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .semantics { this.contentDescription = contentDescription }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SetupSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
