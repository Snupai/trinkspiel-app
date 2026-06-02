package com.snupai.trinkspiel.util

import com.snupai.trinkspiel.data.DrinkEntry
import com.snupai.trinkspiel.model.CardCategory
import com.snupai.trinkspiel.model.CardSyncStatus
import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_ID
import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_NAME
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.model.cardUserIdForName
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportExportTest {

    @Test
    fun roundTripKeepsPackMetadata() {
        val source = listOf(
            DrinkEntry(
                text = "Testkarte",
                drinks = 2,
                category = CardCategory.DUEL.id,
                packName = "Test Pack",
                isEnabled = false,
                isPendingReview = true,
                questionLevel = QuestionLevel.LEVEL_2.id,
                ownerName = "Lena",
                contributorName = "Mika",
                remoteId = "remote-card-1",
                updatedAtMillis = 42L,
                syncStatus = CardSyncStatus.SYNCED.id,
            )
        )

        val parsed = ImportExport.fromJson(ImportExport.toJson(source, packName = "Export"))
        val exported = JSONObject(ImportExport.toJson(source, packName = "Export"))

        assertEquals(7, exported.getInt("version"))
        assertFalse(exported.getJSONArray("cards").getJSONObject(0).getBoolean("enabled"))
        assertTrue(exported.getJSONArray("cards").getJSONObject(0).getBoolean("pendingReview"))
        assertEquals(QuestionLevel.LEVEL_2.id, exported.getJSONArray("cards").getJSONObject(0).getInt("questionLevel"))
        assertEquals(cardUserIdForName("Lena"), exported.getJSONArray("cards").getJSONObject(0).getString("ownerUserId"))
        assertEquals("Lena", exported.getJSONArray("cards").getJSONObject(0).getString("ownerName"))
        assertEquals(cardUserIdForName("Mika"), exported.getJSONArray("cards").getJSONObject(0).getString("contributorUserId"))
        assertEquals("Mika", exported.getJSONArray("cards").getJSONObject(0).getString("contributorName"))
        assertEquals("remote-card-1", exported.getJSONArray("cards").getJSONObject(0).getString("remoteId"))
        assertEquals(42L, exported.getJSONArray("cards").getJSONObject(0).getLong("updatedAtMillis"))
        assertEquals(CardSyncStatus.SYNCED.id, exported.getJSONArray("cards").getJSONObject(0).getString("syncStatus"))
        assertEquals(1, parsed.size)
        assertEquals("Testkarte", parsed.first().text)
        assertEquals(2, parsed.first().drinks)
        assertEquals(CardCategory.DUEL.id, parsed.first().category)
        assertEquals("Test Pack", parsed.first().packName)
        assertFalse(parsed.first().isEnabled)
        assertTrue(parsed.first().isPendingReview)
        assertEquals(QuestionLevel.LEVEL_2.id, parsed.first().questionLevel)
        assertEquals(cardUserIdForName("Lena"), parsed.first().ownerUserId)
        assertEquals("Lena", parsed.first().ownerName)
        assertEquals(cardUserIdForName("Mika"), parsed.first().contributorUserId)
        assertEquals("Mika", parsed.first().contributorName)
        assertEquals("remote-card-1", parsed.first().remoteId)
        assertEquals(42L, parsed.first().updatedAtMillis)
        assertEquals(CardSyncStatus.SYNCED.id, parsed.first().syncStatus)
    }

    @Test
    fun legacyArrayImportUsesSafeDefaults() {
        val parsed = ImportExport.fromJson(
            """
            [
              {"text": "Alte Karte", "drinks": 0},
              {"text": "  "}
            ]
            """.trimIndent()
        )

        assertEquals(1, parsed.size)
        assertEquals("Alte Karte", parsed.first().text)
        assertEquals(1, parsed.first().drinks)
        assertEquals(CardCategory.CHALLENGE.id, parsed.first().category)
        assertEquals("Import", parsed.first().packName)
        assertTrue(parsed.first().isEnabled)
        assertFalse(parsed.first().isPendingReview)
        assertEquals(QuestionLevel.LEVEL_1.id, parsed.first().questionLevel)
        assertEquals(DEFAULT_CARD_USER_ID, parsed.first().ownerUserId)
        assertEquals(DEFAULT_CARD_USER_NAME, parsed.first().ownerName)
        assertEquals(DEFAULT_CARD_USER_ID, parsed.first().contributorUserId)
        assertEquals(DEFAULT_CARD_USER_NAME, parsed.first().contributorName)
        assertEquals("", parsed.first().remoteId)
        assertEquals(0L, parsed.first().updatedAtMillis)
        assertEquals(CardSyncStatus.LOCAL.id, parsed.first().syncStatus)
    }

    @Test
    fun importAcceptsLegacyIsEnabledFlag() {
        val parsed = ImportExport.fromJson(
            """
            {
              "version": 2,
              "packName": "Legacy Pack",
              "cards": [
                {"text": "Pausierte Karte", "drinks": 4, "isEnabled": false, "level": 3, "owner": "Nora", "addedBy": "Sam"}
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, parsed.size)
        assertEquals("Legacy Pack", parsed.first().packName)
        assertFalse(parsed.first().isEnabled)
        assertEquals(QuestionLevel.LEVEL_3.id, parsed.first().questionLevel)
        assertEquals(cardUserIdForName("Nora"), parsed.first().ownerUserId)
        assertEquals("Nora", parsed.first().ownerName)
        assertEquals(cardUserIdForName("Sam"), parsed.first().contributorUserId)
        assertEquals("Sam", parsed.first().contributorName)
    }

    @Test
    fun importKeepsExplicitExternalUserIds() {
        val parsed = ImportExport.fromJson(
            """
            {
              "packName": "Account Pack",
              "cards": [
                {
                  "text": "Account Karte",
                  "drinks": 1,
                  "ownerUserId": "account:owner-1",
                  "ownerName": "Lena",
                  "contributorUserId": "user_123",
                  "contributorName": "Mika"
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, parsed.size)
        assertEquals("account:owner-1", parsed.first().ownerUserId)
        assertEquals("Lena", parsed.first().ownerName)
        assertEquals("user_123", parsed.first().contributorUserId)
        assertEquals("Mika", parsed.first().contributorName)
    }

    @Test
    fun packNameForUsesSinglePackOrFallback() {
        val singlePack = listOf(
            DrinkEntry(text = "A", drinks = 1, packName = "Chaos Pack"),
            DrinkEntry(text = "B", drinks = 1, packName = "Chaos Pack"),
        )
        val mixedPack = singlePack + DrinkEntry(text = "C", drinks = 1, packName = "Couples Pack")

        assertEquals("Chaos Pack", ImportExport.packNameFor(singlePack))
        assertEquals("Seemops Export", ImportExport.packNameFor(mixedPack))
    }

    @Test
    fun fileNameForCreatesSafeJsonName() {
        val entries = listOf(
            DrinkEntry(text = "A", drinks = 1, packName = "Chaos Pack! 2026"),
        )

        assertEquals("chaos_pack_2026.json", ImportExport.fileNameFor(entries))
        assertEquals("trinkspiel_export.json", ImportExport.fileNameFor(emptyList()))
    }
}
