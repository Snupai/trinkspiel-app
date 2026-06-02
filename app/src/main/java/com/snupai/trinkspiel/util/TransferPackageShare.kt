package com.snupai.trinkspiel.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object TransferPackageShare {
    data class PackageFile(
        val uri: Uri,
        val fileName: String,
    )

    fun createFile(
        context: Context,
        json: String,
        fileName: String = AppBackup.fileName(),
        shareFolder: String = "transfer",
    ): PackageFile {
        val safeFileName = fileName.safeShareFileName()
        val safeShareFolder = shareFolder.safeShareFolderName()
        val shareDir = File(context.cacheDir, "share/$safeShareFolder").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val file = File(shareDir, safeFileName).apply {
            writeText(json, Charsets.UTF_8)
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return PackageFile(uri = uri, fileName = safeFileName)
    }

    fun createIntent(context: Context, packageFile: PackageFile): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, packageFile.fileName)
            putExtra(Intent.EXTRA_TITLE, packageFile.fileName)
            putExtra(Intent.EXTRA_STREAM, packageFile.uri)
            clipData = ClipData.newUri(context.contentResolver, packageFile.fileName, packageFile.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun share(context: Context, packageFile: PackageFile): Boolean =
        try {
            context.startActivity(Intent.createChooser(createIntent(context, packageFile), "Transferpaket teilen"))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }

    private fun String.safeShareFileName(): String =
        trim()
            .ifBlank { AppBackup.fileName() }
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { AppBackup.fileName() }

    private fun String.safeShareFolderName(): String =
        trim()
            .ifBlank { "transfer" }
            .replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .trim('_')
            .ifBlank { "transfer" }
}
