package app.gamenative.backup

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Ein Profil, das auf dem Gerät schon liegt: [json] ist der Dateiinhalt (null, wenn nicht lesbar). */
internal data class ExistingProfile(val id: Int, val name: String, val json: String?)

/** Was bei einem Profil mit gleichem Namen geschieht: das vorhandene ersetzen oder beide behalten (das neue bekommt einen anderen Namen). */
internal enum class ConflictPolicy { REPLACE, KEEP_BOTH }

internal enum class ProfileAction {
    /** Neues Profil, so noch nicht vorhanden. */
    CREATE,

    /** Ein Profil gleichen Namens wird mit dem aus dem Backup überschrieben (gleiche Datei, gleiche Id). */
    REPLACE,

    /** Gleicher Name, anderer Inhalt, "beide behalten": das Profil aus dem Backup kommt unter neuem Namen und neuer Id dazu. */
    ADD_RENAMED,

    /** Gleicher Name und gleicher Inhalt: nichts zu tun. */
    IDENTICAL,
}

/** Ein Schritt des Import-Plans: in welche Datei ([targetId]) mit welchem Namen welcher Inhalt ([json], Id und Name schon gesetzt) kommt. */
internal data class ProfileStep(
    val action: ProfileAction,
    val sourceName: String,
    val targetId: Int,
    val targetName: String,
    val json: String,
)

internal data class ProfilePlan(val steps: List<ProfileStep>) {
    fun count(action: ProfileAction): Int = steps.count { it.action == action }

    /** Profile, die tatsächlich geschrieben werden. */
    val writes: List<ProfileStep> get() = steps.filter { it.action != ProfileAction.IDENTICAL }
}

/**
 * Plant den Import der Profile (rein, per JUnit geprüft). Profile werden über den **Namen** zugeordnet, nicht über die Id: die
 * Betriebsarten finden ihr Profil über den Namen ("Touch (Mein Layout)" usw.), und gleiche Ids auf zwei Geräten meinen oft Verschiedenes.
 * Nichts wird stillschweigend überschrieben: gleicher Name und anderer Inhalt ist ein Konflikt, [ConflictPolicy] entscheidet.
 */
internal object BackupImportPlan {
    /** Namen der Profile aus dem Backup, die auf dem Gerät mit anderem Inhalt vorhanden sind (für die Rückfrage). */
    fun conflicts(incoming: List<BackupProfile>, existing: List<ExistingProfile>): List<String> =
        incoming.filter { profile ->
            val same = existing.firstOrNull { it.name == profile.name }
            same != null && !sameContent(same.json, profile.json)
        }.map { it.name }.distinct()

    fun plan(incoming: List<BackupProfile>, existing: List<ExistingProfile>, policy: ConflictPolicy): ProfilePlan {
        val taken = HashMap<String, Int>()
        for (e in existing) taken.putIfAbsent(e.name, e.id)
        val existingJson = HashMap<String, String?>()
        for (e in existing) existingJson.putIfAbsent(e.name, e.json)
        var nextId = (existing.maxOfOrNull { it.id } ?: -1) + 1
        val seenIncoming = HashSet<String>()
        val steps = ArrayList<ProfileStep>()
        for (profile in incoming) {
            val sameName = taken[profile.name]
            // Zweimal derselbe Name im Backup: das zweite ist immer ein eigenes Profil.
            val duplicateInBackup = !seenIncoming.add(profile.name)
            if (sameName == null) {
                val id = nextId++
                taken[profile.name] = id
                steps.add(ProfileStep(ProfileAction.CREATE, profile.name, id, profile.name, withIdAndName(profile.json, id, profile.name)))
            } else if (!duplicateInBackup && sameContent(existingJson[profile.name], profile.json)) {
                steps.add(ProfileStep(ProfileAction.IDENTICAL, profile.name, sameName, profile.name, profile.json))
            } else if (!duplicateInBackup && policy == ConflictPolicy.REPLACE) {
                steps.add(ProfileStep(ProfileAction.REPLACE, profile.name, sameName, profile.name, withIdAndName(profile.json, sameName, profile.name)))
            } else {
                val id = nextId++
                val name = uniqueName(profile.name, taken.keys)
                taken[name] = id
                steps.add(ProfileStep(ProfileAction.ADD_RENAMED, profile.name, id, name, withIdAndName(profile.json, id, name)))
            }
        }
        return ProfilePlan(steps)
    }

    /** "Name (Backup)", sonst "Name (Backup 2)", "Name (Backup 3)" ...: der erste Name, der nicht in [taken] steht. */
    fun uniqueName(base: String, taken: Collection<String>): String {
        var candidate = "$base (Backup)"
        var n = 2
        while (candidate in taken) candidate = "$base (Backup $n)".also { n++ }
        return candidate
    }

    fun withIdAndName(json: String, id: Int, name: String): String = JSONObject(json).put("id", id).put("name", name).toString()

    /** Gleicher Inhalt ohne Rücksicht auf Id, Schlüsselreihenfolge und Leerraum; unlesbares ist nie gleich. */
    fun sameContent(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        return try {
            canonical(JSONObject(a)) == canonical(JSONObject(b))
        } catch (e: JSONException) {
            false
        }
    }

    private fun canonical(obj: JSONObject): String {
        val copy = JSONObject()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key != "id") copy.put(key, obj.opt(key))
        }
        return canonicalValue(copy)
    }

    private fun canonicalValue(value: Any?): String = when (value) {
        is JSONObject -> {
            val keys = ArrayList<String>()
            val iterator = value.keys()
            while (iterator.hasNext()) keys.add(iterator.next())
            keys.sort()
            keys.joinToString(",", "{", "}") { k -> JSONObject.quote(k) + ":" + canonicalValue(value.opt(k)) }
        }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { i -> canonicalValue(value.opt(i)) }
        is String -> JSONObject.quote(value)
        is Number -> numberText(value)
        null -> "null"
        else -> value.toString()
    }

    /** 1, 1.0 und 1L sind dieselbe Zahl. */
    private fun numberText(n: Number): String {
        val d = n.toDouble()
        return if (d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 9.0e15) d.toLong().toString() else d.toString()
    }
}
