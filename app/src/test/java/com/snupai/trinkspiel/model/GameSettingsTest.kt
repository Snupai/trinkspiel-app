package com.snupai.trinkspiel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSettingsTest {

    @Test
    fun firstRunSetupIsRequiredUntilAgeSafetyAndSetupAreComplete() {
        assertTrue(GameSettings().requiresFirstRunSetup)
        assertTrue(
            GameSettings(
                ageGateAccepted = true,
                safetyNoticeAccepted = true,
                firstRunSetupCompleted = false,
            ).requiresFirstRunSetup
        )
        assertFalse(
            GameSettings(
                ageGateAccepted = true,
                safetyNoticeAccepted = true,
                firstRunSetupCompleted = true,
            ).requiresFirstRunSetup
        )
    }

    @Test
    fun scoreboardFollowsPlayerOrderAndDefaultsMissingStats() {
        val settings = GameSettings(
            players = listOf("Lena", "Mika"),
            playerStats = mapOf(
                "Mika" to PlayerStats(
                    name = "Mika",
                    team = TeamOption.TEAM_A,
                    points = 2,
                    drinks = 5,
                )
            )
        )

        val scoreboard = settings.scoreboard

        assertEquals("Lena", scoreboard[0].name)
        assertEquals(0, scoreboard[0].points)
        assertEquals("Mika", scoreboard[1].name)
        assertEquals(2, scoreboard[1].points)
        assertEquals(5, scoreboard[1].drinks)
    }

    @Test
    fun teamScoresIgnoreSoloPlayers() {
        val settings = GameSettings(
            players = listOf("A", "B", "C"),
            playerStats = mapOf(
                "A" to PlayerStats("A", TeamOption.TEAM_A, points = 1, drinks = 2),
                "B" to PlayerStats("B", TeamOption.TEAM_A, points = 3, drinks = 4),
                "C" to PlayerStats("C", TeamOption.SOLO, points = 9, drinks = 9),
            )
        )

        val team = settings.teamScores.single()

        assertEquals(TeamOption.TEAM_A, team.team)
        assertEquals(4, team.points)
        assertEquals(6, team.drinks)
    }

    @Test
    fun renamePlayerPreservesStatsAndCurrentIndex() {
        val settings = GameSettings(
            players = listOf("Lena", "Mika"),
            currentPlayerIndex = 1,
            playerStats = mapOf(
                "Mika" to PlayerStats("Mika", TeamOption.TEAM_B, points = 4, drinks = 7),
            )
        )

        val renamed = settings.renamePlayer(index = 1, newName = "Mikael")

        assertEquals(listOf("Lena", "Mikael"), renamed.players)
        assertEquals(1, renamed.currentPlayerIndex)
        assertEquals("Mikael", renamed.scoreboard[1].name)
        assertEquals(TeamOption.TEAM_B, renamed.scoreboard[1].team)
        assertEquals(4, renamed.scoreboard[1].points)
        assertEquals(7, renamed.scoreboard[1].drinks)
    }

    @Test
    fun renamePlayerRejectsDuplicateNames() {
        val settings = GameSettings(players = listOf("Lena", "Mika"))

        val unchanged = settings.renamePlayer(index = 1, newName = "lena")

        assertEquals(settings, unchanged)
    }

    @Test
    fun undoCompletedTurnRestoresPlayerIndexAndSubtractsScore() {
        val settings = GameSettings(
            players = listOf("Lena", "Mika"),
            currentPlayerIndex = 1,
            playerStats = mapOf(
                "Lena" to PlayerStats("Lena", points = 2, drinks = 5),
                "Mika" to PlayerStats("Mika", points = 1, drinks = 1),
            )
        )

        val restored = settings.undoCompletedTurn(
            CompletedTurn(
                cardId = 42,
                cardDrinks = 3,
                playerName = "Lena",
                previousPlayerIndex = 0,
            )
        )

        assertEquals(0, restored.currentPlayerIndex)
        assertEquals(1, restored.scoreboard[0].points)
        assertEquals(2, restored.scoreboard[0].drinks)
        assertEquals(1, restored.scoreboard[1].points)
    }

    @Test
    fun undoCompletedTurnDoesNotMakeScoresNegative() {
        val settings = GameSettings(
            players = listOf("Lena"),
            playerStats = mapOf("Lena" to PlayerStats("Lena", points = 0, drinks = 1))
        )

        val restored = settings.undoCompletedTurn(
            CompletedTurn(
                cardId = 42,
                cardDrinks = 4,
                playerName = "Lena",
                previousPlayerIndex = 0,
            )
        )

        assertEquals(0, restored.scoreboard[0].points)
        assertEquals(0, restored.scoreboard[0].drinks)
    }

    @Test
    fun excludedPacksAreNotEnabledForGameplay() {
        val settings = GameSettings(excludedPackNames = setOf("Chaos Pack"))

        assertFalse(settings.isPackEnabled("Chaos Pack"))
        assertTrue(settings.isPackEnabled("Classic Starter"))
    }

    @Test
    fun excludedPacksAreScopedByCardOwner() {
        val wgOwnerId = cardUserIdForName("WG Bibliothek")
        val lenaOwnerId = cardUserIdForName("Lena Bibliothek")
        val settings = GameSettings(
            cardOwnerUserId = wgOwnerId,
            cardOwnerName = "WG Bibliothek",
            excludedPackNamesByOwner = mapOf(
                wgOwnerId to setOf("Classic Starter"),
                lenaOwnerId to setOf("Lena Pack"),
            ),
        ).withNormalizedPackExclusions()

        assertFalse(settings.isPackEnabled("Classic Starter"))
        assertTrue(settings.isPackEnabled("Lena Pack"))

        val lenaSettings = settings.withCardOwnerProfile(lenaOwnerId, "Lena Bibliothek")

        assertTrue(lenaSettings.isPackEnabled("Classic Starter"))
        assertFalse(lenaSettings.isPackEnabled("Lena Pack"))
        assertEquals(setOf("Classic Starter"), lenaSettings.excludedPackNamesByOwner[wgOwnerId])
    }

    @Test
    fun customModeUsesSelectedCategories() {
        val settings = GameSettings(
            mode = GameMode.CUSTOM,
            customCategoryIds = setOf(CardCategory.TRUTH.id, CardCategory.DUEL.id),
        )

        assertTrue(settings.isCategoryEnabled(CardCategory.TRUTH.id))
        assertTrue(settings.isCategoryEnabled(CardCategory.DUEL.id))
        assertFalse(settings.isCategoryEnabled(CardCategory.RULE.id))
    }

    @Test
    fun questionLevelSelectionControlsPlayableLevels() {
        val settings = GameSettings(
            enabledQuestionLevelIds = setOf(QuestionLevel.LEVEL_1.id, QuestionLevel.LEVEL_3.id),
        )

        assertTrue(settings.isQuestionLevelEnabled(QuestionLevel.LEVEL_1.id))
        assertFalse(settings.isQuestionLevelEnabled(QuestionLevel.LEVEL_2.id))
        assertTrue(settings.isQuestionLevelEnabled(QuestionLevel.LEVEL_3.id))
    }

    @Test
    fun activeLibraryControlsWhichOwnerCardsArePlayable() {
        val settings = GameSettings(
            cardOwnerUserId = "library_wg",
            cardOwnerName = "WG Bibliothek",
        )

        assertTrue(settings.isCardInActiveLibrary("library_wg", "WG Bibliothek"))
        assertFalse(settings.isCardInActiveLibrary("account_lena", "Lena"))
    }

    @Test
    fun builtInModesIgnoreCustomCategories() {
        val settings = GameSettings(
            mode = GameMode.CHILL,
            customCategoryIds = emptySet(),
        )

        assertTrue(settings.isCategoryEnabled(CardCategory.TRUTH.id))
        assertFalse(settings.isCategoryEnabled(CardCategory.RULE.id))
    }

    @Test
    fun skippedCardStaysDrawnButRemovesDrinkCount() {
        val round = SavedRoundState(
            currentCardId = 7L,
            isCardVisible = true,
            drawnCardIds = setOf(7L),
            drawsThisRound = 1,
            drinksThisRound = 3,
        )

        val skipped = round.skipVisibleCard(cardDrinks = 3)

        assertEquals(null, skipped.currentCardId)
        assertFalse(skipped.isCardVisible)
        assertEquals(setOf(7L), skipped.drawnCardIds)
        assertEquals(1, skipped.drawsThisRound)
        assertEquals(0, skipped.drinksThisRound)
    }

    @Test
    fun skippedCardCannotMakeDrinkCountNegative() {
        val skipped = SavedRoundState(drinksThisRound = 1).skipVisibleCard(cardDrinks = 3)

        assertEquals(0, skipped.drinksThisRound)
    }

    @Test
    fun deletedCardsAreRemovedFromRoundState() {
        val remainingRecord = DrawnCardRecord(
            cardId = 11L,
            text = "Bleibt",
            drinks = 1,
            category = CardCategory.TRUTH.id,
            packName = "Classic Starter",
            playerName = null,
            roundNumber = 1,
        )
        val round = SavedRoundState(
            currentCardId = 9L,
            isCardVisible = true,
            drawnCardIds = setOf(7L, 9L, 11L),
            drawHistory = listOf(
                DrawnCardRecord(
                    cardId = 7L,
                    text = "Gelöscht",
                    drinks = 2,
                    category = CardCategory.CHALLENGE.id,
                    packName = "Classic Starter",
                    playerName = "Lena",
                    roundNumber = 1,
                ),
                remainingRecord,
            ),
            lastCompletedTurn = CompletedTurn(
                cardId = 7L,
                cardDrinks = 2,
                playerName = "Lena",
                previousPlayerIndex = 0,
            ),
        )

        val cleaned = round.removeDeletedCards(setOf(7L, 9L))

        assertEquals(null, cleaned.currentCardId)
        assertFalse(cleaned.isCardVisible)
        assertEquals(setOf(11L), cleaned.drawnCardIds)
        assertEquals(listOf(remainingRecord), cleaned.drawHistory)
        assertEquals(null, cleaned.lastCompletedTurn)
    }
}
