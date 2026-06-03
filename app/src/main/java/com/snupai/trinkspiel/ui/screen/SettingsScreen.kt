package com.snupai.trinkspiel.ui.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snupai.trinkspiel.model.DrinkIntensity
import com.snupai.trinkspiel.model.GameMode
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.model.ThemeMode
import com.snupai.trinkspiel.ui.dialog.BackupImportPreviewDialog
import com.snupai.trinkspiel.ui.dialog.CardSyncPreviewDialog
import com.snupai.trinkspiel.ui.dialog.backupRestoreMessage
import com.snupai.trinkspiel.ui.dialog.cardSyncMessage
import com.snupai.trinkspiel.util.AppBackup
import com.snupai.trinkspiel.util.AppDiagnostics
import com.snupai.trinkspiel.util.BackendSyncInvitePackage
import com.snupai.trinkspiel.util.CardSyncPackage
import com.snupai.trinkspiel.util.LegalCopy
import com.snupai.trinkspiel.util.SupportRequest
import com.snupai.trinkspiel.util.TransferPackageShare
import com.snupai.trinkspiel.viewmodel.BackupImportPreview
import com.snupai.trinkspiel.viewmodel.CardUserSummary
import com.snupai.trinkspiel.viewmodel.DrinkViewModel
import com.snupai.trinkspiel.viewmodel.RemoteCardSyncPreview
import com.snupai.trinkspiel.viewmodel.RemoteCardSyncRunSummary
import com.snupai.trinkspiel.sync.CardSyncPlan
import com.snupai.trinkspiel.sync.RemoteLibraryMembership
import com.snupai.trinkspiel.sync.RemoteCardSnapshot
import com.snupai.trinkspiel.sync.RemoteSyncConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: DrinkViewModel,
    onBack: () -> Unit,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var singular by remember(state.drinkSingular) { mutableStateOf(state.drinkSingular) }
    var plural by remember(state.drinkPlural) { mutableStateOf(state.drinkPlural) }
    var cardOwnerName by remember(state.cardOwnerName) { mutableStateOf(state.cardOwnerName) }
    var activeContributorName by remember(state.activeContributorName) {
        mutableStateOf(state.activeContributorName)
    }
    var remoteSyncEndpointUrl by remember(state.remoteSyncEndpointUrl) {
        mutableStateOf(state.remoteSyncEndpointUrl)
    }
    var remoteSyncAccessToken by remember(state.remoteSyncAccessToken) {
        mutableStateOf(state.remoteSyncAccessToken)
    }
    var remoteSyncRole by remember(state.remoteSyncRole) {
        mutableStateOf(state.remoteSyncRole)
    }
    var backendInviteContributorName by remember { mutableStateOf("") }
    var backendInviteRole by remember { mutableStateOf("write") }
    var pendingDiagnosticsJson by remember { mutableStateOf("") }
    var pendingLastIssueJson by remember { mutableStateOf("") }
    var pendingBackupImport by remember { mutableStateOf<PendingBackupImport?>(null) }
    var pendingCardSyncImport by remember { mutableStateOf<PendingCardSyncImport?>(null) }
    var pendingBackendCardSync by remember { mutableStateOf<PendingBackendCardSync?>(null) }
    var pendingBackendMemberships by remember { mutableStateOf<List<RemoteLibraryMembership>?>(null) }
    var pendingBackendMembershipRevoke by remember { mutableStateOf<RemoteLibraryMembership?>(null) }
    var diagnosticsRefresh by remember { mutableIntStateOf(0) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    val latestCrash = remember(diagnosticsRefresh) { AppDiagnostics.latestCrash(context) }
    val appSnapshot = remember(state, diagnosticsRefresh) {
        AppDiagnostics.snapshot(context, state)
    }

    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = vm.exportBackupJson()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(json.toByteArray(Charsets.UTF_8))
                        } ?: error("Datei konnte nicht geöffnet werden")
                    }
                    snackbarHostState.showSnackbar("Backup exportiert")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Backup fehlgeschlagen: ${e.message}")
                }
            }
        }
    }

    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            ?: error("Datei konnte nicht geöffnet werden")
                    }
                    pendingBackupImport = PendingBackupImport(
                        json = json,
                        preview = vm.previewBackupJson(json),
                    )
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Backup-Prüfung fehlgeschlagen: ${e.message}")
                }
            }
        }
    }

    val cardSyncExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = vm.exportCardSyncPackageJson()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(json.toByteArray(Charsets.UTF_8))
                        } ?: error("Datei konnte nicht geöffnet werden")
                    }
                    snackbarHostState.showSnackbar("Karten-Sync-Paket exportiert")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Sync-Export fehlgeschlagen: ${e.message}")
                }
            }
        }
    }

    val cardSyncImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val cards = withContext(Dispatchers.IO) {
                        val json = context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            ?: error("Datei konnte nicht geöffnet werden")
                        CardSyncPackage.fromJson(json)
                    }
                    pendingCardSyncImport = PendingCardSyncImport(
                        cards = cards,
                        plan = vm.previewCardSync(cards),
                    )
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Sync-Paket-Prüfung fehlgeschlagen: ${e.message}")
                }
            }
        }
    }

    val diagnosticsExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingDiagnosticsJson
        if (uri != null && json.isNotBlank()) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(json.toByteArray(Charsets.UTF_8))
                        } ?: error("Datei konnte nicht geöffnet werden")
                    }
                    snackbarHostState.showSnackbar("Diagnose exportiert")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Diagnose-Export fehlgeschlagen: ${e.message}")
                }
            }
        }
        pendingDiagnosticsJson = ""
    }

    val lastIssueExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingLastIssueJson
        if (uri != null && json.isNotBlank()) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(json.toByteArray(Charsets.UTF_8))
                        } ?: error("Datei konnte nicht geöffnet werden")
                    }
                    snackbarHostState.showSnackbar("Letzter Fehler exportiert")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Fehler-Export fehlgeschlagen: ${e.message}")
                }
            }
        }
        pendingLastIssueJson = ""
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsCard(title = "Darstellung") {
                Text("Design", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.themeMode == mode,
                            onClick = { vm.selectThemeMode(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Systemfarben", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Nutzt Android Dynamic Color, wenn verfügbar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.dynamicColors,
                        onCheckedChange = vm::setDynamicColors
                    )
                }
            }

            SettingsCard(title = "Spielstandard") {
                Text("Modus", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GameMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.mode == mode,
                            onClick = { vm.selectMode(mode) },
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
                            onClick = { vm.selectIntensity(intensity) },
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
                        FilterChip(
                            selected = state.isQuestionLevelEnabled(level),
                            onClick = { vm.toggleQuestionLevel(level) },
                            label = { Text(level.label) }
                        )
                    }
                }
                Text(
                    QuestionLevel.entries.joinToString(separator = "\n") { level ->
                        "${level.shortLabel}: ${level.description}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsCard(title = "Trink-Wording") {
                OutlinedTextField(
                    value = singular,
                    onValueChange = { singular = it },
                    label = { Text("Singular") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = plural,
                    onValueChange = { plural = it },
                    label = { Text("Plural") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                SettingsPrimaryAction(
                    label = "Wording speichern",
                    icon = Icons.Default.Save,
                    onClick = { vm.updateDrinkWording(singular, plural) },
                )
            }

            SettingsCard(title = "Karten-User") {
                OutlinedTextField(
                    value = cardOwnerName,
                    onValueChange = { cardOwnerName = it },
                    label = { Text("Bibliothek-User") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_card_owner_field"),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = activeContributorName,
                    onValueChange = { activeContributorName = it },
                    label = { Text("Aktiver Beiträger") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_active_contributor_field"),
                    singleLine = true,
                )
                SettingsPrimaryAction(
                    label = "Karten-User speichern",
                    icon = Icons.Default.Person,
                    onClick = {
                        vm.updateCardUserProfile(
                            ownerName = cardOwnerName,
                            contributorName = activeContributorName,
                        )
                    },
                )
                CardUserProfilePicker(
                    cardUsers = state.cardUsers,
                    activeOwnerUserId = state.cardOwnerUserId,
                    activeContributorUserId = state.activeContributorUserId,
                    onOwnerSelected = { user ->
                        vm.selectCardOwnerProfile(user.userId, user.displayName)
                    },
                    onContributorSelected = { user ->
                        vm.selectActiveContributorProfile(user.userId, user.displayName)
                    },
                )
            }

            SettingsCard(title = "Backup") {
                SettingsPrimaryAction(
                    label = "Vollbackup exportieren",
                    icon = Icons.Default.Upload,
                    onClick = { backupExportLauncher.launch(AppBackup.fileName()) },
                )
                SettingsSecondaryAction(
                    label = "Backup importieren",
                    icon = Icons.Default.Download,
                    onClick = { backupImportLauncher.launch(arrayOf("application/json", "*/*")) },
                )
            }

            SettingsCard(title = "Gerätewechsel") {
                Text(
                    "Erstellt ein lokales Transferpaket für ein neues Gerät. Es enthält Karten, Spieler, Scores und Einstellungen und wird nur über die Android-App geteilt, die du auswählst.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SettingsPrimaryAction(
                    label = "Transferpaket teilen",
                    icon = Icons.Default.Share,
                    onClick = {
                        scope.launch {
                            try {
                                val json = vm.exportBackupJson()
                                val packageFile = withContext(Dispatchers.IO) {
                                    TransferPackageShare.createFile(context, json)
                                }
                                if (!TransferPackageShare.share(context, packageFile)) {
                                    snackbarHostState.showSnackbar("Keine App zum Teilen gefunden")
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Transferpaket fehlgeschlagen: ${e.message}")
                            }
                        }
                    },
                )
                SettingsSecondaryAction(
                    label = "Transferpaket importieren",
                    icon = Icons.Default.Download,
                    onClick = { backupImportLauncher.launch(arrayOf("application/json", "*/*")) },
                )
            }

            SettingsCard(title = "Karten-Sync-Paket") {
                Text(
                    "Exportiert nur Karten-Snapshots deiner aktuellen Bibliothek. Beim Import werden neue oder neuere Remote-Karten lokal übernommen; Konflikte bleiben unangetastet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SettingsPrimaryAction(
                    label = "Karten-Sync-Paket exportieren",
                    icon = Icons.Default.Upload,
                    enabled = state.activeLibraryEntryCount > 0,
                    onClick = { cardSyncExportLauncher.launch(CardSyncPackage.fileName()) },
                )
                SettingsSecondaryAction(
                    label = "Karten-Sync-Paket teilen",
                    icon = Icons.Default.Share,
                    enabled = state.activeLibraryEntryCount > 0,
                    onClick = {
                        scope.launch {
                            try {
                                val json = vm.exportCardSyncPackageJson()
                                val packageFile = withContext(Dispatchers.IO) {
                                    TransferPackageShare.createFile(
                                        context = context,
                                        json = json,
                                        fileName = CardSyncPackage.fileName(),
                                        shareFolder = "card_sync",
                                    )
                                }
                                if (!TransferPackageShare.share(context, packageFile)) {
                                    snackbarHostState.showSnackbar("Keine App zum Teilen gefunden")
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Sync-Paket teilen fehlgeschlagen: ${e.message}")
                            }
                        }
                    },
                )
                SettingsSecondaryAction(
                    label = "Karten-Sync-Paket importieren",
                    icon = Icons.Default.Download,
                    onClick = { cardSyncImportLauncher.launch(arrayOf("application/json", "*/*")) },
                )
            }

            SettingsCard(title = "Backend-Sync") {
                Text(
                    "Optionaler Anschluss an einen Karten-Backend-Endpunkt. Sync läuft nur, wenn du ihn hier manuell startest.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Ein Backend-Invite enthält Backend-URL und Sync-Token. Teile es nur mit Personen, die diese Bibliothek lesen oder beitragen dürfen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Aktive Bibliothek: ${state.cardOwnerName} · Beiträger: ${state.activeContributorName} · Rolle: ${remoteSyncRole.ifBlank { "unbekannt" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = remoteSyncEndpointUrl,
                    onValueChange = { remoteSyncEndpointUrl = it },
                    label = { Text("Backend-URL") },
                    placeholder = { Text("https://example.com/api") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = remoteSyncAccessToken,
                    onValueChange = { remoteSyncAccessToken = it },
                    label = { Text("Sync-Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text("Token-Rolle", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("read", "write", "admin").forEach { role ->
                        FilterChip(
                            selected = remoteSyncRole == role,
                            onClick = { remoteSyncRole = role },
                            label = { Text(role) },
                        )
                    }
                }
                SettingsPrimaryAction(
                    label = "Backend-Sync speichern",
                    icon = Icons.Default.Save,
                    onClick = {
                        vm.updateRemoteSyncConfig(
                            endpointUrl = remoteSyncEndpointUrl,
                            accessToken = remoteSyncAccessToken,
                            role = remoteSyncRole,
                        )
                        scope.launch { snackbarHostState.showSnackbar("Backend-Sync gespeichert") }
                    },
                )
                SettingsSecondaryAction(
                    label = "Backend-Sync prüfen",
                    icon = Icons.Default.Refresh,
                    enabled = remoteSyncEndpointUrl.trim().isNotBlank(),
                    onClick = {
                        scope.launch {
                            try {
                                vm.updateRemoteSyncConfig(
                                    endpointUrl = remoteSyncEndpointUrl,
                                    accessToken = remoteSyncAccessToken,
                                    role = remoteSyncRole,
                                )
                                val preview = vm.previewRemoteCardSync()
                                pendingBackendCardSync = PendingBackendCardSync(preview)
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Backend-Sync-Prüfung fehlgeschlagen: ${e.message}")
                            }
                        }
                    },
                )
                SettingsSecondaryAction(
                    label = "Backend-Mitglieder prüfen",
                    icon = Icons.Default.People,
                    enabled = remoteSyncEndpointUrl.trim().isNotBlank() &&
                        remoteSyncAccessToken.trim().isNotBlank(),
                    onClick = {
                        scope.launch {
                            try {
                                vm.updateRemoteSyncConfig(
                                    endpointUrl = remoteSyncEndpointUrl,
                                    accessToken = remoteSyncAccessToken,
                                    role = remoteSyncRole,
                                )
                                pendingBackendMemberships = vm.fetchRemoteLibraryMemberships()
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Mitglieder-Prüfung fehlgeschlagen: ${e.message}")
                            }
                        }
                    },
                )
                SettingsSecondaryAction(
                    label = "Backend-Invite teilen",
                    icon = Icons.Default.Share,
                    enabled = remoteSyncEndpointUrl.trim().isNotBlank() &&
                        remoteSyncAccessToken.trim().isNotBlank(),
                    onClick = {
                        scope.launch {
                            try {
                                val json = BackendSyncInvitePackage.toJson(
                                    config = RemoteSyncConfig(
                                        endpointUrl = remoteSyncEndpointUrl,
                                        accessToken = remoteSyncAccessToken,
                                        role = remoteSyncRole,
                                    ),
                                    libraryOwnerUserId = state.cardOwnerUserId,
                                    libraryOwnerName = state.cardOwnerName,
                                    contributorUserId = state.activeContributorUserId,
                                    contributorName = state.activeContributorName,
                                    role = remoteSyncRole.ifBlank { "write" },
                                )
                                val packageFile = withContext(Dispatchers.IO) {
                                    TransferPackageShare.createFile(
                                        context = context,
                                        json = json,
                                        fileName = BackendSyncInvitePackage.fileName(),
                                        shareFolder = "backend_invite",
                                    )
                                }
                                if (!TransferPackageShare.share(context, packageFile)) {
                                    snackbarHostState.showSnackbar("Keine App zum Teilen gefunden")
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Backend-Invite fehlgeschlagen: ${e.message}")
                            }
                        }
                    },
                )
                Text("Contributor-Invite", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Mit einem Admin-Token kann das Backend einen eigenen Token für einen neuen Beiträger erzeugen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = backendInviteContributorName,
                    onValueChange = { backendInviteContributorName = it },
                    label = { Text("Neuer Beiträger") },
                    placeholder = { Text(state.activeContributorName) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text("Invite-Rolle", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("read", "write", "admin").forEach { role ->
                        FilterChip(
                            selected = backendInviteRole == role,
                            onClick = { backendInviteRole = role },
                            label = { Text(role) },
                        )
                    }
                }
                SettingsSecondaryAction(
                    label = "Contributor-Invite erstellen",
                    icon = Icons.Default.People,
                    enabled = remoteSyncEndpointUrl.trim().isNotBlank() &&
                        remoteSyncAccessToken.trim().isNotBlank(),
                    onClick = {
                        scope.launch {
                            try {
                                vm.updateRemoteSyncConfig(
                                    endpointUrl = remoteSyncEndpointUrl,
                                    accessToken = remoteSyncAccessToken,
                                    role = remoteSyncRole,
                                )
                                val invite = vm.createBackendSyncInvite(
                                    contributorName = backendInviteContributorName,
                                    role = backendInviteRole,
                                )
                                val json = BackendSyncInvitePackage.toJson(
                                    config = RemoteSyncConfig(
                                        endpointUrl = invite.endpointUrl,
                                        accessToken = invite.accessToken,
                                        role = invite.role,
                                    ),
                                    libraryOwnerUserId = invite.libraryOwnerUserId,
                                    libraryOwnerName = invite.libraryOwnerName,
                                    contributorUserId = invite.contributorUserId,
                                    contributorName = invite.contributorName,
                                    role = invite.role,
                                )
                                val packageFile = withContext(Dispatchers.IO) {
                                    TransferPackageShare.createFile(
                                        context = context,
                                        json = json,
                                        fileName = BackendSyncInvitePackage.fileName(),
                                        shareFolder = "backend_invite",
                                    )
                                }
                                if (!TransferPackageShare.share(context, packageFile)) {
                                    snackbarHostState.showSnackbar("Keine App zum Teilen gefunden")
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Contributor-Invite fehlgeschlagen: ${e.message}")
                            }
                        }
                    },
                )
            }

            SettingsCard(title = "Diagnose") {
                Text(
                    "Diagnose- und Fehlerberichte bleiben lokal und werden nur geteilt oder exportiert, wenn du es auslöst.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    latestCrash?.let { "Letzter Absturz: ${it.exceptionClass}" } ?: "Kein Absturz gespeichert.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SettingsPrimaryAction(
                    label = "Letzten Fehler teilen",
                    icon = Icons.Default.BugReport,
                    enabled = latestCrash != null,
                    onClick = {
                        val report = AppDiagnostics.lastIssueToJson(AppDiagnostics.snapshot(context, state))
                        if (report != null) {
                            shareLastIssue(context, report)
                        } else {
                            scope.launch { snackbarHostState.showSnackbar("Kein letzter Fehler gespeichert") }
                        }
                    },
                )
                SettingsSecondaryAction(
                    label = "Letzten Fehler exportieren",
                    icon = Icons.Default.Upload,
                    enabled = latestCrash != null,
                    onClick = {
                        val report = AppDiagnostics.lastIssueToJson(AppDiagnostics.snapshot(context, state))
                        if (report != null) {
                            pendingLastIssueJson = report
                            lastIssueExportLauncher.launch(AppDiagnostics.lastIssueFileName())
                        } else {
                            scope.launch { snackbarHostState.showSnackbar("Kein letzter Fehler gespeichert") }
                        }
                    },
                )
                SettingsSecondaryAction(
                    label = "Diagnosebericht teilen",
                    icon = Icons.Default.Share,
                    onClick = {
                        val report = AppDiagnostics.toJson(AppDiagnostics.snapshot(context, state))
                        shareDiagnostics(context, report)
                    },
                )
                SettingsSecondaryAction(
                    label = "Diagnosebericht exportieren",
                    icon = Icons.Default.Upload,
                    onClick = {
                        pendingDiagnosticsJson = AppDiagnostics.toJson(AppDiagnostics.snapshot(context, state))
                        diagnosticsExportLauncher.launch(AppDiagnostics.fileName())
                    },
                )
                SettingsSecondaryAction(
                    label = "Letzten Fehler löschen",
                    icon = Icons.Default.Delete,
                    enabled = latestCrash != null,
                    onClick = {
                        AppDiagnostics.clearCrash(context)
                        diagnosticsRefresh += 1
                        scope.launch { snackbarHostState.showSnackbar("Diagnose gelöscht") }
                    },
                )
            }

            SettingsCard(title = "Support") {
                Text(
                    "Bereitet eine Support-Anfrage mit technischen Infos vor. Kartentexte und Spielernamen werden nicht eingefügt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SettingsPrimaryAction(
                    label = "Support-Anfrage vorbereiten",
                    icon = Icons.Default.SupportAgent,
                    onClick = {
                        val body = SupportRequest.buildBody(AppDiagnostics.snapshot(context, state))
                        if (!shareSupportRequest(context, body)) {
                            scope.launch { snackbarHostState.showSnackbar("Keine App zum Teilen gefunden") }
                        }
                    },
                )
            }

            SettingsCard(title = "App & Rechtliches") {
                Text(
                    "Seemops Trinkspiel ${appSnapshot.appVersionName} (${appSnapshot.appVersionCode})",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "Offline nutzbar, ohne Werbung, ohne Tracking.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SettingsPrimaryAction(
                    label = "Datenschutzrichtlinie anzeigen",
                    icon = Icons.AutoMirrored.Filled.Article,
                    onClick = { showPrivacyPolicy = true },
                )
                SettingsSecondaryAction(
                    label = "Datenschutzrichtlinie teilen",
                    icon = Icons.Default.Share,
                    onClick = {
                        if (!sharePrivacyPolicy(context)) {
                            scope.launch { snackbarHostState.showSnackbar("Keine App zum Teilen gefunden") }
                        }
                    },
                )
            }

            SettingsCard(title = "Datenschutz & Sicherheit") {
                Text(
                    "Kein Tracking, keine Werbung.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "Karten, Spieler, Scores und Einstellungen bleiben lokal auf diesem Gerät, bis du Export, Teilen, Import oder den optionalen Backend-Sync selbst auslöst.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Backups und Transferpakete werden nur erstellt, importiert oder geteilt, wenn du es selbst auslöst.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Diagnose- und Fehlerberichte enthalten technische App-Daten und werden nur manuell geteilt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Nur für volljährige Spieler. Trink Wasser, mach Pausen und steig aus, wenn es genug ist.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsCard(title = "Zurücksetzen") {
                SettingsSecondaryAction(
                    label = "Runde zurücksetzen",
                    icon = Icons.Default.Refresh,
                    onClick = vm::resetDeck,
                )
                SettingsSecondaryAction(
                    label = "Scores zurücksetzen",
                    icon = Icons.Default.Refresh,
                    onClick = vm::resetScores,
                )
                SettingsSecondaryAction(
                    label = "Spieler löschen",
                    icon = Icons.Default.People,
                    onClick = vm::clearPlayers,
                )
                SettingsSecondaryAction(
                    label = "Alters-/Sicherheitshinweis zurücksetzen",
                    icon = Icons.Default.Refresh,
                    onClick = vm::resetSafetyNotice,
                )
            }
        }
    }

    if (showPrivacyPolicy) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicy = false },
            title = { Text(LegalCopy.PRIVACY_POLICY_TITLE) },
            text = {
                Text(
                    text = LegalCopy.privacyPolicyText,
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyPolicy = false }) {
                    Text("Schließen")
                }
            }
        )
    }

    pendingBackupImport?.let { pending ->
        BackupImportPreviewDialog(
            preview = pending.preview,
            showReviewOption = pending.preview.cards.hasCardChanges,
            reviewByDefault = false,
            onConfirm = { pauseImportedCards ->
                scope.launch {
                    try {
                        val summary = vm.restoreBackupJson(
                            json = pending.json,
                            pauseImportedCards = pauseImportedCards,
                        )
                        pendingBackupImport = null
                        snackbarHostState.showSnackbar(summary.backupRestoreMessage(pauseImportedCards))
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Restore fehlgeschlagen: ${e.message}")
                    }
                }
            },
            onDismiss = { pendingBackupImport = null },
        )
    }

    pendingCardSyncImport?.let { pending ->
        CardSyncPreviewDialog(
            plan = pending.plan,
            onConfirm = { remoteConflictLocalIds ->
                scope.launch {
                    try {
                        val summary = vm.applyRemoteCardSync(
                            remoteCards = pending.cards,
                            remoteConflictLocalIds = remoteConflictLocalIds,
                        )
                        pendingCardSyncImport = null
                        snackbarHostState.showSnackbar(summary.cardSyncMessage())
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Sync-Import fehlgeschlagen: ${e.message}")
                    }
                }
            },
            onDismiss = { pendingCardSyncImport = null },
        )
    }

    pendingBackendCardSync?.let { pending ->
        CardSyncPreviewDialog(
            plan = pending.preview.plan,
            title = "Backend-Sync prüfen",
            introText = "Remote-Karten und lokale Uploads werden erst nach deiner Bestätigung synchronisiert.",
            confirmLabel = "Mit Backend synchronisieren",
            allowBackendActions = true,
            emptyMessage = "Es wurden keine synchronisierbaren Änderungen gefunden.",
            onConfirm = { remoteConflictLocalIds ->
                scope.launch {
                    try {
                        val summary = vm.syncCardsWithRemote(
                            remoteCards = pending.preview.remoteCards,
                            remoteConflictLocalIds = remoteConflictLocalIds,
                        )
                        pendingBackendCardSync = null
                        snackbarHostState.showSnackbar(summary.remoteSyncMessage())
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Backend-Sync fehlgeschlagen: ${e.message}")
                    }
                }
            },
            onDismiss = { pendingBackendCardSync = null },
        )
    }

    pendingBackendMemberships?.let { memberships ->
        BackendMembershipDialog(
            memberships = memberships,
            onRevoke = { membership -> pendingBackendMembershipRevoke = membership },
            onDismiss = { pendingBackendMemberships = null },
        )
    }

    pendingBackendMembershipRevoke?.let { membership ->
        AlertDialog(
            onDismissRequest = { pendingBackendMembershipRevoke = null },
            title = { Text("Invite entziehen?") },
            text = {
                Text(
                    "Der Backend-Token für ${membership.contributorName.ifBlank { "diesen Beiträger" }} wird widerrufen. Bereits synchronisierte Karten bleiben erhalten.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                vm.revokeRemoteLibraryMembership(membership.tokenId)
                                pendingBackendMemberships = vm.fetchRemoteLibraryMemberships()
                                pendingBackendMembershipRevoke = null
                                snackbarHostState.showSnackbar("Backend-Invite entzogen")
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Invite konnte nicht entzogen werden: ${e.message}")
                            }
                        }
                    },
                ) {
                    Text("Entziehen")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBackendMembershipRevoke = null }) {
                    Text("Abbrechen")
                }
            },
        )
    }
}

private data class PendingBackupImport(
    val json: String,
    val preview: BackupImportPreview,
)

private data class PendingCardSyncImport(
    val cards: List<RemoteCardSnapshot>,
    val plan: CardSyncPlan,
)

private data class PendingBackendCardSync(
    val preview: RemoteCardSyncPreview,
)

@Composable
private fun CardUserProfilePicker(
    cardUsers: List<CardUserSummary>,
    activeOwnerUserId: String,
    activeContributorUserId: String,
    onOwnerSelected: (CardUserSummary) -> Unit,
    onContributorSelected: (CardUserSummary) -> Unit,
) {
    if (cardUsers.isEmpty()) return

    Text("Bibliothek", style = MaterialTheme.typography.labelLarge)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cardUsers.forEach { user ->
            FilterChip(
                selected = user.userId == activeOwnerUserId,
                onClick = { onOwnerSelected(user) },
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

    Text("Aktiver Beiträger", style = MaterialTheme.typography.labelLarge)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cardUsers.forEach { user ->
            FilterChip(
                selected = user.userId == activeContributorUserId,
                onClick = { onContributorSelected(user) },
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

@Composable
private fun BackendMembershipDialog(
    memberships: List<RemoteLibraryMembership>,
    onRevoke: (RemoteLibraryMembership) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backend-Mitglieder") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (memberships.isEmpty()) {
                    Text(
                        "Keine Mitgliedschaften vom Backend zurückgegeben.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    memberships.forEach { membership ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                membership.contributorName.ifBlank { "Ohne Beiträgername" },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                listOf(
                                    "Rolle: ${membership.role.ifBlank { "unbekannt" }}",
                                    "Quelle: ${membership.sourceLabel()}",
                                    "ID: ${membership.contributorUserId.ifBlank { "ohne Contributor-ID" }}",
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (membership.isGeneratedInvite()) {
                                OutlinedButton(
                                    onClick = { onRevoke(membership) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error,
                                    ),
                                ) {
                                    Text("Invite entziehen")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Schließen")
            }
        },
    )
}

private fun RemoteLibraryMembership.sourceLabel(): String =
    when (source) {
        "generated" -> "Invite"
        "configured" -> "Konfiguration"
        else -> source
    }

private fun RemoteLibraryMembership.isGeneratedInvite(): Boolean =
    source == "generated" && tokenId.isNotBlank()

private fun CardUserSummary.profileLabel(): String =
    if (totalCount > 0) {
        "$displayName ($totalCount)"
    } else {
        displayName
    }

private fun RemoteCardSyncRunSummary.remoteSyncMessage(): String =
    buildString {
        append("${pulled.localInserted} neu")
        append(", ${pulled.localUpdated} aktualisiert")
        append(", ${pulled.localMarkedSynced} synchron markiert")
        if (pulled.conflicts > 0) {
            append(", ${pulled.conflicts} Konflikte")
        }
        if (pulled.conflictsResolved > 0) {
            append(", ${pulled.conflictsResolved} remote übernommen")
        }
        if (pulled.skippedRemoteCards > 0) {
            append(", ${pulled.skippedRemoteCards} übersprungen")
        }
        if (remoteChanged > 0) {
            append(", $remoteChanged remote geschrieben")
        } else {
            append(", nichts remote geschrieben")
        }
    }

private fun shareDiagnostics(context: Context, report: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_SUBJECT, AppDiagnostics.fileName())
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(intent, "Diagnose teilen"))
}

private fun shareLastIssue(context: Context, report: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_SUBJECT, AppDiagnostics.lastIssueFileName())
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(intent, "Letzten Fehler teilen"))
}

private fun shareSupportRequest(context: Context, body: String): Boolean {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, SupportRequest.SUBJECT)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    return try {
        context.startActivity(Intent.createChooser(intent, "Support-Anfrage teilen"))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private fun sharePrivacyPolicy(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, LegalCopy.PRIVACY_POLICY_TITLE)
        putExtra(Intent.EXTRA_TEXT, LegalCopy.privacyPolicyText)
    }
    return try {
        context.startActivity(Intent.createChooser(intent, "Datenschutzrichtlinie teilen"))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

@Composable
private fun SettingsPrimaryAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(
            label,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsSecondaryAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(
            label,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
