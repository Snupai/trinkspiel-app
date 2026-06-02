package com.snupai.trinkspiel.data

import com.snupai.trinkspiel.model.CardCategory

data class BuiltInPack(
    val id: String,
    val name: String,
    val entries: List<DrinkEntry>
)

object BuiltInPacks {
    val all = listOf(
        pack(
            id = "classic",
            name = "Classic Starter",
            cards = listOf(
                seed("Wer zuletzt am Handy war, trinkt 2 Schlucke. Ja, erwischt.", 2, CardCategory.CHALLENGE),
                seed("Alle mit schwarzen Socken trinken 1 Schluck.", 1, CardCategory.EVERYONE),
                seed("Verteile 3 Schlucke. Keine Steuererklärung draus machen.", 3, CardCategory.CHALLENGE),
                seed("Der jüngste Spieler erzählt eine peinliche Story oder trinkt 2.", 2, CardCategory.TRUTH),
                seed("Alle, die heute Kaffee hatten, trinken 1.", 1, CardCategory.EVERYONE),
                seed("Mach eine neue Regel für die nächsten 5 Runden. Wer sie bricht, trinkt 1.", 1, CardCategory.RULE),
                seed("Wähle jemanden: Wahrheit oder 2 Schlucke.", 2, CardCategory.TRUTH),
                seed("Alle heben die linke Hand. Letzter trinkt 2.", 2, CardCategory.MINI_GAME),
                seed("Zeig auf die Person, die am ehesten einen geheimen Plan hat. Mehrheit trinkt 1.", 1, CardCategory.EVERYONE),
                seed("Nenne drei Dinge, die man nie in einer Küche sagen sollte. Fehler kostet 1.", 1, CardCategory.MINI_GAME),
                seed("Der aktuelle Spieler verteilt 2 Schlucke an eine Person mit Begründung.", 2, CardCategory.CHALLENGE),
                seed("Alle, die heute zu spät kamen, trinken 1. Auch mental zu spät zählt.", 1, CardCategory.EVERYONE),
                seed("Erfinde einen Spitznamen für die Person links von dir. Ablehnung kostet dich 1.", 1, CardCategory.CHALLENGE),
                seed("Regel: Niemand darf Vornamen sagen bis zu deinem nächsten Zug.", 1, CardCategory.RULE),
                seed("Wahrheit: Was war dein letzter völlig unnötiger Kauf?", 1, CardCategory.TRUTH),
                seed("Daumenkampf gegen die Person rechts von dir. Verlierer trinkt 2.", 2, CardCategory.DUEL),
            )
        ),
        pack(
            id = "chaos",
            name = "Chaos Pack",
            cards = listOf(
                seed("Kategorie: schlechte Ausreden. Wer hängt, trinkt 2.", 2, CardCategory.MINI_GAME),
                seed("Alle tauschen für eine Runde die Namen. Fehler kostet 1.", 1, CardCategory.RULE),
                seed("Du bist Fragenmeister bis zur nächsten Karte. Wer antwortet, trinkt 1.", 1, CardCategory.RULE),
                seed("Fordere jemanden zum Blickduell heraus. Verlierer trinkt 2.", 2, CardCategory.DUEL),
                seed("Alle trinken 1 und tun so, als wäre das eine Strategie.", 1, CardCategory.EVERYONE),
                seed("Verteile 4 Schlucke, aber jeder darf nur 1 bekommen.", 4, CardCategory.CHALLENGE),
                seed("Der nächste Spieler darf nur noch flüstern. Fehler kostet 1.", 1, CardCategory.RULE),
                seed("Alle zeigen auf die chaotischste Person. Mehrheit trinkt 2.", 2, CardCategory.EVERYONE),
                seed("Sprich bis zu deinem nächsten Zug wie ein Sportkommentator. Vergisst du es: 2.", 2, CardCategory.RULE),
                seed("Alle stehen kurz auf. Wer zuletzt sitzt, verteilt 2.", 2, CardCategory.MINI_GAME),
                seed("Du darfst eine Karte ausdenken. Die Gruppe stimmt ab: spielen oder du trinkst 2.", 2, CardCategory.CHALLENGE),
                seed("Kategorie: Dinge, die man im Club verliert. Wer doppelt, trinkt 1.", 1, CardCategory.MINI_GAME),
                seed("Wähle zwei Spieler für ein Komplimente-Duell. Schwächeres Kompliment trinkt 1.", 1, CardCategory.DUEL),
                seed("Regel: Jede Frage muss mit Gegenfrage beantwortet werden. Fehler kostet 1.", 1, CardCategory.RULE),
                seed("Alle, die gerade nichts verstanden haben, trinken 1. Ehrlichkeit zählt.", 1, CardCategory.EVERYONE),
                seed("Mach 10 Sekunden dramatische Stille. Wer lacht, trinkt 1.", 1, CardCategory.MINI_GAME),
            )
        ),
        pack(
            id = "couples",
            name = "Couples Pack",
            cards = listOf(
                seed("Sag deinem Gegenüber ein Kompliment oder trink 1.", 1, CardCategory.TRUTH),
                seed("Wählt ein Paar. Beide beantworten dieselbe Frage oder trinken 2.", 2, CardCategory.DUEL),
                seed("Wer von euch ist schlechter im Zurückschreiben? Die Person trinkt 1.", 1, CardCategory.TRUTH),
                seed("Nenne einen harmlosen Ick. Keine Diplomatie, sonst 2.", 2, CardCategory.SPICY),
                seed("Macht ein Team-High-Five. Wenn es peinlich wird: beide 1.", 1, CardCategory.CHALLENGE),
                seed("Wähle jemanden: ehrliche Frage beantworten oder 2 trinken.", 2, CardCategory.TRUTH),
                seed("Alle vergeben den Preis für beste Flirt-Energie. Gewinner verteilt 2.", 2, CardCategory.EVERYONE),
                seed("Erzähle von deinem besten Date-Move oder trink 2.", 2, CardCategory.SPICY),
                seed("Wer würde eher einen Jahrestag vergessen? Mehrheit entscheidet, Person trinkt 1.", 1, CardCategory.TRUTH),
                seed("Nenne etwas, das sofort sympathisch macht. Keine Wiederholungen.", 1, CardCategory.MINI_GAME),
                seed("Wählt ein Duo. Beide sagen gleichzeitig ihren perfekten Snack. Unterschied: beide 1.", 1, CardCategory.DUEL),
                seed("Spicy Frage: Was ist eine harmlose Sache, die du attraktiv findest?", 1, CardCategory.SPICY),
                seed("Regel: Bis zur nächsten Runde dürfen Paare nicht nebeneinander reden.", 1, CardCategory.RULE),
                seed("Alle Singles verteilen 1, alle Vergebenen trinken 1.", 1, CardCategory.EVERYONE),
                seed("Sag, wer hier den besten Dating-Rat geben würde. Die Person verteilt 2.", 2, CardCategory.TRUTH),
                seed("Duo-Challenge: Erfindet in 15 Sekunden einen Paarnamen für zwei Spieler.", 1, CardCategory.CHALLENGE),
            )
        ),
        pack(
            id = "preparty",
            name = "Preparty Pack",
            cards = listOf(
                seed("Wer als Nächstes Musik aussuchen darf, trinkt vorher 1.", 1, CardCategory.CHALLENGE),
                seed("Alle mit weißen Schuhen trinken 1.", 1, CardCategory.EVERYONE),
                seed("Kategorie: Dinge, die man vor dem Losgehen vergisst. Wer hängt, trinkt 1.", 1, CardCategory.MINI_GAME),
                seed("Der Spieler mit dem vollsten Akku verteilt 2.", 2, CardCategory.EVERYONE),
                seed("Wahrheit: Was ist dein peinlichster Party-Song?", 1, CardCategory.TRUTH),
                seed("Regel: Niemand darf das Wort 'später' sagen. Fehler kostet 1.", 1, CardCategory.RULE),
                seed("Zeig deinen besten Türsteher-Blick. Gruppe lacht? Du trinkst 1.", 1, CardCategory.CHALLENGE),
                seed("Fordere jemanden zu einem Outfit-Kompliment-Duell heraus. Verlierer trinkt 1.", 1, CardCategory.DUEL),
                seed("Alle, die heute noch nichts gegessen haben, trinken Wasser und pausieren diese Karte.", 1, CardCategory.EVERYONE),
                seed("Verteile 3 Schlucke an Leute, die bereit aussehen.", 3, CardCategory.CHALLENGE),
                seed("Kategorie: schlechte Clubnamen. Wer doppelt, trinkt 1.", 1, CardCategory.MINI_GAME),
                seed("Wahrheit: Welchen Song skipst du nur heimlich?", 1, CardCategory.TRUTH),
                seed("Regel: Bis zur nächsten Karte wird jeder Toast ernst vorgetragen.", 1, CardCategory.RULE),
                seed("Alle zeigen auf die Person mit der besten Ausgeh-Energie. Person verteilt 2.", 2, CardCategory.EVERYONE),
                seed("Mach einen 5-Sekunden-Dance-Move oder trink 2.", 2, CardCategory.CHALLENGE),
                seed("Duell: Wer schneller drei Bars in der Nähe nennen kann, verteilt 2.", 2, CardCategory.DUEL),
            )
        ),
        pack(
            id = "wg_abend",
            name = "WG Abend Pack",
            cards = listOf(
                seed("Wer zuletzt den Müll rausgebracht hat, verteilt 2.", 2, CardCategory.EVERYONE),
                seed("Kategorie: Dinge im Kühlschrank, die keiner zugeben will. Wer hängt, trinkt 1.", 1, CardCategory.MINI_GAME),
                seed("Wahrheit: Was ist deine schlimmste Mitbewohner-Eigenschaft?", 1, CardCategory.TRUTH),
                seed("Regel: Bis zu deinem nächsten Zug muss jeder vor dem Trinken 'Miete' sagen.", 1, CardCategory.RULE),
                seed("Der Gastgeber verteilt 3 und bekommt dafür ein ehrliches Danke.", 3, CardCategory.CHALLENGE),
                seed("Alle, die schon mal Essen von jemand anderem genommen haben, trinken 1.", 1, CardCategory.EVERYONE),
                seed("Duell: Wer besser einen defekten Drucker imitieren kann, verteilt 2.", 2, CardCategory.DUEL),
                seed("Nenne eine Sache, die in jeder Wohnung fehlt. Gruppe stimmt ab: gut oder 1 trinken.", 1, CardCategory.CHALLENGE),
                seed("Alle, die heute im Homeoffice waren, trinken 1.", 1, CardCategory.EVERYONE),
                seed("Kategorie: Nachrichten im Gruppenchat. Wer lacht, trinkt 1.", 1, CardCategory.MINI_GAME),
                seed("Wahrheit: Welche Kleinigkeit nervt dich irrational?", 1, CardCategory.TRUTH),
                seed("Regel: Niemand darf 'kurz' sagen. Es ist nie kurz. Fehler kostet 1.", 1, CardCategory.RULE),
                seed("Wähle jemanden, der dir eine Küchenregel geben darf. Brichst du sie: 2.", 2, CardCategory.CHALLENGE),
                seed("Alle zeigen auf die ordentlichste Person. Die Person verteilt 2.", 2, CardCategory.EVERYONE),
                seed("Duell: Zwei Spieler nennen abwechselnd Putzmittel. Wer hängt, trinkt 1.", 1, CardCategory.DUEL),
                seed("Erzähle die beste Ausrede, warum du nicht gespült hast, oder trink 2.", 2, CardCategory.TRUTH),
            )
        ),
        pack(
            id = "bar_runde",
            name = "Bar Runde Pack",
            cards = listOf(
                seed("Alle, die heute Bargeld dabeihaben, trinken 1.", 1, CardCategory.EVERYONE),
                seed("Wahrheit: Was bestellst du, wenn du cool wirken willst?", 1, CardCategory.TRUTH),
                seed("Kategorie: Snacks, die jede Bar haben sollte. Wer hängt, trinkt 1.", 1, CardCategory.MINI_GAME),
                seed("Regel: Bis zur nächsten Karte darf niemand 'Prost' sagen. Fehler kostet 1.", 1, CardCategory.RULE),
                seed("Verteile 2 Schlucke an die Person mit der besten Bestellung.", 2, CardCategory.CHALLENGE),
                seed("Duell: Wer kann besser so tun, als hätte er die Karte verstanden? Gruppe entscheidet.", 1, CardCategory.DUEL),
                seed("Alle mit einem Getränk in der linken Hand trinken 1.", 1, CardCategory.EVERYONE),
                seed("Spicy Frage: Was war dein mutigster erster Schritt?", 1, CardCategory.SPICY),
                seed("Wähle jemanden, der einen Mini-Toast auf dich halten muss. Schwach? Beide 1.", 1, CardCategory.CHALLENGE),
                seed("Kategorie: Dinge, die man um 2 Uhr nachts sagt. Wer doppelt, trinkt 1.", 1, CardCategory.MINI_GAME),
                seed("Wahrheit: Was war dein teuerster unnötiger Abend?", 2, CardCategory.TRUTH),
                seed("Regel: Jede Bestellung wird ab jetzt nobel kommentiert. Fehler kostet 1.", 1, CardCategory.RULE),
                seed("Alle zeigen auf die Person, die am ehesten den Heimweg plant. Person verteilt 2.", 2, CardCategory.EVERYONE),
                seed("Duell: Zwei Spieler erfinden Cocktailnamen. Gruppe wählt, Verlierer trinkt 1.", 1, CardCategory.DUEL),
                seed("Erzähl eine Bar-Story in genau einem Satz oder trink 2.", 2, CardCategory.TRUTH),
                seed("Verteile 4 Schlucke, aber nur an Leute, die noch sitzen.", 4, CardCategory.CHALLENGE),
            )
        ),
        pack(
            id = "spieleabend",
            name = "Spieleabend Pack",
            cards = listOf(
                seed("Kategorie: Brettspiele, die Freundschaften testen. Wer hängt, trinkt 1.", 1, CardCategory.MINI_GAME),
                seed("Alle, die schon mal Regeln nachgeschlagen haben, trinken 1.", 1, CardCategory.EVERYONE),
                seed("Wahrheit: Bei welchem Spiel wirst du unnötig ehrgeizig?", 1, CardCategory.TRUTH),
                seed("Regel: Niemand darf 'nur noch eine Runde' sagen. Fehler kostet 1.", 1, CardCategory.RULE),
                seed("Der aktuelle Spieler verteilt 2 an die Person mit dem besten Pokerface.", 2, CardCategory.CHALLENGE),
                seed("Alle, die heute Snacks mitgebracht haben, verteilen 1.", 1, CardCategory.EVERYONE),
                seed("Mini-Spiel: Zähle rückwärts von 20. Fehler oder Lachen kostet 1.", 1, CardCategory.MINI_GAME),
                seed("Erzähle deinen größten Spielabend-Triumph oder trink 2.", 2, CardCategory.TRUTH),
                seed("Regel: Erklärungen müssen wie ein Filmtrailer klingen. Fehler kostet 1.", 1, CardCategory.RULE),
                seed("Alle zeigen auf die Person, die am ehesten schummelt. Mehrheit trinkt 1.", 1, CardCategory.EVERYONE),
                seed("Duell: Schere-Stein-Papier gegen links. Verlierer trinkt 1.", 1, CardCategory.DUEL),
                seed("Kategorie: Dinge, die auf jedem Tisch landen. Wer doppelt, trinkt 1.", 1, CardCategory.MINI_GAME),
                seed("Der Spieler mit den meisten Punkten verteilt 2. Bei Gleichstand trinken die Führenden 1.", 2, CardCategory.CHALLENGE),
                seed("Pausenkarte: Trinkt Wasser, atmet kurz durch und sammelt euch für die nächste Runde.", 1, CardCategory.EVERYONE),
                seed("Wahrheit: Welche Hausregel würdest du sofort abschaffen?", 1, CardCategory.TRUTH),
                seed("Duell: Zwei Spieler nennen abwechselnd Kartenspiele. Wer hängt, trinkt 1.", 1, CardCategory.DUEL),
            )
        ),
        pack(
            id = "feierabend",
            name = "Feierabend Pack",
            cards = listOf(
                seed("Alle, die heute ein Meeting hatten, trinken 1.", 1, CardCategory.EVERYONE),
                seed("Wahrheit: Was war dein unnötigster kleiner Sieg diese Woche?", 1, CardCategory.TRUTH),
                seed("Kategorie: Dinge, die man nach Feierabend nicht mehr hören will. Wer hängt, trinkt 1.", 1, CardCategory.MINI_GAME),
                seed("Regel: Bis zur nächsten Karte beginnt jeder Satz mit 'Dienstlich gesehen'. Fehler kostet 1.", 1, CardCategory.RULE),
                seed("Der aktuelle Spieler verteilt 2 an die Person mit dem entspanntesten Gesicht.", 2, CardCategory.CHALLENGE),
                seed("Alle, die heute mehr als drei Tabs offen hatten, trinken 1.", 1, CardCategory.EVERYONE),
                seed("Duell: Zwei Spieler nennen abwechselnd Bürosnacks. Wer hängt, trinkt 1.", 1, CardCategory.DUEL),
                seed("Erzähle deinen besten Feierabend-Moment der Woche oder trink 2.", 2, CardCategory.TRUTH),
                seed("Pausenkarte: Alle trinken Wasser oder legen kurz das Getränk ab.", 1, CardCategory.EVERYONE),
                seed("Regel: Niemand darf 'morgen' sagen. Fehler kostet 1.", 1, CardCategory.RULE),
                seed("Alle zeigen auf die Person mit der ruhigsten Energie. Person verteilt 2.", 2, CardCategory.EVERYONE),
                seed("Kategorie: perfekte Feierabend-Snacks. Wer doppelt, trinkt 1.", 1, CardCategory.MINI_GAME),
                seed("Wähle jemanden für einen Mini-Toast auf die Woche. Schwach? Beide 1.", 1, CardCategory.CHALLENGE),
                seed("Wahrheit: Welche Aufgabe hast du heute erfolgreich ignoriert?", 1, CardCategory.TRUTH),
                seed("Duell: Zwei Spieler machen ihren besten 'endlich frei'-Blick. Gruppe entscheidet.", 1, CardCategory.DUEL),
                seed("Verteile 3 Schlucke an Leute, die gerade wirklich angekommen wirken.", 3, CardCategory.CHALLENGE),
            )
        ),
    )

    fun find(id: String): BuiltInPack? = all.firstOrNull { it.id == id }

    private fun pack(
        id: String,
        name: String,
        cards: List<CardSeed>
    ): BuiltInPack = BuiltInPack(
        id = id,
        name = name,
        entries = cards.map { card ->
            DrinkEntry(
                text = card.text,
                drinks = card.drinks,
                category = card.category.id,
                packName = name,
            )
        }
    )

    private fun seed(
        text: String,
        drinks: Int,
        category: CardCategory,
    ): CardSeed = CardSeed(text, drinks, category)
}

private data class CardSeed(
    val text: String,
    val drinks: Int,
    val category: CardCategory,
)
