package com.snupai.trinkspiel.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snupai.trinkspiel.data.DrinkEntry
import com.snupai.trinkspiel.data.PackTemplate
import com.snupai.trinkspiel.model.CardCategory
import com.snupai.trinkspiel.model.CardSyncStatus
import com.snupai.trinkspiel.model.EntrySortMode
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.model.normalizedCardUserId
import com.snupai.trinkspiel.model.normalizedCardUserName
import com.snupai.trinkspiel.ui.dialog.AddEntryDialog
import com.snupai.trinkspiel.ui.dialog.ConfirmDeleteDialog
import com.snupai.trinkspiel.ui.dialog.EditEntryDialog
import com.snupai.trinkspiel.util.ImportExport
import com.snupai.trinkspiel.viewmodel.CardImportPreview
import com.snupai.trinkspiel.viewmodel.CardUserSummary
import com.snupai.trinkspiel.viewmodel.DrinkViewModel
import com.snupai.trinkspiel.viewmodel.ImportSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryListScreen(
    vm: DrinkViewModel,
    onBack: () -> Unit
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var editEntry by remember { mutableStateOf<DrinkEntry?>(null) }
    var deleteEntry by remember { mutableStateOf<DrinkEntry?>(null) }
    var deletePackName by remember { mutableStateOf<String?>(null) }
    var deleteVisibleEntries by remember { mutableStateOf<List<DrinkEntry>?>(null) }
    var rejectReviewEntries by remember { mutableStateOf<List<DrinkEntry>?>(null) }
    var showDeleteAll by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showTemplatesDialog by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var pendingEntryImport by remember { mutableStateOf<PendingEntryImport?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedQuestionLevel by remember { mutableStateOf<Int?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedPack by remember { mutableStateOf<String?>(null) }
    var selectedUserFilter by remember { mutableStateOf<EntryUserFilter?>(null) }
    var selectedPendingReviewOnly by remember { mutableStateOf(false) }
    var selectedSyncStatus by remember { mutableStateOf<String?>(null) }
    var sortMode by remember { mutableStateOf(EntrySortMode.NEWEST) }
    var pendingExportEntries by remember { mutableStateOf<List<DrinkEntry>>(emptyList()) }

    val libraryEntries = state.entries.filter(state::isActiveLibraryEntry)
    val packNames = libraryEntries
        .map { it.packName }
        .distinct()
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
    val questionLevelCounts = libraryEntries
        .groupingBy { QuestionLevel.fromId(it.questionLevel).id }
        .eachCount()
    val syncStatusCounts = libraryEntries
        .groupingBy { CardSyncStatus.fromId(it.syncStatus).id }
        .eachCount()
    val cardUsersWithLibraryCards = cardUsersForEntries(libraryEntries, state.cardUsers)
    val visibleEntries = libraryEntries
        .filter { entry ->
            val query = searchQuery.trim()
            query.isBlank() ||
                entry.text.contains(query, ignoreCase = true) ||
                entry.packName.contains(query, ignoreCase = true) ||
                entry.ownerName.contains(query, ignoreCase = true) ||
                entry.contributorName.contains(query, ignoreCase = true)
        }
        .filter { entry -> selectedQuestionLevel == null || QuestionLevel.fromId(entry.questionLevel).id == selectedQuestionLevel }
        .filter { entry -> selectedCategory == null || entry.category == selectedCategory }
        .filter { entry -> selectedPack == null || entry.packName == selectedPack }
        .filter { entry -> selectedUserFilter?.matches(entry) ?: true }
        .filter { entry -> !selectedPendingReviewOnly || entry.isPendingReview }
        .filter { entry -> selectedSyncStatus == null || CardSyncStatus.fromId(entry.syncStatus).id == selectedSyncStatus }
        .sortedWith(sortMode.comparator())

    val pendingReviewCount = libraryEntries.count { it.isPendingReview }
    val reviewContributorSummaries = reviewContributorSummariesForEntries(libraryEntries)
    val activeLibrarySyncOpenCount = syncStatusCounts
        .filterKeys { CardSyncStatus.fromId(it) != CardSyncStatus.SYNCED }
        .values
        .sum()
    val syncOpenEntries = visibleEntries.filter { CardSyncStatus.fromId(it.syncStatus) != CardSyncStatus.SYNCED }
    val remoteDirtyEntries = visibleEntries.filter {
        it.remoteId.isNotBlank() && CardSyncStatus.fromId(it.syncStatus) != CardSyncStatus.SYNCED
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val entriesToExport = pendingExportEntries
        if (uri != null) {
            scope.launch {
                try {
                    val json = ImportExport.toJson(
                        entriesToExport,
                        packName = ImportExport.packNameFor(entriesToExport)
                    )
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(json.toByteArray(Charsets.UTF_8))
                        } ?: error("Datei konnte nicht geöffnet werden")
                    }
                    snackbarHostState.showSnackbar("${entriesToExport.size} Einträge exportiert")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Export fehlgeschlagen: ${e.message}")
                }
            }
        }
        pendingExportEntries = emptyList()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingImport = uri
    }

    LaunchedEffect(pendingImport) {
        pendingImport?.let { uri ->
            try {
                val entries = withContext(Dispatchers.IO) {
                    val json = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() } ?: ""
                    ImportExport.fromJson(json)
                }
                pendingEntryImport = PendingEntryImport(
                    entries = entries,
                    preview = vm.previewCardImport(entries),
                )
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Import-Prüfung fehlgeschlagen: ${e.message}")
            }
            pendingImport = null
        }
    }

    LaunchedEffect(packNames, selectedPack) {
        val currentPack = selectedPack
        if (currentPack != null && currentPack !in packNames) {
            selectedPack = null
        }
    }

    LaunchedEffect(cardUsersWithLibraryCards, selectedUserFilter) {
        val currentFilter = selectedUserFilter ?: return@LaunchedEffect
        val user = cardUsersWithLibraryCards.firstOrNull { it.userId == currentFilter.userId }
        val hasCards = when (currentFilter.kind) {
            EntryUserFilterKind.OWNER -> (user?.ownedCount ?: 0) > 0
            EntryUserFilterKind.CONTRIBUTOR -> (user?.contributedCount ?: 0) > 0
        }
        if (!hasCards) selectedUserFilter = null
    }

    LaunchedEffect(pendingReviewCount, selectedPendingReviewOnly) {
        if (pendingReviewCount == 0 && selectedPendingReviewOnly) {
            selectedPendingReviewOnly = false
        }
    }

    LaunchedEffect(syncStatusCounts, selectedSyncStatus) {
        val currentSyncStatus = selectedSyncStatus ?: return@LaunchedEffect
        if ((syncStatusCounts[currentSyncStatus] ?: 0) == 0) {
            selectedSyncStatus = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Einträge (${visibleEntries.size}/${libraryEntries.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            pendingExportEntries = visibleEntries
                            exportLauncher.launch(ImportExport.fileNameFor(visibleEntries))
                        },
                        enabled = visibleEntries.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Upload, "Exportieren")
                    }
                    IconButton(onClick = {
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    }) {
                        Icon(Icons.Default.Download, "Importieren")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Mehr")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Pack-Vorlagen") },
                                onClick = {
                                    showMenu = false
                                    showTemplatesDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Alle Standardpacks laden") },
                                onClick = {
                                    showMenu = false
                                    scope.launch {
                                        val summary = vm.addAllBuiltInPacks()
                                        snackbarHostState.showSnackbar(summary.message("geladen"))
                                    }
                                }
                            )
                            vm.builtInPacks.forEach { pack ->
                                DropdownMenuItem(
                                    text = { Text("${pack.name} laden") },
                                    onClick = {
                                        showMenu = false
                                        scope.launch {
                                            val summary = vm.addBuiltInPack(pack.id)
                                            snackbarHostState.showSnackbar(summary.message("geladen"))
                                        }
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Aktuelle Ansicht teilen") },
                                onClick = {
                                    showMenu = false
                                    scope.launch {
                                        try {
                                            shareEntries(context, visibleEntries)
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar("Teilen fehlgeschlagen: ${e.message}")
                                        }
                                    }
                                },
                                enabled = visibleEntries.isNotEmpty()
                            )
                            DropdownMenuItem(
                                text = { Text("Sync-offene Ansicht teilen") },
                                onClick = {
                                    showMenu = false
                                    scope.launch {
                                        try {
                                            shareEntries(context, syncOpenEntries)
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar("Teilen fehlgeschlagen: ${e.message}")
                                        }
                                    }
                                },
                                enabled = syncOpenEntries.isNotEmpty()
                            )
                            DropdownMenuItem(
                                text = { Text("Remote-Karten als synchron markieren") },
                                onClick = {
                                    showMenu = false
                                    scope.launch {
                                        val changed = vm.markRemoteEntriesSynced(visibleEntries)
                                        snackbarHostState.showSnackbar("$changed Remote-Karten synchronisiert")
                                    }
                                },
                                enabled = remoteDirtyEntries.isNotEmpty()
                            )
                            DropdownMenuItem(
                                text = { Text("Review-Karten freigeben") },
                                onClick = {
                                    showMenu = false
                                    scope.launch {
                                        val changed = vm.approveEntries(visibleEntries)
                                        snackbarHostState.showSnackbar("$changed Review-Karten freigegeben")
                                    }
                                },
                                enabled = visibleEntries.any { it.isPendingReview }
                            )
                            DropdownMenuItem(
                                text = { Text("Review-Karten ablehnen") },
                                onClick = {
                                    showMenu = false
                                    rejectReviewEntries = visibleEntries.filter { it.isPendingReview }
                                },
                                enabled = visibleEntries.any { it.isPendingReview }
                            )
                            DropdownMenuItem(
                                text = { Text("Sichtbare Karten pausieren") },
                                onClick = {
                                    showMenu = false
                                    scope.launch {
                                        val changed = vm.setEntriesEnabled(visibleEntries, isEnabled = false)
                                        snackbarHostState.showSnackbar("$changed Karten pausiert")
                                    }
                                },
                                enabled = visibleEntries.any { it.isEnabled && !it.isPendingReview }
                            )
                            DropdownMenuItem(
                                text = { Text("Sichtbare Karten aktivieren") },
                                onClick = {
                                    showMenu = false
                                    scope.launch {
                                        val changed = vm.setEntriesEnabled(visibleEntries, isEnabled = true)
                                        snackbarHostState.showSnackbar("$changed Karten aktiviert")
                                    }
                                },
                                enabled = visibleEntries.any { !it.isEnabled && !it.isPendingReview }
                            )
                            DropdownMenuItem(
                                text = { Text("Sichtbare Karten löschen") },
                                onClick = {
                                    showMenu = false
                                    deleteVisibleEntries = visibleEntries
                                },
                                enabled = visibleEntries.isNotEmpty()
                            )
                            DropdownMenuItem(
                                text = { Text("Pack in aktiver Bibliothek löschen") },
                                onClick = {
                                    showMenu = false
                                    deletePackName = selectedPack
                                },
                                enabled = selectedPack != null
                            )
                            DropdownMenuItem(
                                text = { Text("Aktive Bibliothek löschen") },
                                onClick = {
                                    showMenu = false
                                    showDeleteAll = true
                                },
                                enabled = libraryEntries.isNotEmpty()
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Hinzufügen")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            EntrySummary(
                totalCount = libraryEntries.size,
                visibleCount = visibleEntries.size,
                activeCount = libraryEntries.count { it.isEnabled && !it.isPendingReview },
                pendingReviewCount = pendingReviewCount,
                syncOpenCount = activeLibrarySyncOpenCount,
                packCount = packNames.size,
            )
            EntryTools(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                selectedQuestionLevel = selectedQuestionLevel,
                questionLevelCounts = questionLevelCounts,
                onQuestionLevelSelected = { selectedQuestionLevel = it },
                pendingReviewCount = pendingReviewCount,
                reviewContributorSummaries = reviewContributorSummaries,
                selectedPendingReviewOnly = selectedPendingReviewOnly,
                onPendingReviewOnlyChange = { selectedPendingReviewOnly = it },
                syncStatusCounts = syncStatusCounts,
                selectedSyncStatus = selectedSyncStatus,
                onSyncStatusSelected = { selectedSyncStatus = it },
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it },
                selectedPack = selectedPack,
                packNames = packNames,
                onPackSelected = { selectedPack = it },
                cardUsers = cardUsersWithLibraryCards,
                selectedUserFilter = selectedUserFilter,
                onUserFilterSelected = { selectedUserFilter = it },
                sortMode = sortMode,
                onSortModeSelected = { sortMode = it },
            )

            if (visibleEntries.isEmpty()) {
                val noEntries = libraryEntries.isEmpty()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        if (noEntries) Icons.Default.Style else Icons.Default.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (noEntries) "Keine Einträge" else "Keine Treffer",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (noEntries) "Diese Bibliothek ist leer. Drück + zum Hinzufügen, lade ein Pack oder starte mit einer Vorlage."
                        else "Passe Suche, Stufe, Kategorie, User, Sync oder Sortierung an.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (noEntries) {
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { showTemplatesDialog = true },
                            modifier = Modifier.testTag("entry_empty_pack_templates_button"),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(Icons.Default.Style, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Pack-Vorlagen")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleEntries, key = { it.id }) { entry ->
                        EntryRow(
                            entry = entry,
                            drinkLabel = state.drinkLabel(entry.drinks),
                            onEdit = { editEntry = entry },
                            onDelete = { deleteEntry = entry },
                            onToggleEnabled = { vm.toggleEntryEnabled(entry) },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEntryDialog(
            onDismiss = { showAddDialog = false },
            defaultOwnerUserId = state.cardOwnerUserId,
            defaultOwnerName = state.cardOwnerName,
            defaultContributorUserId = state.activeContributorUserId,
            defaultContributorName = state.activeContributorName,
            cardUsers = state.cardUsers,
            onSave = { text, drinks, category, packName, isEnabled, isPendingReview, questionLevel, ownerUserId, ownerName, contributorUserId, contributorName ->
                scope.launch {
                    val added = vm.addEntry(
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
                    )
                    if (added) {
                        showAddDialog = false
                        snackbarHostState.showSnackbar("Eintrag gespeichert")
                    } else {
                        snackbarHostState.showSnackbar("Duplikat oder ungültiger Eintrag")
                    }
                }
            }
        )
    }

    editEntry?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onDismiss = { editEntry = null },
            cardUsers = state.cardUsers,
            onSave = { updated ->
                scope.launch {
                    val saved = vm.updateEntry(updated)
                    if (saved) {
                        editEntry = null
                        snackbarHostState.showSnackbar("Eintrag aktualisiert")
                    } else {
                        snackbarHostState.showSnackbar("Duplikat oder ungültiger Eintrag")
                    }
                }
            }
        )
    }

    deleteEntry?.let { entry ->
        ConfirmDeleteDialog(
            message = "\"${entry.text.take(50)}\" wird gelöscht.",
            onConfirm = {
                vm.deleteEntry(entry)
                deleteEntry = null
            },
            onDismiss = { deleteEntry = null }
        )
    }

    if (showDeleteAll) {
        ConfirmDeleteDialog(
            title = "Aktive Bibliothek löschen?",
            message = "${libraryEntries.size} Einträge in ${state.cardOwnerName} werden unwiderruflich gelöscht.",
            onConfirm = {
                vm.deleteEntries(libraryEntries)
                showDeleteAll = false
            },
            onDismiss = { showDeleteAll = false }
        )
    }

    deletePackName?.let { packName ->
        val count = state.entries.count { it.packName == packName && state.isActiveLibraryEntry(it) }
        ConfirmDeleteDialog(
            title = "Pack löschen?",
            message = "\"$packName\" mit $count Einträgen in ${state.cardOwnerName} wird unwiderruflich gelöscht.",
            onConfirm = {
                vm.deletePack(packName)
                if (selectedPack == packName) selectedPack = null
                deletePackName = null
            },
            onDismiss = { deletePackName = null }
        )
    }

    deleteVisibleEntries?.let { entries ->
        ConfirmDeleteDialog(
            title = "Sichtbare Karten löschen?",
            message = "${entries.size} sichtbare Einträge werden unwiderruflich gelöscht.",
            onConfirm = {
                vm.deleteEntries(entries)
                deleteVisibleEntries = null
            },
            onDismiss = { deleteVisibleEntries = null }
        )
    }

    rejectReviewEntries?.let { entries ->
        ConfirmDeleteDialog(
            title = "Review-Karten ablehnen?",
            message = "${entries.size} Review-Karten werden gelöscht.",
            onConfirm = {
                scope.launch {
                    try {
                        val rejected = vm.rejectReviewEntries(entries)
                        rejectReviewEntries = null
                        snackbarHostState.showSnackbar("$rejected Review-Karten abgelehnt")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Review-Ablehnung fehlgeschlagen: ${e.message}")
                    }
                }
            },
            onDismiss = { rejectReviewEntries = null }
        )
    }

    pendingEntryImport?.let { pending ->
        EntryImportPreviewDialog(
            preview = pending.preview,
            onConfirm = { pauseImportedCards ->
                scope.launch {
                    val summary = vm.addEntries(
                        entries = pending.entries,
                        pauseImportedCards = pauseImportedCards,
                    )
                    pendingEntryImport = null
                    val action = if (pauseImportedCards) {
                        "als Entwürfe importiert"
                    } else {
                        "importiert"
                    }
                    snackbarHostState.showSnackbar(summary.message(action))
                }
            },
            onDismiss = { pendingEntryImport = null },
        )
    }

    if (showTemplatesDialog) {
        PackTemplateDialog(
            templates = vm.packTemplates,
            onCreate = { template ->
                showTemplatesDialog = false
                scope.launch {
                    val summary = vm.addPackTemplate(template.id)
                    snackbarHostState.showSnackbar(summary.message("erstellt"))
                }
            },
            onDismiss = { showTemplatesDialog = false },
        )
    }
}

private data class PendingEntryImport(
    val entries: List<DrinkEntry>,
    val preview: CardImportPreview,
)

private enum class EntryUserFilterKind {
    OWNER,
    CONTRIBUTOR,
}

private data class EntryUserFilter(
    val kind: EntryUserFilterKind,
    val userId: String,
) {
    fun matches(entry: DrinkEntry): Boolean =
        when (kind) {
            EntryUserFilterKind.OWNER -> normalizedCardUserId(entry.ownerUserId, entry.ownerName) == userId
            EntryUserFilterKind.CONTRIBUTOR -> normalizedCardUserId(entry.contributorUserId, entry.contributorName) == userId
        }
}

internal fun cardUsersForEntries(
    entries: List<DrinkEntry>,
    savedUsers: List<CardUserSummary>,
): List<CardUserSummary> {
    val displayNamesById = savedUsers.associate { it.userId to it.displayName }
    val entryNamesById = buildMap {
        entries.forEach { entry ->
            val ownerName = normalizedCardUserName(entry.ownerName)
            val contributorName = normalizedCardUserName(entry.contributorName)
            putIfAbsent(normalizedCardUserId(entry.ownerUserId, ownerName), ownerName)
            putIfAbsent(normalizedCardUserId(entry.contributorUserId, contributorName), contributorName)
        }
    }

    val ownedCounts = entries
        .groupingBy { entry -> normalizedCardUserId(entry.ownerUserId, entry.ownerName) }
        .eachCount()
    val contributedCounts = entries
        .groupingBy { entry -> normalizedCardUserId(entry.contributorUserId, entry.contributorName) }
        .eachCount()
    val userIds = (entryNamesById.keys + ownedCounts.keys + contributedCounts.keys)
        .filter { it.isNotBlank() }
        .toSet()

    return userIds
        .map { userId ->
            CardUserSummary(
                userId = userId,
                displayName = displayNamesById[userId]
                    ?: entryNamesById[userId]
                    ?: normalizedCardUserName(""),
                ownedCount = ownedCounts[userId] ?: 0,
                contributedCount = contributedCounts[userId] ?: 0,
            )
        }
        .filter { it.totalCount > 0 }
        .sortedWith(
            compareByDescending<CardUserSummary> { it.totalCount }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
        )
}

internal data class ReviewContributorSummary(
    val userId: String,
    val displayName: String,
    val cardCount: Int,
    val levelCounts: Map<Int, Int>,
)

internal fun reviewContributorSummariesForEntries(
    entries: List<DrinkEntry>,
): List<ReviewContributorSummary> {
    val reviewEntries = entries.filter { it.isPendingReview }
    val displayNamesById = reviewEntries
        .associate { entry ->
            val contributorName = normalizedCardUserName(entry.contributorName)
            normalizedCardUserId(entry.contributorUserId, contributorName) to contributorName
        }
    return reviewEntries
        .groupBy { entry -> normalizedCardUserId(entry.contributorUserId, entry.contributorName) }
        .map { (userId, userEntries) ->
            ReviewContributorSummary(
                userId = userId,
                displayName = displayNamesById[userId] ?: normalizedCardUserName(userEntries.first().contributorName),
                cardCount = userEntries.size,
                levelCounts = userEntries
                    .groupingBy { entry -> QuestionLevel.fromId(entry.questionLevel).id }
                    .eachCount(),
            )
        }
        .sortedWith(
            compareByDescending<ReviewContributorSummary> { it.cardCount }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
        )
}

@Composable
private fun PackTemplateDialog(
    templates: List<PackTemplate>,
    onCreate: (PackTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pack-Vorlagen") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Vorlagen legen pausierte Entwurfskarten an. Bearbeite sie, aktiviere passende Karten und halte dein Pack ausgewogen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                templates.forEach { template ->
                    PackTemplateRow(
                        template = template,
                        onCreate = { onCreate(template) },
                    )
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

@Composable
private fun PackTemplateRow(
    template: PackTemplate,
    onCreate: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                template.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(template.description, style = MaterialTheme.typography.bodySmall)
            Text(
                "${template.entries.size} Entwürfe · ${template.recommendedMode.label} / ${template.recommendedIntensity.label}",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                "Kategorien: ${template.categorySummary()}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Button(
                onClick = onCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pack_template_create_${template.id}"),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    "${template.name} erstellen",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EntryImportPreviewDialog(
    preview: CardImportPreview,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var pauseImportedCards by remember(preview) {
        mutableStateOf(false)
    }
    val externalReviewCards = preview.externalContributorCounts.sumOf { it.cardCount }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import prüfen") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Die Datei wird erst nach deiner Bestätigung importiert.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Karten in Datei: ${preview.totalCards}")
                Text("Neue Karten: ${preview.newCards}")
                if (preview.updatedCards > 0) {
                    Text("Aktualisiert: ${preview.updatedCards}")
                }
                Text("Übersprungen: ${preview.skippedCards}")
                if (preview.packNames.isNotEmpty()) {
                    Text("Packs: ${preview.packNames.previewList()}")
                }
                if (preview.questionLevelCounts.isNotEmpty()) {
                    Text("Stufen: ${preview.questionLevelCounts.levelPreview()}")
                }
                if (preview.contributorCounts.isNotEmpty()) {
                    Text("Beiträger: ${preview.contributorCounts.previewList { "${it.displayName} (${it.cardCount})" }}")
                }
                if (externalReviewCards > 0) {
                    Text(
                        "Review-pflichtige Fremdbeiträge: $externalReviewCards",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (preview.pendingReviewCards > externalReviewCards) {
                    Text(
                        "Karten im Review: ${preview.pendingReviewCards}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = pauseImportedCards,
                        onCheckedChange = { pauseImportedCards = it },
                        enabled = preview.hasCardChanges,
                    )
                    Text(
                        "Zusätzlich alle importierten Karten pausieren",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!preview.hasCardChanges) {
                    Text(
                        "In dieser Datei wurden keine neuen oder aktualisierbaren Karten gefunden.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(pauseImportedCards) },
                enabled = preview.hasCardChanges,
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

@Composable
private fun EntrySummary(
    totalCount: Int,
    visibleCount: Int,
    activeCount: Int,
    pendingReviewCount: Int,
    syncOpenCount: Int,
    packCount: Int,
) {
    val pausedCount = totalCount - activeCount - pendingReviewCount
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        FlowRow(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryStat(
                value = visibleCount.toString(),
                label = "sichtbar",
                modifier = Modifier.widthIn(min = 72.dp),
            )
            SummaryStat(
                value = activeCount.toString(),
                label = "aktiv",
                modifier = Modifier.widthIn(min = 72.dp),
            )
            SummaryStat(
                value = pausedCount.toString(),
                label = "pausiert",
                modifier = Modifier.widthIn(min = 72.dp),
            )
            SummaryStat(
                value = pendingReviewCount.toString(),
                label = "Review",
                modifier = Modifier.widthIn(min = 72.dp),
            )
            SummaryStat(
                value = syncOpenCount.toString(),
                label = "Sync offen",
                modifier = Modifier.widthIn(min = 72.dp),
            )
            SummaryStat(
                value = packCount.toString(),
                label = if (packCount == 1) "Pack" else "Packs",
                modifier = Modifier.widthIn(min = 72.dp),
            )
        }
    }
}

@Composable
private fun SummaryStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EntryTools(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedQuestionLevel: Int?,
    questionLevelCounts: Map<Int, Int>,
    onQuestionLevelSelected: (Int?) -> Unit,
    pendingReviewCount: Int,
    reviewContributorSummaries: List<ReviewContributorSummary>,
    selectedPendingReviewOnly: Boolean,
    onPendingReviewOnlyChange: (Boolean) -> Unit,
    syncStatusCounts: Map<String, Int>,
    selectedSyncStatus: String?,
    onSyncStatusSelected: (String?) -> Unit,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    selectedPack: String?,
    packNames: List<String>,
    onPackSelected: (String?) -> Unit,
    cardUsers: List<CardUserSummary>,
    selectedUserFilter: EntryUserFilter?,
    onUserFilterSelected: (EntryUserFilter?) -> Unit,
    sortMode: EntrySortMode,
    onSortModeSelected: (EntrySortMode) -> Unit,
) {
    val cardUsersWithCards = cardUsers.filter { it.totalCount > 0 }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text("Suchen") },
                placeholder = { Text("Text, Pack oder User") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (pendingReviewCount > 0) {
                Text("Review", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedPendingReviewOnly,
                        onClick = { onPendingReviewOnlyChange(!selectedPendingReviewOnly) },
                        label = { Text("Review-Karten ($pendingReviewCount)") }
                    )
                }
                if (reviewContributorSummaries.isNotEmpty()) {
                    Text(
                        "Nach Beiträger",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        reviewContributorSummaries.forEach { summary ->
                            val filter = EntryUserFilter(EntryUserFilterKind.CONTRIBUTOR, summary.userId)
                            FilterChip(
                                selected = selectedPendingReviewOnly && selectedUserFilter == filter,
                                onClick = {
                                    onPendingReviewOnlyChange(true)
                                    onUserFilterSelected(filter)
                                },
                                label = {
                                    Text(
                                        "${summary.displayName}: ${summary.cardCount} (${summary.levelCounts.levelPreview()})",
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }
            }
            if (syncStatusCounts.isNotEmpty()) {
                Text("Sync", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedSyncStatus == null,
                        onClick = { onSyncStatusSelected(null) },
                        label = { Text("Alle") }
                    )
                    CardSyncStatus.entries.forEach { status ->
                        val count = syncStatusCounts[status.id] ?: 0
                        if (count > 0) {
                            FilterChip(
                                selected = selectedSyncStatus == status.id,
                                onClick = { onSyncStatusSelected(status.id) },
                                label = { Text("${status.label} ($count)") }
                            )
                        }
                    }
                }
            }
            Text("Stufe", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedQuestionLevel == null,
                    onClick = { onQuestionLevelSelected(null) },
                    label = { Text("Alle") }
                )
                QuestionLevel.entries.forEach { level ->
                    val count = questionLevelCounts[level.id] ?: 0
                    FilterChip(
                        selected = selectedQuestionLevel == level.id,
                        onClick = { onQuestionLevelSelected(level.id) },
                        label = { Text("${level.label} ($count)") }
                    )
                }
            }
            Text("Kategorie", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onCategorySelected(null) },
                    label = { Text("Alle") }
                )
                CardCategory.entries.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category.id,
                        onClick = { onCategorySelected(category.id) },
                        label = { Text(category.label) }
                    )
                }
            }
            if (packNames.size > 1) {
                Text("Pack", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedPack == null,
                        onClick = { onPackSelected(null) },
                        label = { Text("Alle") }
                    )
                    packNames.forEach { packName ->
                        FilterChip(
                            selected = selectedPack == packName,
                            onClick = { onPackSelected(packName) },
                            label = {
                                Text(
                                    packName,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
            if (cardUsersWithCards.isNotEmpty()) {
                Text("User", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedUserFilter == null,
                        onClick = { onUserFilterSelected(null) },
                        label = { Text("Alle") }
                    )
                    cardUsersWithCards.forEach { user ->
                        if (user.ownedCount > 0) {
                            val filter = EntryUserFilter(EntryUserFilterKind.OWNER, user.userId)
                            FilterChip(
                                selected = selectedUserFilter == filter,
                                onClick = { onUserFilterSelected(filter) },
                                label = {
                                    Text(
                                        "User: ${user.displayName} (${user.ownedCount})",
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            )
                        }
                        if (user.contributedCount > 0) {
                            val filter = EntryUserFilter(EntryUserFilterKind.CONTRIBUTOR, user.userId)
                            FilterChip(
                                selected = selectedUserFilter == filter,
                                onClick = { onUserFilterSelected(filter) },
                                label = {
                                    Text(
                                        "von ${user.displayName} (${user.contributedCount})",
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            )
                        }
                    }
                }
            }
            Text("Sortierung", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EntrySortMode.entries.forEach { option ->
                    FilterChip(
                        selected = sortMode == option,
                        onClick = { onSortModeSelected(option) },
                        label = { Text(option.label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: DrinkEntry,
    drinkLabel: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: () -> Unit,
) {
    val level = QuestionLevel.fromId(entry.questionLevel)
    val syncStatus = CardSyncStatus.fromId(entry.syncStatus)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (entry.isEnabled && !entry.isPendingReview) 1f else 0.6f)
            ) {
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    EntryMetaPill(level.label)
                    EntryMetaPill(CardCategory.fromId(entry.category).label)
                    EntryMetaPill("${entry.drinks} $drinkLabel")
                    EntryMetaPill(entry.packName)
                    EntryMetaPill("User: ${entry.ownerName.ifBlank { "Lokal" }}")
                    if (!entry.contributorName.equals(entry.ownerName, ignoreCase = true)) {
                        EntryMetaPill("von ${entry.contributorName.ifBlank { "Lokal" }}")
                    }
                    if (entry.isPendingReview) {
                        EntryMetaPill("Review", tone = EntryMetaTone.Review)
                    } else if (!entry.isEnabled) {
                        EntryMetaPill("pausiert", tone = EntryMetaTone.Muted)
                    }
                    if (syncStatus != CardSyncStatus.LOCAL || entry.remoteId.isNotBlank()) {
                        EntryMetaPill("Sync: ${syncStatus.label}", tone = EntryMetaTone.Sync)
                    }
                }
            }
            val toggleAction = when {
                entry.isPendingReview -> "Review-Karte freigeben"
                entry.isEnabled -> "Karte pausieren"
                else -> "Karte aktivieren"
            }
            IconButton(
                onClick = onToggleEnabled,
                modifier = Modifier.semantics {
                    contentDescription = entry.actionDescription(toggleAction)
                },
            ) {
                Icon(
                    when {
                        entry.isPendingReview -> Icons.Default.CheckCircle
                        entry.isEnabled -> Icons.Default.Visibility
                        else -> Icons.Default.VisibilityOff
                    },
                    contentDescription = null,
                    tint = if (entry.isEnabled || entry.isPendingReview) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.semantics {
                    contentDescription = entry.actionDescription("Karte bearbeiten")
                },
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.semantics {
                    contentDescription = entry.actionDescription("Karte löschen")
                },
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private enum class EntryMetaTone {
    Neutral,
    Review,
    Muted,
    Sync,
}

@Composable
private fun EntryMetaPill(
    text: String,
    tone: EntryMetaTone = EntryMetaTone.Neutral,
) {
    val colorScheme = MaterialTheme.colorScheme
    val colors = when (tone) {
        EntryMetaTone.Neutral -> colorScheme.surface to colorScheme.onSurfaceVariant
        EntryMetaTone.Review -> colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer
        EntryMetaTone.Muted -> colorScheme.surface to colorScheme.onSurfaceVariant
        EntryMetaTone.Sync -> colorScheme.secondaryContainer to colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = Modifier.widthIn(max = 220.dp),
        color = colors.first,
        contentColor = colors.second,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun EntrySortMode.comparator(): Comparator<DrinkEntry> =
    when (this) {
        EntrySortMode.NEWEST -> compareByDescending<DrinkEntry> { it.id }
        EntrySortMode.OLDEST -> compareBy<DrinkEntry> { it.id }
        EntrySortMode.LEVEL -> compareBy<DrinkEntry> { QuestionLevel.fromId(it.questionLevel).id }
            .thenByDescending { it.id }
        EntrySortMode.DRINKS_DESC -> compareByDescending<DrinkEntry> { it.drinks }.thenBy { it.text.lowercase() }
        EntrySortMode.DRINKS_ASC -> compareBy<DrinkEntry> { it.drinks }.thenBy { it.text.lowercase() }
        EntrySortMode.CATEGORY -> compareBy<DrinkEntry> { CardCategory.fromId(it.category).label }.thenBy { it.text.lowercase() }
        EntrySortMode.USER -> compareBy<DrinkEntry> { it.ownerName.lowercase() }
            .thenBy { it.contributorName.lowercase() }
            .thenBy { it.text.lowercase() }
        EntrySortMode.SYNC -> compareBy<DrinkEntry> { CardSyncStatus.fromId(it.syncStatus).ordinal }
            .thenByDescending { it.updatedAtMillis }
            .thenBy { it.text.lowercase() }
        EntrySortMode.TEXT -> compareBy<DrinkEntry> { it.text.lowercase() }
    }

private fun DrinkEntry.actionDescription(action: String): String {
    val shortText = text
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(48)
    return if (shortText.isBlank()) action else "$action: $shortText"
}

private fun ImportSummary.message(action: String): String =
    buildString {
        append("$added Einträge $action")
        if (updated > 0) {
            append(", $updated aktualisiert")
        }
        if (skippedDuplicates > 0) {
            append(", $skippedDuplicates Duplikate übersprungen")
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

private fun PackTemplate.categorySummary(): String =
    entries
        .groupingBy { CardCategory.fromId(it.category).label }
        .eachCount()
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)
        .entries
        .joinToString(", ") { (category, count) -> "$category: $count" }

private fun shareEntries(context: android.content.Context, entries: List<DrinkEntry>) {
    val packName = ImportExport.packNameFor(entries, fallback = "Seemops Pack")
    val json = ImportExport.toJson(entries, packName = packName)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_SUBJECT, ImportExport.fileNameFor(entries, fallback = "seemops_pack"))
        putExtra(Intent.EXTRA_TEXT, json)
    }
    context.startActivity(Intent.createChooser(intent, "Pack teilen"))
}
