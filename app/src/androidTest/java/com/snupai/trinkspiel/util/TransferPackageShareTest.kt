package com.snupai.trinkspiel.util

import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferPackageShareTest {
    @Test
    fun createFileExposesReadableJsonAttachment() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val json = """{"cards":[],"settings":null}"""

        val packageFile = TransferPackageShare.createFile(context, json)

        assertEquals("content", packageFile.uri.scheme)
        assertEquals(AppBackup.fileName(), packageFile.fileName)
        val exportedJson = context.contentResolver
            .openInputStream(packageFile.uri)
            ?.bufferedReader()
            ?.use { it.readText() }
        assertEquals(json, exportedJson)

        val intent = TransferPackageShare.createIntent(context, packageFile)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/json", intent.type)
        @Suppress("DEPRECATION")
        val streamUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        assertEquals(packageFile.uri, streamUri)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNotNull(intent.clipData)
    }

    @Test
    fun createFileSupportsCardSyncAttachmentNameAndFolder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val json = """{"type":"seemops.card_sync","cards":[]}"""

        val packageFile = TransferPackageShare.createFile(
            context = context,
            json = json,
            fileName = CardSyncPackage.fileName(),
            shareFolder = "card_sync",
        )

        assertEquals("content", packageFile.uri.scheme)
        assertEquals(CardSyncPackage.fileName(), packageFile.fileName)
        assertTrue(packageFile.uri.toString().contains("card_sync_packages"))
        val exportedJson = context.contentResolver
            .openInputStream(packageFile.uri)
            ?.bufferedReader()
            ?.use { it.readText() }
        assertEquals(json, exportedJson)

        val intent = TransferPackageShare.createIntent(context, packageFile)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/json", intent.type)
        assertEquals(CardSyncPackage.fileName(), intent.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    @Test
    fun createFileSupportsBackendInviteAttachmentNameAndFolder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val json = """{"type":"seemops.backend_sync_invite"}"""

        val packageFile = TransferPackageShare.createFile(
            context = context,
            json = json,
            fileName = BackendSyncInvitePackage.fileName(),
            shareFolder = "backend_invite",
        )

        assertEquals("content", packageFile.uri.scheme)
        assertEquals(BackendSyncInvitePackage.fileName(), packageFile.fileName)
        assertTrue(packageFile.uri.toString().contains("backend_invite_packages"))
        val exportedJson = context.contentResolver
            .openInputStream(packageFile.uri)
            ?.bufferedReader()
            ?.use { it.readText() }
        assertEquals(json, exportedJson)

        val intent = TransferPackageShare.createIntent(context, packageFile)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/json", intent.type)
        assertEquals(BackendSyncInvitePackage.fileName(), intent.getStringExtra(Intent.EXTRA_SUBJECT))
    }
}
