package com.snupai.trinkspiel.ui.screen

import com.snupai.trinkspiel.data.DrinkEntry
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.model.cardUserIdForName
import com.snupai.trinkspiel.viewmodel.CardUserSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class EntryListScreenTest {
    @Test
    fun cardUsersForEntriesCountsOnlyProvidedLibraryEntries() {
        val wgOwnerId = cardUserIdForName("WG Bibliothek")
        val mikaId = cardUserIdForName("Mika")
        val noraId = cardUserIdForName("Nora")
        val summaries = cardUsersForEntries(
            entries = listOf(
                DrinkEntry(
                    id = 1,
                    text = "WG Karte",
                    drinks = 1,
                    ownerUserId = wgOwnerId,
                    ownerName = "WG Bibliothek",
                    contributorUserId = mikaId,
                    contributorName = "Mika",
                ),
                DrinkEntry(
                    id = 2,
                    text = "WG zweite Karte",
                    drinks = 1,
                    ownerUserId = wgOwnerId,
                    ownerName = "WG Bibliothek",
                    contributorUserId = noraId,
                    contributorName = "Nora",
                ),
            ),
            savedUsers = listOf(
                CardUserSummary(
                    userId = wgOwnerId,
                    displayName = "WG Bibliothek",
                    ownedCount = 99,
                    contributedCount = 99,
                ),
                CardUserSummary(
                    userId = mikaId,
                    displayName = "Mika",
                    ownedCount = 0,
                    contributedCount = 99,
                ),
            ),
        )

        assertEquals(
            listOf(
                CardUserSummary(wgOwnerId, "WG Bibliothek", ownedCount = 2, contributedCount = 0),
                CardUserSummary(mikaId, "Mika", ownedCount = 0, contributedCount = 1),
                CardUserSummary(noraId, "Nora", ownedCount = 0, contributedCount = 1),
            ),
            summaries,
        )
    }

    @Test
    fun reviewContributorSummariesCountPendingCardsByContributorAndLevel() {
        val wgOwnerId = cardUserIdForName("WG Bibliothek")
        val mikaId = cardUserIdForName("Mika")
        val noraId = cardUserIdForName("Nora")

        val summaries = reviewContributorSummariesForEntries(
            listOf(
                DrinkEntry(
                    id = 1,
                    text = "Mika einfach",
                    drinks = 1,
                    questionLevel = QuestionLevel.LEVEL_1.id,
                    ownerUserId = wgOwnerId,
                    ownerName = "WG Bibliothek",
                    contributorUserId = mikaId,
                    contributorName = "Mika",
                    isPendingReview = true,
                ),
                DrinkEntry(
                    id = 2,
                    text = "Mika hardcore",
                    drinks = 5,
                    questionLevel = QuestionLevel.LEVEL_3.id,
                    ownerUserId = wgOwnerId,
                    ownerName = "WG Bibliothek",
                    contributorUserId = mikaId,
                    contributorName = "Mika",
                    isPendingReview = true,
                ),
                DrinkEntry(
                    id = 3,
                    text = "Nora saufen",
                    drinks = 2,
                    questionLevel = QuestionLevel.LEVEL_2.id,
                    ownerUserId = wgOwnerId,
                    ownerName = "WG Bibliothek",
                    contributorUserId = noraId,
                    contributorName = "Nora",
                    isPendingReview = true,
                ),
                DrinkEntry(
                    id = 4,
                    text = "Schon frei",
                    drinks = 1,
                    questionLevel = QuestionLevel.LEVEL_1.id,
                    ownerUserId = wgOwnerId,
                    ownerName = "WG Bibliothek",
                    contributorUserId = noraId,
                    contributorName = "Nora",
                    isPendingReview = false,
                ),
            )
        )

        assertEquals(
            listOf(
                ReviewContributorSummary(
                    userId = mikaId,
                    displayName = "Mika",
                    cardCount = 2,
                    levelCounts = mapOf(
                        QuestionLevel.LEVEL_1.id to 1,
                        QuestionLevel.LEVEL_3.id to 1,
                    ),
                ),
                ReviewContributorSummary(
                    userId = noraId,
                    displayName = "Nora",
                    cardCount = 1,
                    levelCounts = mapOf(QuestionLevel.LEVEL_2.id to 1),
                ),
            ),
            summaries,
        )
    }
}
