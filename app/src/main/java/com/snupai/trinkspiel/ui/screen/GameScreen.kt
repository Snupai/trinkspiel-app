package com.snupai.trinkspiel.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snupai.trinkspiel.model.CardCategory
import com.snupai.trinkspiel.model.DrinkIntensity
import com.snupai.trinkspiel.model.DrawnCardRecord
import com.snupai.trinkspiel.model.GameMode
import com.snupai.trinkspiel.model.PlayerStats
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.viewmodel.DrinkViewModel
import com.snupai.trinkspiel.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    vm: DrinkViewModel,
    onNavigateToList: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val card = state.currentCard

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Seemops Trinkspiel") },
                actions = {
                    IconButton(
                        onClick = vm::resetDeck,
                        enabled = state.entries.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Runde zurücksetzen")
                    }
                    IconButton(onClick = onNavigateToList) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = "Einträge"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Einstellungen"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HeaderStats(state)

            if (!state.safetyNoticeAccepted) {
                SafetyNotice(onAccept = vm::acceptSafetyNotice)
            }

            if (state.isCardVisible && card != null) {
                val category = CardCategory.fromId(card.category)
                val questionLevel = QuestionLevel.fromId(card.questionLevel)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = category.label.take(1),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = card.text,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CardInfoPill(category.label)
                            CardInfoPill(questionLevel.label)
                            CardInfoPill(card.packName)
                        }
                        Spacer(Modifier.height(16.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                text = "${card.drinks} ${state.drinkLabel(card.drinks)}",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
                            )
                        }
                    }
                }

                Button(
                    onClick = vm::completeCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(
                        "Erledigt, nächster Zug",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                OutlinedButton(
                    onClick = vm::skipCurrentCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null)
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Karte überspringen")
                }
            } else {
                IdlePanel(
                    state = state,
                    onPickRandom = vm::pickRandom,
                    onAddStarterPack = vm::addStarterPack,
                    onUndoLastTurn = vm::undoLastTurn,
                )
            }

            if (state.drawsThisRound > 0 || state.drawHistory.isNotEmpty()) {
                SessionRecapPanel(state)
            }

            GameSettingsPanel(
                state = state,
                onModeSelected = vm::selectMode,
                onIntensitySelected = vm::selectIntensity,
                onQuestionLevelToggled = vm::toggleQuestionLevel,
                onPackToggled = vm::togglePackEnabled,
                onCustomCategoryToggled = vm::toggleCustomCategory,
            )

            PlayerPanel(
                state = state,
                onAddPlayer = vm::addPlayer,
                onSelectPlayer = vm::selectPlayer,
                onRemovePlayer = vm::removePlayer,
                onRenamePlayer = vm::renamePlayer,
                onNextPlayer = vm::nextPlayer,
                onCycleTeam = vm::cyclePlayerTeam,
                onAdjustPoints = vm::adjustPlayerPoints,
                onAdjustDrinks = vm::adjustPlayerDrinks,
            )

            if (state.playerStats.isNotEmpty()) {
                ScoreboardPanel(state)
            }

            OutlinedButton(
                onClick = onNavigateToList,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Einträge verwalten")
            }
        }
    }
}

@Composable
private fun CardInfoPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HeaderStats(state: UiState) {
    val progress = if (state.playableEntries.isEmpty()) {
        0f
    } else {
        (state.drawnCardIds.size / state.playableEntries.size.toFloat()).coerceIn(0f, 1f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        "Runde ${state.deckFinishedCount + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    state.currentPlayer?.let { player ->
                        Text(
                            "Am Zug: $player",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    "${state.remainingCards}/${state.playableEntries.size} spielbar",
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeaderPill(
                    text = "${state.drawsThisRound} gezogen",
                    modifier = Modifier.weight(0.9f),
                )
                HeaderPill(
                    text = "${state.drinksThisRound} ${state.drinkLabel(state.drinksThisRound)}",
                    modifier = Modifier.weight(0.9f),
                )
                HeaderPill(
                    text = "${state.mode.label} / ${state.intensity.label}",
                    modifier = Modifier.weight(1.25f),
                )
            }
        }
    }
}

@Composable
private fun HeaderPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SafetyNotice(onAccept: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Nur für volljährige Spieler. Trink Wasser, mach Pausen und steig aus, wenn es genug ist.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(onClick = onAccept) {
                Text("Verstanden")
            }
        }
    }
}

@Composable
private fun GameSettingsPanel(
    state: UiState,
    onModeSelected: (GameMode) -> Unit,
    onIntensitySelected: (DrinkIntensity) -> Unit,
    onQuestionLevelToggled: (QuestionLevel) -> Unit,
    onPackToggled: (String) -> Unit,
    onCustomCategoryToggled: (CardCategory) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Spieloptionen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Modus", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GameMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.mode == mode,
                        onClick = { onModeSelected(mode) },
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
                        selected = state.intensity == intensity,
                        onClick = { onIntensitySelected(intensity) },
                        label = { Text(intensity.label) }
                    )
                }
            }

            Text("Stufen", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuestionLevel.entries.forEach { level ->
                    val count = state.questionLevelCounts[level.id] ?: 0
                    FilterChip(
                        selected = state.isQuestionLevelEnabled(level),
                        onClick = { onQuestionLevelToggled(level) },
                        label = { Text("${level.label} ($count)") },
                    )
                }
            }
            Text(
                if (state.enabledQuestionLevelIds.isEmpty()) {
                    "Keine Stufe ausgewählt."
                } else {
                    QuestionLevel.entries
                        .filter { level -> level.id in state.enabledQuestionLevelIds }
                        .joinToString(separator = " · ") { level -> level.description }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.mode == GameMode.CUSTOM) {
                Text("Kategorien", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CardCategory.entries.forEach { category ->
                        FilterChip(
                            selected = state.isCustomCategoryEnabled(category),
                            onClick = { onCustomCategoryToggled(category) },
                            label = { Text(category.label) }
                        )
                    }
                }
                Text(
                    if (state.customCategoryIds.isEmpty()) {
                        "Keine Kategorie ausgewählt."
                    } else {
                        "${state.customCategoryIds.size} Kategorien aktiv"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.packNames.size > 1) {
                Text("Packs", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.packNames.forEach { packName ->
                        FilterChip(
                            selected = state.isPackEnabled(packName),
                            onClick = { onPackToggled(packName) },
                            label = {
                                Text(
                                    packName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
                Text(
                    "${state.playableEntries.size} Karten mit aktueller Auswahl spielbar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlayerPanel(
    state: UiState,
    onAddPlayer: (String) -> Unit,
    onSelectPlayer: (Int) -> Unit,
    onRemovePlayer: (Int) -> Unit,
    onRenamePlayer: (Int, String) -> Unit,
    onNextPlayer: () -> Unit,
    onCycleTeam: (Int) -> Unit,
    onAdjustPoints: (Int, Int) -> Unit,
    onAdjustDrinks: (Int, Int) -> Unit,
) {
    var playerName by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Spieler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = playerName,
                    onValueChange = { playerName = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Name") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        onAddPlayer(playerName)
                        playerName = ""
                    },
                    enabled = playerName.isNotBlank(),
                    modifier = Modifier.heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(
                        "Hinzufügen",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (state.players.isEmpty()) {
                Text(
                    "Ohne Spieler zieht ihr frei. Mit Spielern rotiert der Zug nach jeder Karte.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.playerStats.forEachIndexed { index, player ->
                        FilterChip(
                            selected = index == state.currentPlayerIndex,
                            onClick = { onSelectPlayer(index) },
                            label = {
                                Text(
                                    player.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onNextPlayer,
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text("Nächster Spieler")
                    }
                    TextButton(onClick = { onRemovePlayer(state.currentPlayerIndex) }) {
                        Text("Aktuellen entfernen")
                    }
                }
                state.currentPlayerStats?.let { stats ->
                    var renameName by remember(stats.name) { mutableStateOf(stats.name) }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "${stats.name}: ${stats.points} Punkte, ${stats.drinks} ${state.drinkLabel(stats.drinks)}, ${stats.team.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = renameName,
                                    onValueChange = { renameName = it },
                                    label = { Text("Name") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                )
                                OutlinedButton(
                                    onClick = { onRenamePlayer(state.currentPlayerIndex, renameName) },
                                    enabled = renameName.trim().isNotBlank() && renameName.trim() != stats.name,
                                    modifier = Modifier.heightIn(min = 52.dp),
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Text(
                                        "Umbenennen",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { onCycleTeam(state.currentPlayerIndex) },
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Text("Team wechseln")
                                }
                                OutlinedButton(
                                    onClick = { onAdjustPoints(state.currentPlayerIndex, 1) },
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Text("+Punkt")
                                }
                                OutlinedButton(
                                    onClick = { onAdjustPoints(state.currentPlayerIndex, -1) },
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Text("-Punkt")
                                }
                                OutlinedButton(
                                    onClick = { onAdjustDrinks(state.currentPlayerIndex, 1) },
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Text("+${state.drinkLabel(1)}")
                                }
                                OutlinedButton(
                                    onClick = { onAdjustDrinks(state.currentPlayerIndex, -1) },
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Text("-${state.drinkLabel(1)}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreboardPanel(state: UiState) {
    val rankedPlayers = state.playerStats
        .sortedWith(compareByDescending<PlayerStats> { it.points }.thenByDescending { it.drinks })

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Scoreboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            rankedPlayers.forEachIndexed { index, stats ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (index == 0) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (index == 0) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${index + 1}. ${stats.name}",
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                stats.team.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            "${stats.points} P / ${stats.drinks} ${state.drinkLabel(stats.drinks)}",
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (state.teamStats.isNotEmpty()) {
                Text("Teams", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                state.teamStats.forEach { team ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            team.team.label,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${team.points} P / ${team.drinks} ${state.drinkLabel(team.drinks)}",
                            modifier = Modifier.padding(start = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRecapPanel(state: UiState) {
    val recentCards = state.drawHistory.takeLast(5).asReversed()
    val topPlayers = state.playerStats
        .filter { it.points > 0 || it.drinks > 0 }
        .sortedWith(compareByDescending<PlayerStats> { it.points }.thenByDescending { it.drinks })
        .take(3)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Rückblick", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RecapPill("Runde ${state.deckFinishedCount + 1}")
                RecapPill("${state.drawsThisRound} Karten gezogen")
                RecapPill("${state.drinksThisRound} ${state.drinkLabel(state.drinksThisRound)}")
            }

            if (topPlayers.isNotEmpty()) {
                Text("Top Spieler", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                topPlayers.forEachIndexed { index, stats ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${index + 1}. ${stats.name}",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "${stats.points} P / ${stats.drinks} ${state.drinkLabel(stats.drinks)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (recentCards.isNotEmpty()) {
                Text("Letzte Karten", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                recentCards.forEach { record ->
                    RecapCardRow(record = record, state = state)
                }
            }
        }
    }
}

@Composable
private fun RecapPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecapCardRow(
    record: DrawnCardRecord,
    state: UiState,
) {
    val category = CardCategory.fromId(record.category)
    val level = QuestionLevel.fromId(record.questionLevel)
    val playerSuffix = record.playerName?.let { " · $it" }.orEmpty()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = record.text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Runde ${record.roundNumber} · ${level.label} · ${category.label} · ${record.drinks} ${state.drinkLabel(record.drinks)}$playerSuffix",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun IdlePanel(
    state: UiState,
    onPickRandom: () -> Unit,
    onAddStarterPack: () -> Unit,
    onUndoLastTurn: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.entries.isEmpty() || state.activeLibraryEntryCount == 0) {
            val noGlobalCards = state.entries.isEmpty()
            Icon(
                Icons.Default.Casino,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (noGlobalCards) "Noch keine Karten" else "Noch keine Karten in dieser Bibliothek",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (noGlobalCards) {
                    "Du kannst eigene Aufgaben bauen oder ein Starterpack laden."
                } else {
                    "Wechsle zurück oder lade Karten für ${state.cardOwnerName}."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            val emptyDeckHints = state.emptyDeckHints()
            if (emptyDeckHints.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                emptyDeckHints.forEach { hint ->
                    Text(
                        "- $hint",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = onAddStarterPack,
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Classic Starter laden")
            }
        } else {
            val emptyDeckHints = state.emptyDeckHints()
            Icon(
                Icons.Default.Casino,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "${state.playableEntries.size} von ${state.activeLibraryEntryCount} Karten dieser Bibliothek spielbar",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            if (emptyDeckHints.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Gerade ist alles blockiert:",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    emptyDeckHints.forEach { hint ->
                        Text(
                            "- $hint",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Text(
                    if (state.remainingCards == 0) {
                        "Deck ist durch. Die nächste Karte startet das Deck neu."
                    } else {
                        "Keine Wiederholungen bis das spielbare Deck leer ist."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (state.canUndoLastTurn) {
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onUndoLastTurn,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("Letzten Zug zurücknehmen")
        }
    }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = onPickRandom,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp),
        enabled = state.playableEntries.isNotEmpty(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Icon(Icons.Default.Casino, contentDescription = null)
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text("Karte ziehen", style = MaterialTheme.typography.titleMedium)
    }
}
