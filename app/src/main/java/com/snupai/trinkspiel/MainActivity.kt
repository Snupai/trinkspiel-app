package com.snupai.trinkspiel

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.snupai.trinkspiel.data.AppDatabase
import com.snupai.trinkspiel.data.DrinkRepository
import com.snupai.trinkspiel.data.GamePreferences
import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_NAME
import com.snupai.trinkspiel.util.IncomingBackupImport
import com.snupai.trinkspiel.viewmodel.DrinkViewModel
import com.snupai.trinkspiel.viewmodel.DrinkViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val incomingBackupImport = MutableStateFlow<IncomingBackupImport?>(null)
    private var incomingBackupImportId = 0L

    private val vm: DrinkViewModel by viewModels {
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "drink_game.db"
        ).addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
        ).build()
        DrinkViewModelFactory(
            DrinkRepository(db.drinkEntryDao()),
            GamePreferences(applicationContext)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TrinkspielApp(
                vm = vm,
                incomingBackupImport = incomingBackupImport,
                onIncomingBackupImportHandled = ::clearIncomingBackupImport,
            )
        }
        handleIncomingBackupIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingBackupIntent(intent)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun handleIncomingBackupIntentForTest(intent: Intent) {
        handleIncomingBackupIntent(intent)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun resetSharedStateForTest() {
        incomingBackupImport.value = null
        vm.updateCardUserProfile(DEFAULT_CARD_USER_NAME, DEFAULT_CARD_USER_NAME)
        vm.updateRemoteSyncConfig(endpointUrl = "", accessToken = "", role = "")
    }

    private fun handleIncomingBackupIntent(intent: Intent?) {
        if (intent == null || !intent.isBackupImportIntent()) return

        lifecycleScope.launch {
            try {
                val payload = withContext(Dispatchers.IO) {
                    readIncomingBackupJson(intent)
                }
                incomingBackupImport.value = IncomingBackupImport(
                    id = ++incomingBackupImportId,
                    sourceLabel = payload.sourceLabel,
                    json = payload.json,
                )
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Transferpaket konnte nicht geöffnet werden: ${e.message}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun clearIncomingBackupImport(id: Long) {
        if (incomingBackupImport.value?.id == id) {
            incomingBackupImport.value = null
        }
    }

    private fun readIncomingBackupJson(intent: Intent): IncomingBackupPayload {
        val uri = intent.incomingJsonUri()
        val json = if (uri != null) {
            contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Datei konnte nicht geöffnet werden")
        } else {
            intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.takeIf { it.trim().isNotBlank() }
                ?: error("Keine JSON-Datei gefunden")
        }
        return IncomingBackupPayload(
            sourceLabel = uri?.readableSourceLabel() ?: "Geteiltes JSON",
            json = json,
        )
    }

    private fun Uri.readableSourceLabel(): String =
        lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "Transferpaket"

    private fun Intent.isBackupImportIntent(): Boolean =
        action == Intent.ACTION_SEND || action == Intent.ACTION_VIEW

    private fun Intent.incomingJsonUri(): Uri? =
        when (action) {
            Intent.ACTION_SEND -> streamUri()
            Intent.ACTION_VIEW -> data
            else -> null
        }

    private fun Intent.streamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }

    private data class IncomingBackupPayload(
        val sourceLabel: String,
        val json: String,
    )
}
