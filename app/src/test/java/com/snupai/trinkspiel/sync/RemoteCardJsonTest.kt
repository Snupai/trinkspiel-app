package com.snupai.trinkspiel.sync

import com.snupai.trinkspiel.model.CardCategory
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.model.cardUserIdForName
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteCardJsonTest {
    @Test
    fun parsesBackendObjectWithCardsArray() {
        val json = """
            {
              "cards": [
                {
                  "remoteId": "remote-77",
                  "text": "Backend Karte",
                  "drinks": 4,
                  "category": "${CardCategory.SPICY.id}",
                  "packName": "Backend Pack",
                  "enabled": false,
                  "pendingReview": true,
                  "questionLevel": ${QuestionLevel.LEVEL_3.id},
                  "ownerName": "WG Bibliothek",
                  "contributorName": "Mika",
                  "updatedAtMillis": 12345
                }
              ]
            }
        """.trimIndent()

        val cards = RemoteCardJson.fromJson(json)

        assertEquals(1, cards.size)
        val card = cards.first()
        assertEquals("remote-77", card.remoteId)
        assertEquals("Backend Karte", card.text)
        assertEquals(4, card.drinks)
        assertEquals(CardCategory.SPICY.id, card.category)
        assertEquals("Backend Pack", card.packName)
        assertEquals(false, card.isEnabled)
        assertEquals(true, card.isPendingReview)
        assertEquals(QuestionLevel.LEVEL_3.id, card.questionLevel)
        assertEquals(cardUserIdForName("WG Bibliothek"), card.ownerUserId)
        assertEquals(cardUserIdForName("Mika"), card.contributorUserId)
        assertEquals(12345L, card.updatedAtMillis)
    }

    @Test
    fun serializesRemoteSnapshotWithCanonicalFields() {
        val ownerId = cardUserIdForName("WG Bibliothek")
        val contributorId = cardUserIdForName("Mika")
        val json = RemoteCardJson.toJsonObject(
            RemoteCardSnapshot(
                remoteId = "remote-88",
                text = "Canonical",
                drinks = 2,
                category = CardCategory.CHALLENGE.id,
                packName = "Shared",
                questionLevel = QuestionLevel.LEVEL_2.id,
                ownerUserId = ownerId,
                ownerName = "WG Bibliothek",
                contributorUserId = contributorId,
                contributorName = "Mika",
                updatedAtMillis = 67890,
            )
        )

        assertEquals("remote-88", json.getString("remoteId"))
        assertEquals("Canonical", json.getString("text"))
        assertEquals(QuestionLevel.LEVEL_2.id, json.getInt("questionLevel"))
        assertEquals(ownerId, json.getString("ownerUserId"))
        assertEquals(contributorId, json.getString("contributorUserId"))
        assertEquals(67890L, json.getLong("updatedAtMillis"))
    }
}
