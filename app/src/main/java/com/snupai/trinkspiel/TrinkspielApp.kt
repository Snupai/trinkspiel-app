package com.snupai.trinkspiel

import android.content.Context
import android.os.Looper
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.snupai.trinkspiel.ui.dialog.BackupImportPreviewDialog
import com.snupai.trinkspiel.ui.dialog.CardSyncPreviewDialog
import com.snupai.trinkspiel.ui.dialog.backupRestoreMessage
import com.snupai.trinkspiel.ui.dialog.cardSyncMessage
import com.snupai.trinkspiel.ui.screen.EntryListScreen
import com.snupai.trinkspiel.ui.screen.FirstRunSetupScreen
import com.snupai.trinkspiel.ui.screen.GameScreen
import com.snupai.trinkspiel.ui.screen.SettingsScreen
import com.snupai.trinkspiel.ui.theme.TrinkspielTheme
import com.snupai.trinkspiel.ui.theme.shouldUseDarkTheme
import com.snupai.trinkspiel.util.BackendSyncInvite
import com.snupai.trinkspiel.util.BackendSyncInvitePackage
import com.snupai.trinkspiel.util.CardSyncPackage
import com.snupai.trinkspiel.util.IncomingBackupImport
import com.snupai.trinkspiel.sync.CardSyncPlan
import com.snupai.trinkspiel.sync.RemoteCardSnapshot
import com.snupai.trinkspiel.viewmodel.BackupImportPreview
import com.snupai.trinkspiel.viewmodel.DrinkViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TrinkspielApp(
    vm: DrinkViewModel,
    incomingBackupImport: StateFlow<IncomingBackupImport?>? = null,
    onIncomingBackupImportHandled: (Long) -> Unit = {},
) {
    val navController = rememberNavController()
    val emptyIncomingBackupImport = remember { MutableStateFlow<IncomingBackupImport?>(null) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val pendingIncomingBackupImport by (incomingBackupImport ?: emptyIncomingBackupImport)
        .collectAsStateWithLifecycle()

    TrinkspielTheme(
        darkTheme = shouldUseDarkTheme(state.themeMode),
        dynamicColors = state.dynamicColors,
    ) {
        if (state.requiresFirstRunSetup) {
            FirstRunSetupScreen(vm)
        } else {
            NavHost(navController = navController, startDestination = "game") {
                composable("game") {
                    GameScreen(
                        vm = vm,
                        onNavigateToList = { navController.navigate("list") },
                        onNavigateToSettings = { navController.navigate("settings") }
                    )
                }
                composable("list") {
                    EntryListScreen(
                        vm = vm,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        vm = vm,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }

        IncomingBackupImportHandler(
            vm = vm,
            request = pendingIncomingBackupImport,
            onHandled = onIncomingBackupImportHandled,
        )
    }
}

@Composable
private fun IncomingBackupImportHandler(
    vm: DrinkViewModel,
    request: IncomingBackupImport?,
    onHandled: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<IncomingImportPreview?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(request?.id) {
        preview = null
        errorMessage = null
        val importRequest = request ?: return@LaunchedEffect
        try {
            preview = withContext(Dispatchers.Default) {
                if (CardSyncPackage.isCardSyncPackage(importRequest.json)) {
                    val cards = CardSyncPackage.fromJson(importRequest.json)
                    IncomingImportPreview.CardSync(
                        cards = cards,
                        plan = vm.previewCardSync(cards),
                    )
                } else if (BackendSyncInvitePackage.isBackendSyncInvite(importRequest.json)) {
                    IncomingImportPreview.BackendInvite(
                        BackendSyncInvitePackage.fromJson(importRequest.json)
                    )
                } else {
                    IncomingImportPreview.Backup(vm.previewBackupJson(importRequest.json))
                }
            }
        } catch (e: Exception) {
            errorMessage = "Transferpaket konnte nicht geprüft werden: ${e.message}"
        }
    }

    val importRequest = request ?: return
    val error = errorMessage
    val importPreview = preview
    when {
        error != null -> {
            AlertDialog(
                onDismissRequest = { onHandled(importRequest.id) },
                title = { Text("Transferpaket prüfen") },
                text = { Text(error) },
                confirmButton = {
                    TextButton(onClick = { onHandled(importRequest.id) }) {
                        Text("OK")
                    }
                },
            )
        }

        importPreview is IncomingImportPreview.Backup -> {
            BackupImportPreviewDialog(
                preview = importPreview.preview,
                title = "Transferpaket prüfen",
                introText = "Das geteilte Transferpaket \"${importRequest.sourceLabel}\" wird erst nach deiner Bestätigung übernommen.",
                showReviewOption = importPreview.preview.cards.hasCardChanges,
                reviewByDefault = importPreview.preview.cards.externalContributorCounts.isNotEmpty(),
                onConfirm = { pauseImportedCards ->
                    scope.launch {
                        try {
                            val summary = vm.restoreBackupJson(
                                json = importRequest.json,
                                pauseImportedCards = pauseImportedCards,
                            )
                            context.showLongToast(summary.backupRestoreMessage(pauseImportedCards))
                            onHandled(importRequest.id)
                        } catch (e: Exception) {
                            context.showLongToast("Restore fehlgeschlagen: ${e.message}")
                        }
                    }
                },
                onDismiss = { onHandled(importRequest.id) },
            )
        }

        importPreview is IncomingImportPreview.CardSync -> {
            CardSyncPreviewDialog(
                plan = importPreview.plan,
                title = "Karten-Sync-Paket prüfen",
                introText = "Das geteilte Karten-Sync-Paket \"${importRequest.sourceLabel}\" wird erst nach deiner Bestätigung übernommen.",
                onConfirm = { remoteConflictLocalIds ->
                    scope.launch {
                        try {
                            val summary = vm.applyRemoteCardSync(
                                remoteCards = importPreview.cards,
                                remoteConflictLocalIds = remoteConflictLocalIds,
                            )
                            context.showLongToast(summary.cardSyncMessage())
                            onHandled(importRequest.id)
                        } catch (e: Exception) {
                            context.showLongToast("Sync-Import fehlgeschlagen: ${e.message}")
                        }
                    }
                },
                onDismiss = { onHandled(importRequest.id) },
            )
        }

        importPreview is IncomingImportPreview.BackendInvite -> {
            AlertDialog(
                onDismissRequest = { onHandled(importRequest.id) },
                title = { Text("Backend-Invite prüfen") },
                text = {
                    Text(
                        "Bibliothek: ${importPreview.invite.libraryOwnerName}\n" +
                            "Backend: ${importPreview.invite.endpointUrl}\n" +
                            "Rolle: ${importPreview.invite.role}\n\n" +
                            "Der Sync-Token wird lokal gespeichert. Danach kannst du in den Einstellungen den Backend-Sync prüfen und starten."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            vm.applyBackendSyncInvite(importPreview.invite)
                            context.showLongToast("Backend-Invite übernommen")
                            onHandled(importRequest.id)
                        }
                    ) {
                        Text("Invite übernehmen")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onHandled(importRequest.id) }) {
                        Text("Abbrechen")
                    }
                },
            )
        }
    }
}

private sealed interface IncomingImportPreview {
    data class Backup(val preview: BackupImportPreview) : IncomingImportPreview
    data class CardSync(
        val cards: List<RemoteCardSnapshot>,
        val plan: CardSyncPlan,
    ) : IncomingImportPreview

    data class BackendInvite(val invite: BackendSyncInvite) : IncomingImportPreview
}

private fun Context.showLongToast(message: String) {
    val showToast = {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    if (Looper.myLooper() == Looper.getMainLooper()) {
        showToast()
    } else {
        ContextCompat.getMainExecutor(this).execute(showToast)
    }
}
