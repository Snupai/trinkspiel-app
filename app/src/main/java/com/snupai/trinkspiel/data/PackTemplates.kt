package com.snupai.trinkspiel.data

import com.snupai.trinkspiel.model.CardCategory
import com.snupai.trinkspiel.model.DrinkIntensity
import com.snupai.trinkspiel.model.GameMode

data class PackTemplate(
    val id: String,
    val name: String,
    val description: String,
    val recommendedMode: GameMode,
    val recommendedIntensity: DrinkIntensity,
    val entries: List<DrinkEntry>,
)

object PackTemplates {
    val all = listOf(
        template(
            id = "wg_warmup",
            name = "WG Warmup Vorlage",
            description = "Sanfter Mix für Hausrunden mit Wahrheiten, Mini-Spielen und kleinen Regeln.",
            recommendedMode = GameMode.CHILL,
            recommendedIntensity = DrinkIntensity.LOW,
            cards = listOf(
                draft("Vorlage: Harmlose Wahrheit über den heutigen Abend.", 1, CardCategory.TRUTH),
                draft("Vorlage: Alle, auf die eine Eigenschaft passt, trinken 1.", 1, CardCategory.EVERYONE),
                draft("Vorlage: Kurzes Mini-Spiel mit 10 Sekunden Zeitlimit.", 1, CardCategory.MINI_GAME),
                draft("Vorlage: Kleine Regel bis zur nächsten Karte.", 1, CardCategory.RULE),
                draft("Vorlage: Mini-Aufgabe, die am Platz funktioniert.", 1, CardCategory.CHALLENGE),
                draft("Vorlage: Zeigt auf die passendste Person, Mehrheit entscheidet.", 1, CardCategory.EVERYONE),
                draft("Vorlage: Duell für zwei Spieler, Verlierer trinkt 1.", 1, CardCategory.DUEL),
                draft("Vorlage: Runde Wasser- oder Pausencheck einbauen.", 1, CardCategory.EVERYONE),
            ),
        ),
        template(
            id = "preparty_balance",
            name = "Preparty Balance Vorlage",
            description = "Mehr Energie, aber weiterhin kurze Karten für Musik, Outfits und Start-in-den-Abend.",
            recommendedMode = GameMode.CLASSIC,
            recommendedIntensity = DrinkIntensity.MEDIUM,
            cards = listOf(
                draft("Vorlage: Musikfrage mit schneller Abstimmung.", 1, CardCategory.TRUTH),
                draft("Vorlage: Alle mit passendem Outfit-Detail trinken 1.", 1, CardCategory.EVERYONE),
                draft("Vorlage: Kategorie mit Party- oder Ausgehbegriffen.", 1, CardCategory.MINI_GAME),
                draft("Vorlage: Verteile 2 Schlucke mit kurzer Begründung.", 2, CardCategory.CHALLENGE),
                draft("Vorlage: Kleine Regel, die nur eine Runde gilt.", 1, CardCategory.RULE),
                draft("Vorlage: Duell mit schneller Gruppenentscheidung.", 1, CardCategory.DUEL),
                draft("Vorlage: Wahrheit über einen Song, Look oder Plan.", 1, CardCategory.TRUTH),
                draft("Vorlage: Gruppenkarte, bei der niemand bloßgestellt wird.", 1, CardCategory.EVERYONE),
                draft("Vorlage: Bewegungsaufgabe ohne Rennen oder Risiko.", 1, CardCategory.CHALLENGE),
                draft("Vorlage: Pausenkarte für Wasser, Essen oder frische Luft.", 1, CardCategory.EVERYONE),
            ),
        ),
        template(
            id = "couples_soft",
            name = "Couples Soft Vorlage",
            description = "Lockere Duo-Karten mit Komplimenten, Dating-Fragen und mildem Spicy-Anteil.",
            recommendedMode = GameMode.COUPLES,
            recommendedIntensity = DrinkIntensity.LOW,
            cards = listOf(
                draft("Vorlage: Kompliment oder harmlose Frage an eine Person.", 1, CardCategory.TRUTH),
                draft("Vorlage: Duo beantwortet dieselbe Frage nacheinander.", 1, CardCategory.DUEL),
                draft("Vorlage: Spicy, aber freiwillig und ohne Details.", 1, CardCategory.SPICY),
                draft("Vorlage: Alle stimmen über eine sympathische Eigenschaft ab.", 1, CardCategory.EVERYONE),
                draft("Vorlage: Team-Aufgabe für zwei Spieler am Platz.", 1, CardCategory.CHALLENGE),
                draft("Vorlage: Wahrheit über Dating, Freundschaft oder Alltag.", 1, CardCategory.TRUTH),
                draft("Vorlage: Mini-Spiel mit einem Duo und kurzer Antwortzeit.", 1, CardCategory.MINI_GAME),
                draft("Vorlage: Regel, die Paare und Singles gleich behandelt.", 1, CardCategory.RULE),
            ),
        ),
    )

    fun find(id: String): PackTemplate? = all.firstOrNull { it.id == id }

    private fun template(
        id: String,
        name: String,
        description: String,
        recommendedMode: GameMode,
        recommendedIntensity: DrinkIntensity,
        cards: List<TemplateSeed>,
    ): PackTemplate = PackTemplate(
        id = id,
        name = name,
        description = description,
        recommendedMode = recommendedMode,
        recommendedIntensity = recommendedIntensity,
        entries = cards.map { card ->
            DrinkEntry(
                text = card.text,
                drinks = card.drinks,
                category = card.category.id,
                packName = name,
                isEnabled = false,
            )
        },
    )

    private fun draft(
        text: String,
        drinks: Int,
        category: CardCategory,
    ): TemplateSeed = TemplateSeed(text, drinks, category)
}

private data class TemplateSeed(
    val text: String,
    val drinks: Int,
    val category: CardCategory,
)
