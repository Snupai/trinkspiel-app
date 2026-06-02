package com.snupai.trinkspiel.data

import com.snupai.trinkspiel.model.CardCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackTemplatesTest {

    @Test
    fun templatesProvideEditableBalancedDraftPacks() {
        assertTrue(PackTemplates.all.size >= 3)

        PackTemplates.all.forEach { template ->
            assertTrue(template.id.isNotBlank())
            assertTrue(template.name.endsWith("Vorlage"))
            assertTrue(template.description.isNotBlank())
            assertTrue("${template.name} should include enough draft cards", template.entries.size >= 8)

            val categories = template.entries.map { CardCategory.fromId(it.category).id }.toSet()
            assertTrue("${template.name} should mix at least 4 categories", categories.size >= 4)
            assertTrue("${template.name} should include low-impact cards", template.entries.any { it.drinks == 1 })

            template.entries.forEach { entry ->
                assertEquals(template.name, entry.packName)
                assertTrue(entry.text.startsWith("Vorlage:"))
                assertTrue(entry.text.length <= 120)
                assertTrue(entry.drinks in 1..2)
                assertFalse("Template drafts should start paused for editing", entry.isEnabled)
            }
        }
    }

    @Test
    fun templateIdsNamesAndDraftTextsAreUnique() {
        val ids = PackTemplates.all.map { it.id.lowercase() }
        val names = PackTemplates.all.map { it.name.lowercase() }
        val texts = PackTemplates.all
            .flatMap { it.entries }
            .map { it.text.trim().lowercase() }

        assertEquals(ids.distinct().size, ids.size)
        assertEquals(names.distinct().size, names.size)
        assertEquals(texts.distinct().size, texts.size)
    }
}
