package com.snupai.trinkspiel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionLevelTest {
    @Test
    fun questionLevelsKeepRequestedEscalationLabels() {
        assertEquals("1 Einfach", QuestionLevel.LEVEL_1.label)
        assertEquals("2 Saufen", QuestionLevel.LEVEL_2.label)
        assertEquals("3 Fast ausziehen", QuestionLevel.LEVEL_3.label)

        assertTrue(QuestionLevel.LEVEL_1.description.contains("Lockere Fragen"))
        assertTrue(QuestionLevel.LEVEL_2.description.contains("Trinklastige Karten"))
        assertTrue(QuestionLevel.LEVEL_3.description.contains("Viel Alkohol"))
        assertTrue(QuestionLevel.LEVEL_3.description.contains("freizuegige Aufgaben"))
    }

    @Test
    fun drinksStillMapToEscalationLevels() {
        assertEquals(QuestionLevel.LEVEL_1, QuestionLevel.fromDrinks(1))
        assertEquals(QuestionLevel.LEVEL_2, QuestionLevel.fromDrinks(2))
        assertEquals(QuestionLevel.LEVEL_2, QuestionLevel.fromDrinks(3))
        assertEquals(QuestionLevel.LEVEL_3, QuestionLevel.fromDrinks(4))
    }
}
