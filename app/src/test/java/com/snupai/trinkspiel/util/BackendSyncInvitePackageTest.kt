package com.snupai.trinkspiel.util

import com.snupai.trinkspiel.model.cardUserIdForName
import com.snupai.trinkspiel.sync.RemoteSyncConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendSyncInvitePackageTest {
    @Test
    fun roundTripKeepsBackendLibraryAndContributorConfig() {
        val ownerId = cardUserIdForName("WG Bibliothek")
        val contributorId = "account_mika"

        val json = BackendSyncInvitePackage.toJson(
            config = RemoteSyncConfig(
                endpointUrl = " https://sync.example.test/api/ ",
                accessToken = " writer-token ",
            ),
            libraryOwnerUserId = ownerId,
            libraryOwnerName = "WG Bibliothek",
            contributorUserId = contributorId,
            contributorName = "Mika",
            role = "write",
        )
        val root = JSONObject(json)
        val parsed = BackendSyncInvitePackage.fromJson(json)

        assertEquals("seemops.backend_sync_invite", root.getString("type"))
        assertEquals(1, root.getInt("version"))
        assertEquals("https://sync.example.test/api", parsed.endpointUrl)
        assertEquals("writer-token", parsed.accessToken)
        assertEquals(ownerId, parsed.libraryOwnerUserId)
        assertEquals("WG Bibliothek", parsed.libraryOwnerName)
        assertEquals(contributorId, parsed.contributorUserId)
        assertEquals("Mika", parsed.contributorName)
        assertEquals("write", parsed.role)
    }

    @Test
    fun detectsOnlyTypedBackendInvites() {
        val inviteJson = BackendSyncInvitePackage.toJson(
            config = RemoteSyncConfig("https://sync.example.test/api", "token"),
            libraryOwnerUserId = cardUserIdForName("Lena"),
            libraryOwnerName = "Lena",
            contributorUserId = "account_sam",
            contributorName = "Sam",
        )

        assertTrue(BackendSyncInvitePackage.isBackendSyncInvite(inviteJson))
        assertFalse(BackendSyncInvitePackage.isBackendSyncInvite("""{"type":"seemops.card_sync"}"""))
        assertFalse(BackendSyncInvitePackage.isBackendSyncInvite("not json"))
    }

    @Test
    fun inviteUsesConfigRoleWhenNoExplicitRoleIsProvided() {
        val invite = BackendSyncInvitePackage.fromJson(
            BackendSyncInvitePackage.toJson(
                config = RemoteSyncConfig(
                    endpointUrl = "https://sync.example.test/api",
                    accessToken = "reader-token",
                    role = "read",
                ),
                libraryOwnerUserId = cardUserIdForName("Lena"),
                libraryOwnerName = "Lena",
                contributorUserId = "account_guest",
                contributorName = "Gast",
            )
        )

        assertEquals("read", invite.role)
    }

    @Test
    fun rejectsInvitesWithoutToken() {
        val result = runCatching {
            BackendSyncInvitePackage.fromJson(
                """
                {
                  "type": "seemops.backend_sync_invite",
                  "endpointUrl": "https://sync.example.test/api",
                  "accessToken": "",
                  "libraryOwnerUserId": "account_library",
                  "libraryOwnerName": "WG Bibliothek"
                }
                """.trimIndent()
            )
        }

        assertTrue(result.isFailure)
    }
}
