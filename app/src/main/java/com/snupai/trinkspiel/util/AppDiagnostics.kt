package com.snupai.trinkspiel.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import com.snupai.trinkspiel.viewmodel.UiState
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject

data class CrashSummary(
    val timestamp: String,
    val exceptionClass: String,
    val message: String,
    val stackTrace: String,
)

data class DiagnosticSnapshot(
    val generatedAt: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val androidSdk: Int,
    val device: String,
    val totalCards: Int,
    val activeCards: Int,
    val packCount: Int,
    val playerCount: Int,
    val mode: String,
    val intensity: String,
    val themeMode: String,
    val dynamicColors: Boolean,
    val ageGateAccepted: Boolean,
    val firstRunSetupCompleted: Boolean,
    val latestCrash: CrashSummary?,
)

object AppDiagnostics {
    private const val PREFS_NAME = "seemops_diagnostics"
    private const val KEY_CRASH_TIMESTAMP = "crashTimestamp"
    private const val KEY_CRASH_CLASS = "crashClass"
    private const val KEY_CRASH_MESSAGE = "crashMessage"
    private const val KEY_CRASH_STACK = "crashStack"

    fun installCrashHandler(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            saveCrash(appContext, throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun latestCrash(context: Context): CrashSummary? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val timestamp = prefs.getString(KEY_CRASH_TIMESTAMP, null) ?: return null
        val exceptionClass = prefs.getString(KEY_CRASH_CLASS, null).orEmpty()
        return CrashSummary(
            timestamp = timestamp,
            exceptionClass = exceptionClass,
            message = prefs.getString(KEY_CRASH_MESSAGE, null).orEmpty(),
            stackTrace = prefs.getString(KEY_CRASH_STACK, null).orEmpty(),
        )
    }

    fun clearCrash(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { clear() }
    }

    fun snapshot(context: Context, state: UiState): DiagnosticSnapshot {
        val appContext = context.applicationContext
        val packageInfo = appContext.packageManager.getPackageInfoCompat(appContext.packageName)
        return DiagnosticSnapshot(
            generatedAt = nowUtc(),
            appVersionName = packageInfo.versionName.orEmpty().ifBlank { "unknown" },
            appVersionCode = packageInfo.longVersionCodeCompat(),
            androidSdk = Build.VERSION.SDK_INT,
            device = listOf(Build.MANUFACTURER, Build.MODEL)
                .joinToString(" ")
                .trim()
                .ifBlank { "unknown" },
            totalCards = state.entries.size,
            activeCards = state.entries.count { it.isEnabled },
            packCount = state.packNames.size,
            playerCount = state.players.size,
            mode = state.mode.id,
            intensity = state.intensity.id,
            themeMode = state.themeMode.id,
            dynamicColors = state.dynamicColors,
            ageGateAccepted = state.ageGateAccepted,
            firstRunSetupCompleted = state.firstRunSetupCompleted,
            latestCrash = latestCrash(appContext),
        )
    }

    fun toJson(snapshot: DiagnosticSnapshot): String =
        JSONObject().apply {
            put("type", "seemops.diagnostics")
            put("generatedAt", snapshot.generatedAt)
            put("appVersionName", snapshot.appVersionName)
            put("appVersionCode", snapshot.appVersionCode)
            put("androidSdk", snapshot.androidSdk)
            put("device", snapshot.device)
            put("totalCards", snapshot.totalCards)
            put("activeCards", snapshot.activeCards)
            put("packCount", snapshot.packCount)
            put("playerCount", snapshot.playerCount)
            put("mode", snapshot.mode)
            put("intensity", snapshot.intensity)
            put("themeMode", snapshot.themeMode)
            put("dynamicColors", snapshot.dynamicColors)
            put("ageGateAccepted", snapshot.ageGateAccepted)
            put("firstRunSetupCompleted", snapshot.firstRunSetupCompleted)
            put("latestCrash", snapshot.latestCrash?.toJsonObject() ?: JSONObject.NULL)
        }.toString(2)

    fun lastIssueToJson(snapshot: DiagnosticSnapshot): String? {
        val crash = snapshot.latestCrash ?: return null
        return JSONObject().apply {
            put("type", "seemops.last_issue")
            put("generatedAt", snapshot.generatedAt)
            put("appVersionName", snapshot.appVersionName)
            put("appVersionCode", snapshot.appVersionCode)
            put("androidSdk", snapshot.androidSdk)
            put("device", snapshot.device)
            put("totalCards", snapshot.totalCards)
            put("activeCards", snapshot.activeCards)
            put("packCount", snapshot.packCount)
            put("mode", snapshot.mode)
            put("intensity", snapshot.intensity)
            put("themeMode", snapshot.themeMode)
            put("latestCrash", crash.toJsonObject())
        }.toString(2)
    }

    fun fileName(): String = "seemops_diagnostics.json"

    fun lastIssueFileName(): String = "seemops_last_issue.json"

    private fun saveCrash(context: Context, throwable: Throwable) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_CRASH_TIMESTAMP, nowUtc())
                putString(KEY_CRASH_CLASS, throwable::class.java.name)
                putString(KEY_CRASH_MESSAGE, throwable.message.orEmpty())
                putString(KEY_CRASH_STACK, throwable.stackTraceString())
            }
    }

    private fun CrashSummary.toJsonObject(): JSONObject =
        JSONObject()
            .put("timestamp", timestamp)
            .put("exceptionClass", exceptionClass)
            .put("message", message)
            .put("stackTrace", stackTrace)

    private fun Throwable.stackTraceString(): String {
        val writer = StringWriter()
        printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    private fun nowUtc(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

    private fun PackageManager.getPackageInfoCompat(packageName: String) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            getPackageInfo(packageName, 0)
        }

    private fun android.content.pm.PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        }
}
