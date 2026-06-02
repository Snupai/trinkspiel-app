package com.snupai.trinkspiel.data

import com.snupai.trinkspiel.model.CardCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInPacksTest {

    @Test
    fun builtInLibraryHasEnoughLaunchContent() {
        assertTrue(BuiltInPacks.all.size >= 8)
        assertTrue(BuiltInPacks.all.sumOf { it.entries.size } >= 128)
        BuiltInPacks.all.forEach { pack ->
            assertTrue("${pack.name} should have at least 16 cards", pack.entries.size >= 16)
        }
    }

    @Test
    fun packIdsNamesAndCardTextsAreUnique() {
        val packIds = BuiltInPacks.all.map { it.id.lowercase() }
        val packNames = BuiltInPacks.all.map { it.name.lowercase() }
        val cardTexts = BuiltInPacks.all
            .flatMap { it.entries }
            .map { it.text.trim().lowercase() }

        assertEquals(packIds.distinct().size, packIds.size)
        assertEquals(packNames.distinct().size, packNames.size)
        assertEquals(cardTexts.distinct().size, cardTexts.size)
    }

    @Test
    fun builtInCardsHaveValidMetadata() {
        val validCategories = CardCategory.entries.map { it.id }.toSet()

        BuiltInPacks.all.forEach { pack ->
            pack.entries.forEach { entry ->
                assertEquals(pack.name, entry.packName)
                assertTrue(entry.text.isNotBlank())
                assertTrue(entry.drinks >= 1)
                assertTrue(entry.category in validCategories)
                assertTrue(entry.isEnabled)
            }
        }
    }

    @Test
    fun builtInCardsStayInsideSafetyReviewBounds() {
        BuiltInPacks.all
            .flatMap { pack -> pack.entries.map { entry -> pack.name to entry } }
            .forEach { (packName, entry) ->
                assertTrue(
                    "$packName card should stay at or below $maxBuiltInDrinks drinks: ${entry.text}",
                    entry.drinks <= maxBuiltInDrinks
                )
                assertTrue(
                    "$packName card is too long for quick in-game reading: ${entry.text}",
                    entry.text.length <= maxBuiltInCardLength
                )

                val normalized = entry.text.lowercase()
                disallowedBuiltInContentSnippets.forEach { snippet ->
                    assertFalse(
                        "$packName card contains high-risk content '$snippet': ${entry.text}",
                        normalized.contains(snippet)
                    )
                }
            }
    }

    @Test
    fun builtInLibraryIncludesSafetyPacingPrompt() {
        val cardTexts = BuiltInPacks.all
            .flatMap { it.entries }
            .map { it.text.lowercase() }

        assertTrue(
            "Built-in content should include at least one water/pause prompt.",
            cardTexts.any { text -> "wasser" in text && ("pause" in text || "pausieren" in text) }
        )
    }

    private companion object {
        const val maxBuiltInDrinks = 4
        const val maxBuiltInCardLength = 140

        val disallowedBuiltInContentSnippets = listOf(
            "auf ex",
            "austrinken",
            "blackout",
            "droge",
            "drogen",
            "ecstasy",
            "exen",
            "fahr nach hause",
            "flasche",
            "ganzes glas",
            "ganze glas",
            "heroin",
            "koks",
            "kokain",
            "komasaufen",
            "leer trinken",
            "mdma",
            "meth",
            "nackt",
            "promille",
            "porno",
            "sex",
            "strip",
            "vodka",
        )
    }
}
