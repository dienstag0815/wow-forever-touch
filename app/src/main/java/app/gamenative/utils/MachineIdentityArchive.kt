package app.gamenative.utils

/**
 * Ablage früherer Windows-Kennungen (rein, ohne Android, per JUnit geprüft).
 *
 * Wird die Kennung auf Wunsch erneuert, wandert die bisherige hierher und kann später zurückgeholt werden. Die Ablage enthält die
 * vollen Kennungen und liegt deshalb nur im App-Speicher: nie in Log, Fehlerbericht, Verlaufsdatei im Spielordner oder Backup.
 *
 * Reihenfolge: neueste zuerst. Höchstens [MAX_ITEMS]; die älteste fällt weg. Dieselbe Kennung steht nie zweimal darin.
 */
object MachineIdentityArchive {
    const val MAX_ITEMS = 5

    /** Name der Ablagedatei im App-Speicher (`filesDir`). */
    const val FILE_NAME = "wow_identity_archive.txt"

    /** Eine abgelegte Kennung; [from] und [until] (Sekunden seit 1970) sind der Zeitraum, in dem sie zuletzt benutzt wurde. */
    class Item(val identity: MachineIdentity, val from: Long, val until: Long) {
        val fingerprint: String get() = MachineIdentityLog.fingerprint(identity)

        override fun toString(): String = "Item($fingerprint)"

        override fun equals(other: Any?): Boolean = other is Item && identity == other.identity && from == other.from && until == other.until

        override fun hashCode(): Int = identity.hashCode()
    }

    /** Was die Oberfläche von einem Eintrag sieht: nie die Kennung selbst. */
    data class Info(val fingerprint: String, val from: Long, val until: Long)

    fun infos(items: List<Item>): List<Info> = items.map { Info(it.fingerprint, it.from, it.until) }

    /** Legt [item] vorn ab; war dieselbe Kennung schon da, ersetzt der neue Eintrag den alten. Danach höchstens [MAX_ITEMS]. */
    fun push(items: List<Item>, item: Item): List<Item> =
        (listOf(item) + items.filter { it.identity != item.identity }).take(MAX_ITEMS)

    /** Ergebnis von [swap]: die nun aktive Kennung und die neue Ablage. */
    class Swapped(val active: MachineIdentity, val items: List<Item>)

    /**
     * Holt den Eintrag [index] zurück: er wird aktiv, die bisher aktive Kennung ([activeFrom] bis [now]) wandert in die Ablage.
     * Null bei ungültigem [index].
     */
    fun swap(items: List<Item>, index: Int, active: MachineIdentity, activeFrom: Long, now: Long): Swapped? {
        val chosen = items.getOrNull(index) ?: return null
        val rest = items.filterIndexed { i, _ -> i != index }
        return Swapped(chosen.identity, push(rest, Item(active, activeFrom.coerceAtMost(now), now)))
    }

    /** Eine Kennung pro Block (Leerzeile dazwischen): [MachineIdentity.serialize] plus `usedFrom` und `usedUntil`. */
    fun serialize(items: List<Item>): String =
        items.joinToString("\n") { it.identity.serialize() + "usedFrom=${it.from}\nusedUntil=${it.until}\n" }

    /** Unlesbare oder ungültige Blöcke fallen weg; Doppelte auch; mehr als [MAX_ITEMS] werden hinten gekappt. */
    fun parse(text: String?): List<Item> {
        if (text == null) return emptyList()
        val result = ArrayList<Item>()
        for (block in text.split(Regex("\n[ \t\r]*\n"))) {
            val identity = MachineIdentity.parse(block) ?: continue
            val values = HashMap<String, String>()
            for (line in block.lines()) {
                val eq = line.indexOf('=')
                if (eq > 0) values[line.substring(0, eq).trim()] = line.substring(eq + 1).trim()
            }
            val from = values["usedFrom"]?.toLongOrNull() ?: continue
            val until = values["usedUntil"]?.toLongOrNull() ?: continue
            if (result.none { it.identity == identity }) result += Item(identity, from, until)
        }
        return result.take(MAX_ITEMS)
    }
}
