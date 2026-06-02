package com.snupai.trinkspiel.util

import com.snupai.trinkspiel.data.DrinkEntry
import com.snupai.trinkspiel.model.CardCategory
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.model.cardUserIdForName
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardSyncPackageTest {

    @Test
    fun roundTripKeepsRemoteSnapshotMetadata() {
        val ownerId = cardUserIdForName("WG Bibliothek")
        val contributorId = cardUserIdForName("Mika")
        val entries = listOf(
            DrinkEntry(
                id = 12,
                text = "Remote Karte",
                drinks = 4,
                category = CardCategory.SPICY.id,
                packName = "WG Pack",
                isEnabled = false,
                isPendingReview = true,
                questionLevel = QuestionLevel.LEVEL_3.id,
                ownerUserId = ownerId,
                ownerName = "WG Bibliothek",
                contributorUserId = contributorId,
                contributorName = "Mika",
                remoteId = "remote-card-12",
                updatedAtMillis = 123L,
            )
        )

        val json = CardSyncPackage.toJson(entries, libraryOwnerUserId = ownerId)
        val root = JSONObject(json)
        val parsed = CardSyncPackage.fromJson(json)

        assertEquals("seemops.card_sync", root.getString("type"))
        assertEquals(1, root.getInt("version"))
        assertEquals(1, parsed.size)
        assertEquals("remote-card-12", parsed.first().remoteId)
        assertEquals("Remote Karte", parsed.first().text)
        assertEquals(4, parsed.first().drinks)
        assertEquals(CardCategory.SPICY.id, parsed.first().category)
        assertEquals("WG Pack", parsed.first().packName)
        assertEquals(false, parsed.first().isEnabled)
        assertTrue(parsed.first().isPendingReview)
        assertEquals(QuestionLevel.LEVEL_3.id, parsed.first().questionLevel)
        assertEquals(ownerId, parsed.first().ownerUserId)
        assertEquals("WG Bibliothek", parsed.first().ownerName)
        assertEquals(contributorId, parsed.first().contributorUserId)
        assertEquals("Mika", parsed.first().contributorName)
        assertEquals(123L, parsed.first().updatedAtMillis)
    }

    @Test
    fun localCardsWithoutRemoteIdReceiveStablePackageRemoteId() {
        val ownerId = cardUserIdForName("Lena")
        val json = CardSyncPackage.toJson(
            entries = listOf(
                DrinkEntry(
                    id = 44,
                    text = "Lokale Karte",
                    drinks = 1,
                    ownerUserId = ownerId,
                    ownerName = "Lena",
                )
            ),
            libraryOwnerUserId = ownerId,
        )

        val parsed = CardSyncPackage.fromJson(json)

        assertEquals("package:$ownerId:44", parsed.first().remoteId)
    }

    @Test
    fun exportFiltersToRequestedOwnerLibrary() {
        val ownerId = cardUserIdForName("Lena")
        val otherOwnerId = cardUserIdForName("Nora")
        val json = CardSyncPackage.toJson(
            entries = listOf(
                DrinkEntry(
                    id = 1,
                    text = "Meine Karte",
                    drinks = 1,
                    ownerUserId = ownerId,
                    ownerName = "Lena",
                ),
                DrinkEntry(
                    id = 2,
                    text = "Andere Karte",
                    drinks = 1,
                    ownerUserId = otherOwnerId,
                    ownerName = "Nora",
                ),
            ),
            libraryOwnerUserId = ownerId,
        )

        val parsed = CardSyncPackage.fromJson(json)

        assertEquals(1, parsed.size)
        assertEquals("Meine Karte", parsed.first().text)
        assertEquals(ownerId, parsed.first().ownerUserId)
    }

    @Test
    fun exportOwnerFilterNormalizesLegacyLocalOwnerIds() {
        val ownerId = cardUserIdForName("WG Bibliothek")
        val json = CardSyncPackage.toJson(
            entries = listOf(
                DrinkEntry(
                    id = 3,
                    text = "Legacy Owner Karte",
                    drinks = 1,
                    ownerUserId = "local",
                    ownerName = "WG Bibliothek",
                ),
            ),
            libraryOwnerUserId = ownerId,
        )

        val parsed = CardSyncPackage.fromJson(json)

        assertEquals(1, parsed.size)
        assertEquals("Legacy Owner Karte", parsed.first().text)
        assertEquals(ownerId, parsed.first().ownerUserId)
    }

    @Test
    fun detectsOnlyTypedCardSyncPackages() {
        val ownerId = cardUserIdForName("Lena")
        val syncJson = CardSyncPackage.toJson(
            entries = listOf(
                DrinkEntry(
                    id = 1,
                    text = "Sync Karte",
                    drinks = 1,
                    ownerUserId = ownerId,
                    ownerName = "Lena",
                )
            ),
            libraryOwnerUserId = ownerId,
        )

        assertTrue(CardSyncPackage.isCardSyncPackage(syncJson))
        assertFalse(CardSyncPackage.isCardSyncPackage("""{"cards": [], "settings": null}"""))
        assertFalse(CardSyncPackage.isCardSyncPackage("""not json"""))
    }
}
