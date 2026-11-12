package app.gamenative.utils

import app.gamenative.utils.MachineIdentityLog.Entry
import app.gamenative.utils.MachineIdentityLog.Event
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * Loads or creates the [MachineIdentity] of this installation and writes the identity log ([MachineIdentityLog]).
 *
 * What it adds to [MachineIdentity.loadOrCreate]:
 * - Every creation is logged (`erzeugt`; `erzeugt_ungueltig` with the reason when a file existed but was unusable).
 * - A failed save is logged (`speichern_fehlgeschlagen`) and the identity is kept in memory: as long as the process lives the
 *   next start tries to store the same identity again instead of creating a new one (`gespeichert` once it worked).
 * - `geladen` is logged only when the short id or the app version changed since the last entry.
 * - On request ([regenerate]) a new identity replaces the current one, which moves to the archive ([archiveFile], app storage only);
 *   [restore] brings an archived one back and archives the active one. Both refuse while the game runs; [regenerate] at most
 *   once per 24 hours (restoring neither counts nor resets that).
 * - The identity file does not go away silently: a second copy lives in another folder of the app storage ([copyFile], written with
 *   every save). A missing or unusable main file is restored from it (`aus_zweitkopie`); a file that is only partly valid keeps its valid
 *   fields and renews the others (`repariert`, field names only); the unusable text is kept in [damagedFile] first. When both files are
 *   valid and differ, the main file wins.
 * - A backup copy of the identity in the game folder ([keepFile], see [MachineIdentityKeep]) survives a reinstall of the app: with no identity
 *   in the app storage (neither main file nor second copy) a valid copy of the same device model is taken over (`aus_spielordner`); a
 *   copy of another model, or any copy that differs from an identity that has already been applied, goes to the archive instead
 *   (`kopie_abgelegt`). A new identity that was never applied to a game start is replaced by a valid copy of the same model that shows up
 *   later. Written with every save; can be switched off ([setKeepEnabled]), which deletes the copy. A missing access is left out silently
 *   (one event). Display paths ([peek], [summary], [keepStatus]) never create an identity.
 * - Besides the log in the app storage a history without details goes to [historyFile] in the game folder; it survives a
 *   reinstall of the app. When the folder is missing or not writable it is silently left out.
 *
 * No Android classes, so it runs in plain JUnit. Writing the log never breaks the start: all errors are swallowed.
 */
class MachineIdentityStore(
    private val identityFile: File,
    private val logFile: File,
    private val historyFile: () -> File?,
    private val versionCode: Int,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
    private val random: () -> Random = { SecureRandom().asKotlinRandom() },
    private val archiveFile: File = File(identityFile.absoluteFile.parentFile, MachineIdentityArchive.FILE_NAME),
    private val systemFile: File = File(identityFile.absoluteFile.parentFile, SYSTEM_FILE_NAME),
    /** Second copy of the identity; another folder than [identityFile] (the app wires `noBackupFilesDir`). Never in a backup or a report. */
    private val copyFile: File = File(File(identityFile.absoluteFile.parentFile, COPY_DIR_NAME), identityFile.name),
    /** Unusable identity text that was replaced (last [DAMAGED_KEEP]); app storage only. */
    private val damagedFile: File = File(identityFile.absoluteFile.parentFile, DAMAGED_FILE_NAME),
    private val prefixFile: File = File(identityFile.absoluteFile.parentFile, PrefixTraits.FILE_NAME),
    /** Backup copy in the game folder (next to the history file); null = not wired (tests). The folder is never created. */
    private val keepFile: () -> File? = { null },
    /** Device trait written into the copy ([MachineIdentityKeep.deviceTrait]); a copy with another trait is "from another model". */
    private val deviceTrait: String = "00000000",
    /** Exists = "keep the identity in the game folder" is switched off. */
    private val keepOffFile: File = File(identityFile.absoluteFile.parentFile, MachineIdentityKeep.OFF_FILE_NAME),
    /** Short ids that were applied for a game start; app storage only. */
    private val usedFile: File = File(identityFile.absoluteFile.parentFile, MachineIdentityKeep.USED_FILE_NAME),
) {
    companion object {
        /** Name of the stored system view in the app storage (parts, fingerprints, coarse features; never plain values). */
        const val SYSTEM_FILE_NAME = "wow_system_view.txt"

        /** Folder name of the second copy when no other place is given (tests). */
        const val COPY_DIR_NAME = "copy"

        const val DAMAGED_FILE_NAME = "wow_identity_damaged.txt"
        private const val DAMAGED_KEEP = 3
        private const val DAMAGED_MAX_CHARS = 2000
    }

    private val lock = Any()

    /** Created but not storable identity, kept for the retry at the next start (process lifetime only). */
    private var unsaved: MachineIdentity? = null

    fun loadOrCreate(): MachineIdentity = synchronized(lock) {
        val inspection = MachineIdentity.inspect(identityFile)
        val copy = MachineIdentity.inspect(copyFile)
        if (inspection is MachineIdentity.Inspection.Valid) {
            unsaved = null
            // The main file wins; the copy follows it (also the first start after an update that brought the copy).
            if (copy !is MachineIdentity.Inspection.Valid || copy.identity != inspection.identity) MachineIdentity.save(copyFile, inspection.identity)
            val active = reconcileKeep(inspection.identity)
            val fingerprint = MachineIdentityLog.fingerprint(active)
            if (MachineIdentityLog.shouldLogLoaded(readLog(), fingerprint, versionCode)) record(Event.LOADED, fingerprint)
            return active
        }
        val pending = unsaved
        if (pending != null) {
            val fingerprint = MachineIdentityLog.fingerprint(pending)
            if (saveBoth(pending)) {
                unsaved = null
                record(Event.SAVED_LATER, fingerprint)
            } else {
                record(Event.SAVE_FAILED, fingerprint)
            }
            flushKeepNote(fingerprint)
            return pending
        }
        val oldText = MachineIdentity.readText(identityFile)
        if (copy is MachineIdentity.Inspection.Valid) {
            // Main file missing or unusable: the second copy brings the identity back and the main file is written again.
            keepDamaged(oldText)
            val fingerprint = MachineIdentityLog.fingerprint(copy.identity)
            val reason = if (inspection is MachineIdentity.Inspection.Missing) "haupt_fehlt" else "haupt_ungueltig"
            val saved = MachineIdentity.save(identityFile, copy.identity)
            record(Event.FROM_COPY, fingerprint, reason)
            if (!saved) {
                unsaved = copy.identity
                record(Event.SAVE_FAILED, fingerprint)
                return copy.identity
            }
            return reconcileKeep(copy.identity)
        }
        val text = oldText ?: MachineIdentity.readText(copyFile)
        val repair = MachineIdentity.repair(text, random(), clock())
        if (repair != null) {
            // Valid fields stay, only the damaged ones are made new; the old text is kept first.
            keepDamaged(text)
            val fingerprint = MachineIdentityLog.fingerprint(repair.identity)
            val saved = saveBoth(repair.identity)
            markUsed(fingerprint)
            record(Event.REPAIRED, fingerprint, repair.fields.joinToString(","))
            if (!saved) {
                unsaved = repair.identity
                record(Event.SAVE_FAILED, fingerprint)
            }
            flushKeepNote(fingerprint)
            return repair.identity
        }
        // Nothing usable in the app storage: the copy in the game folder (kept over a reinstall) may bring the identity back.
        var foreign: MachineIdentity? = null
        var ignoredReason: String? = null
        when (val folder = readKeep()) {
            is KeepRead.Found -> when (val reading = folder.reading) {
                is MachineIdentityKeep.Reading.Valid ->
                    if (reading.trait == deviceTrait) {
                        if (inspection is MachineIdentity.Inspection.Invalid) keepDamaged(text)
                        return adopt(reading.identity, "keine_kennung")
                    } else {
                        foreign = reading.identity
                    }
                is MachineIdentityKeep.Reading.Invalid -> ignoredReason = reading.reason
            }
            else -> Unit
        }
        if (inspection is MachineIdentity.Inspection.Invalid) keepDamaged(text)
        val created = MachineIdentity.generate(random(), clock())
        val fingerprint = MachineIdentityLog.fingerprint(created)
        // The copy of another model is offered under "earlier IDs" before the new copy replaces it.
        val archivedForeign = foreign?.let { archiveCopy(it) } == true
        val saved = saveBoth(created)
        if (!usedFile.isFile) writeUsed(emptyList())
        if (inspection is MachineIdentity.Inspection.Invalid) {
            record(Event.CREATED_INVALID, fingerprint, inspection.reason)
        } else {
            record(Event.CREATED, fingerprint)
        }
        if (!saved) {
            unsaved = created
            record(Event.SAVE_FAILED, fingerprint)
        }
        if (archivedForeign) record(Event.KEEP_ARCHIVED, fingerprint, "anderes_modell")
        ignoredReason?.let { record(Event.KEEP_IGNORED, fingerprint, it) }
        flushKeepNote(fingerprint)
        created
    }

    // --- Backup copy in the game folder ---

    private enum class KeepResult { NONE, WRITTEN, FAILED }

    private sealed class KeepRead {
        object Off : KeepRead()
        object NotWired : KeepRead()
        object NoAccess : KeepRead()
        object Missing : KeepRead()
        class Found(val reading: MachineIdentityKeep.Reading) : KeepRead()
    }

    /** Outcome of the last [writeKeep]; [flushKeepNote] logs it (after the main event, so the log reads in order). */
    private var keepResult = KeepResult.NONE

    /** Whether the identity is also kept in the game folder (default: yes). */
    fun keepEnabled(): Boolean = !keepOffFile.exists()

    private fun readKeep(): KeepRead {
        if (!keepEnabled()) return KeepRead.Off
        val file = keepFile() ?: return KeepRead.NotWired
        return try {
            val dir = file.absoluteFile.parentFile
            when {
                dir == null || !dir.isDirectory -> KeepRead.NoAccess
                !file.isFile -> KeepRead.Missing
                else -> KeepRead.Found(MachineIdentityKeep.parse(MachineIdentity.readText(file)))
            }
        } catch (e: Exception) {
            KeepRead.NoAccess
        }
    }

    /** Writes the copy into the game folder (atomically); the outcome is kept for [flushKeepNote]. Nothing without a folder, nothing when switched off. */
    private fun writeKeep(identity: MachineIdentity) {
        if (!keepEnabled()) return
        val file = keepFile() ?: return
        keepResult = try {
            val dir = file.absoluteFile.parentFile
            if (dir == null || !dir.isDirectory) {
                KeepResult.FAILED
            } else {
                writeAtomic(file, MachineIdentityKeep.serialize(identity, deviceTrait))
                KeepResult.WRITTEN
            }
        } catch (e: Exception) {
            KeepResult.FAILED
        }
    }

    /** True when the last state event about the copy is a failure that was not followed by a success. */
    private fun keepFailedBefore(): Boolean =
        readLog().lastOrNull { it.event == Event.KEEP_FAILED || it.event == Event.KEEP_WRITTEN }?.event == Event.KEEP_FAILED

    /** Logs a failed copy once, and the first success after it. */
    private fun flushKeepNote(fingerprint: String) {
        val result = keepResult
        keepResult = KeepResult.NONE
        when (result) {
            KeepResult.FAILED -> if (!keepFailedBefore()) record(Event.KEEP_FAILED, fingerprint)
            KeepResult.WRITTEN -> if (keepFailedBefore()) record(Event.KEEP_WRITTEN, fingerprint)
            KeepResult.NONE -> Unit
        }
    }

    private fun readUsed(): List<String>? = try {
        if (usedFile.isFile) MachineIdentityKeep.parseUsed(String(Files.readAllBytes(usedFile.toPath()), StandardCharsets.UTF_8)) else null
    } catch (e: Exception) {
        null
    }

    private fun writeUsed(used: List<String>) {
        try {
            writeAtomic(usedFile, MachineIdentityKeep.serializeUsed(used))
        } catch (e: Exception) {
            // Without the list every identity counts as used: nothing is replaced by mistake.
        }
    }

    private fun markUsed(fingerprint: String) {
        val used = readUsed().orEmpty()
        if (fingerprint !in used) writeUsed(used + fingerprint)
    }

    /** The identity was applied for a game start (the prefix got its values): from now on it is never replaced by a copy from the game folder. */
    fun markApplied(identity: MachineIdentity) = synchronized(lock) { markUsed(MachineIdentityLog.fingerprint(identity)) }

    /** Takes [copy] from the game folder as the identity of this installation and saves it to the app storage. */
    private fun adopt(copy: MachineIdentity, detail: String): MachineIdentity {
        val fingerprint = MachineIdentityLog.fingerprint(copy)
        val saved = saveBoth(copy)
        if (!usedFile.isFile) writeUsed(emptyList())
        record(Event.FROM_FOLDER, fingerprint, detail)
        if (!saved) {
            unsaved = copy
            record(Event.SAVE_FAILED, fingerprint)
        }
        flushKeepNote(fingerprint)
        return copy
    }

    /** Puts an identity found in the game folder into the archive (not twice); false when it was already there or could not be stored. */
    private fun archiveCopy(identity: MachineIdentity): Boolean {
        val items = readArchive()
        if (items.any { it.identity == identity }) return false
        val now = clock()
        val item = MachineIdentityArchive.Item(identity, identity.installDate.coerceAtMost(now), now)
        return try {
            writeAtomic(archiveFile, MachineIdentityArchive.serialize(MachineIdentityArchive.push(items, item)))
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * An identity is valid in the app storage; look at the copy in the game folder. No copy: write it. Damaged copy: log it, write a new one.
     * A valid copy of a different identity: when this one was never applied and the copy is from the same model, the copy replaces it (the
     * unused one lapses); otherwise the copy goes to the archive and ours replaces it in the folder. Returns the identity to use.
     */
    private fun reconcileKeep(active: MachineIdentity): MachineIdentity {
        val fingerprint = MachineIdentityLog.fingerprint(active)
        var used = readUsed()
        if (used == null) {
            // Identity from before the copy existed: its history is unknown, so it counts as used.
            markUsed(fingerprint)
            used = readUsed()
        }
        when (val folder = readKeep()) {
            KeepRead.Off, KeepRead.NotWired -> return active
            KeepRead.NoAccess -> keepResult = KeepResult.FAILED
            KeepRead.Missing -> writeKeep(active)
            is KeepRead.Found -> when (val reading = folder.reading) {
                is MachineIdentityKeep.Reading.Invalid -> {
                    record(Event.KEEP_IGNORED, fingerprint, reading.reason)
                    writeKeep(active)
                }
                is MachineIdentityKeep.Reading.Valid -> {
                    if (reading.identity == active) return active
                    val sameModel = reading.trait == deviceTrait
                    if (sameModel && !MachineIdentityKeep.isUsed(used, fingerprint)) {
                        return adopt(reading.identity, MachineIdentityLog.discardedDetail(fingerprint))
                    }
                    if (archiveCopy(reading.identity)) record(Event.KEEP_ARCHIVED, fingerprint, if (sameModel) "neue_benutzt" else "anderes_modell")
                    writeKeep(active)
                }
            }
        }
        flushKeepNote(fingerprint)
        return active
    }

    /** State of the copy in the game folder for the card; reads only, never creates anything. */
    fun keepStatus(): MachineIdentityKeep.Status = synchronized(lock) {
        val active = current()
        when (val folder = readKeep()) {
            KeepRead.Off -> MachineIdentityKeep.Status.OFF
            KeepRead.NotWired, KeepRead.NoAccess -> MachineIdentityKeep.Status.NO_ACCESS
            KeepRead.Missing -> MachineIdentityKeep.Status.NONE
            is KeepRead.Found -> when (val reading = folder.reading) {
                is MachineIdentityKeep.Reading.Invalid -> MachineIdentityKeep.Status.DAMAGED
                is MachineIdentityKeep.Reading.Valid -> when {
                    active != null && reading.identity == active -> MachineIdentityKeep.Status.MATCHES
                    reading.trait == deviceTrait -> MachineIdentityKeep.Status.OTHER_IDENTITY
                    else -> MachineIdentityKeep.Status.OTHER_MODEL
                }
            }
        }
    }

    /**
     * Switches "keep the identity in the game folder" on or off. Off: the copy is deleted and no longer written (and no longer read).
     * On: the copy is written now. Returns false when the switch could not be stored or the copy could not be deleted.
     */
    fun setKeepEnabled(on: Boolean): Boolean = synchronized(lock) {
        val active = current()
        val fingerprint = active?.let { MachineIdentityLog.fingerprint(it) }
        if (on) {
            keepOffFile.delete()
            if (keepOffFile.exists()) return false
            if (active != null && fingerprint != null) {
                record(Event.KEEP_ON, fingerprint)
                writeKeep(active)
                flushKeepNote(fingerprint)
            }
            true
        } else {
            try {
                writeAtomic(keepOffFile, "off\n")
            } catch (e: Exception) {
                return false
            }
            val file = try {
                keepFile()
            } catch (e: Exception) {
                null
            }
            val gone = try {
                file == null || !file.exists() || file.delete()
            } catch (e: Exception) {
                false
            }
            if (fingerprint != null) record(Event.KEEP_OFF, fingerprint)
            gone
        }
    }

    /**
     * Writes the main file and then the second copy. With [copyOnFailure] false the copy is only written when the main file was saved,
     * so a failed swap leaves both files at the identity that stays active. Returns whether the main file was saved.
     */
    private fun saveBoth(identity: MachineIdentity, copyOnFailure: Boolean = true): Boolean {
        keepResult = KeepResult.NONE
        val saved = MachineIdentity.save(identityFile, identity)
        if (saved || copyOnFailure) {
            MachineIdentity.save(copyFile, identity)
            writeKeep(identity)
        }
        return saved
    }

    /** Keeps the unusable text of an identity file before it is replaced (the last [DAMAGED_KEEP], each cut to [DAMAGED_MAX_CHARS]). */
    private fun keepDamaged(text: String?) {
        if (text.isNullOrBlank()) return
        try {
            val old = if (damagedFile.isFile) String(Files.readAllBytes(damagedFile.toPath()), StandardCharsets.UTF_8) else ""
            val blocks = old.split("\n---\n").filter { it.isNotBlank() }
            val block = "savedAt=" + clock() + "\n" + text.take(DAMAGED_MAX_CHARS).trimEnd() + "\n"
            val kept = (listOf(block) + blocks).take(DAMAGED_KEEP)
            writeAtomic(damagedFile, kept.joinToString("\n---\n"))
        } catch (e: Exception) {
            // Keeping the old text is a courtesy; it must not stop the repair.
        }
    }

    /**
     * Logs which prefix values do not match [identity] ([fields] are names only), [phase] says when it was looked at
     * (`vor_anwenden`, `nach_anwenden`, `nach_spiel`). Nothing is written for an empty list.
     */
    fun recordPrefixMismatch(identity: MachineIdentity, phase: String, fields: List<String>) {
        if (fields.isEmpty()) return
        synchronized(lock) {
            record(Event.PREFIX_MISMATCH, MachineIdentityLog.fingerprint(identity), phase + ":" + fields.joinToString(","))
        }
    }

    /** Result of [regenerate] and [restore]. */
    sealed class Outcome {
        /** Done; [fingerprint] is the short id of the identity now active. */
        class Done(val fingerprint: String) : Outcome()
        object GameRunning : Outcome()

        /** [regenerate] refused: allowed again from [until] (seconds since 1970). */
        class Locked(val until: Long) : Outcome()

        /** There is no identity yet (it is created at the first game start) or no such archive entry. */
        object NothingToChange : Outcome()

        /** The archive or the identity could not be stored; the old identity stays active. */
        object SaveFailed : Outcome()
    }

    /** The identity in use (stored or kept in memory); null before the first game start. Never creates one. */
    fun peek(): MachineIdentity? = synchronized(lock) { current() }

    private fun current(): MachineIdentity? =
        (MachineIdentity.inspect(identityFile) as? MachineIdentity.Inspection.Valid)?.identity ?: unsaved
            ?: (MachineIdentity.inspect(copyFile) as? MachineIdentity.Inspection.Valid)?.identity

    /** Archive entries for the screen (short id and period only), newest first. */
    fun archiveInfos(): List<MachineIdentityArchive.Info> = synchronized(lock) { MachineIdentityArchive.infos(readArchive()) }

    /** From when "new identity" is allowed again; null when it is allowed now. */
    fun regenerateLockedUntil(): Long? = synchronized(lock) { lockedUntil(clock()) }

    private fun lockedUntil(now: Long): Long? =
        listOfNotNull(
            MachineIdentityLog.regenerateLockedUntil(readLog(), now),
            MachineIdentityLog.regenerateLockedUntil(readHistory(), now),
        ).maxOrNull()

    /**
     * Creates a new identity on request. The old one goes to the archive first; if the archive or the new identity cannot be
     * stored nothing changes. Logged as `neu_auf_wunsch` (new short id, old one in the detail).
     */
    fun regenerate(gameRunning: Boolean): Outcome = synchronized(lock) {
        if (gameRunning) return Outcome.GameRunning
        val old = current() ?: return Outcome.NothingToChange
        val now = clock()
        lockedUntil(now)?.let { return Outcome.Locked(it) }
        val oldFingerprint = MachineIdentityLog.fingerprint(old)
        var created = MachineIdentity.generate(random(), now)
        var tries = 0
        while (created == old && tries++ < 5) created = MachineIdentity.generate(random(), now)
        val fingerprint = swapIn(created, oldFingerprint, Event.NEW_ON_REQUEST) { MachineIdentityArchive.push(it, archiveItem(old, oldFingerprint, now)) }
            ?: return Outcome.SaveFailed
        Outcome.Done(fingerprint)
    }

    /**
     * Brings archive entry [index] back (newest is 0). The active identity takes its place in the archive. Not limited by the
     * 24 hours of [regenerate] and does not reset them. Logged as `wiederhergestellt`.
     */
    fun restore(index: Int, gameRunning: Boolean): Outcome = synchronized(lock) {
        if (gameRunning) return Outcome.GameRunning
        val old = current() ?: return Outcome.NothingToChange
        val items = readArchive()
        val chosen = items.getOrNull(index) ?: return Outcome.NothingToChange
        val now = clock()
        val oldFingerprint = MachineIdentityLog.fingerprint(old)
        val fingerprint = swapIn(chosen.identity, oldFingerprint, Event.RESTORED) {
            MachineIdentityArchive.swap(it, index, old, activeSince(old, oldFingerprint), now)?.items ?: it
        } ?: return Outcome.SaveFailed
        Outcome.Done(fingerprint)
    }

    private fun activeSince(identity: MachineIdentity, fingerprint: String): Long {
        val summary = MachineIdentityLog.summarize(readLog(), readHistory())
        return if (summary.fingerprint == fingerprint) summary.since ?: identity.installDate else identity.installDate
    }

    private fun archiveItem(old: MachineIdentity, fingerprint: String, now: Long) =
        MachineIdentityArchive.Item(old, activeSince(old, fingerprint).coerceAtMost(now), now)

    /** Writes the new archive, then the new identity; rolls the archive back when the identity cannot be stored. Null on failure. */
    private fun swapIn(
        next: MachineIdentity,
        oldFingerprint: String,
        event: Event,
        newArchive: (List<MachineIdentityArchive.Item>) -> List<MachineIdentityArchive.Item>,
    ): String? {
        val before = readArchive()
        try {
            writeAtomic(archiveFile, MachineIdentityArchive.serialize(newArchive(before)))
        } catch (e: Exception) {
            return null
        }
        if (!saveBoth(next, copyOnFailure = false)) {
            try {
                writeAtomic(archiveFile, MachineIdentityArchive.serialize(before))
            } catch (e: Exception) {
                // The old identity is still the active one; a stray archive entry of it is harmless.
            }
            return null
        }
        unsaved = null
        val fingerprint = MachineIdentityLog.fingerprint(next)
        // A choice of the player counts as used: a copy from the game folder never replaces it.
        markUsed(fingerprint)
        record(event, fingerprint, MachineIdentityLog.oldFingerprintDetail(oldFingerprint))
        flushKeepNote(fingerprint)
        return fingerprint
    }

    private fun readArchive(): List<MachineIdentityArchive.Item> = try {
        if (archiveFile.isFile) MachineIdentityArchive.parse(String(Files.readAllBytes(archiveFile.toPath()), StandardCharsets.UTF_8)) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * Records the system view of this start ([SystemView]): compares it with the stored one of the last start, writes the new one
     * and logs `systemsicht_erfasst` (first time) or `systemsicht_geaendert` (parts named) only when something changed. Never
     * throws; [snapshot] null (capture failed completely) counts as a view with unreadable parts. Returns the change, or null if
     * the view could not be computed.
     */
    fun recordSystemView(identity: MachineIdentity, snapshot: SystemView.Snapshot?): SystemView.Change? = synchronized(lock) {
        try {
            val view = SystemView.compute(snapshot ?: SystemView.Snapshot())
            val change = SystemView.compare(readSystemState(), view)
            if (change !is SystemView.Change.Same) {
                // First the state, then the event: when the state cannot be saved the event comes again, but the log suppresses repeats.
                try {
                    writeAtomic(systemFile, SystemView.serialize(SystemView.stored(view)))
                } catch (e: Exception) {
                    // Without the state file the next start compares against nothing; the event below stays valid.
                }
                val event = if (change is SystemView.Change.Changed) Event.SYSTEM_CHANGED else Event.SYSTEM_CAPTURED
                record(event, MachineIdentityLog.fingerprint(identity), SystemView.eventDetail(view, change))
            }
            change
        } catch (e: Exception) {
            null
        }
    }

    /** The system view stored at the last start; null if there is none yet. */
    fun readSystemState(): SystemView.Stored? = try {
        if (systemFile.isFile) SystemView.parseStored(String(Files.readAllBytes(systemFile.toPath()), StandardCharsets.UTF_8)) else null
    } catch (e: Exception) {
        null
    }

    /**
     * Records the prefix traits measured before the identity is applied ([PrefixTraits]): compares them with the stored ones of the last start,
     * writes the new ones and logs `prefix_merkmale_erfasst` (first time) or `prefix_merkmale_geaendert` (parts named) only when something
     * changed. Never throws; [snapshot] null (reading failed completely) counts as traits that could not be read. Nothing in the prefix is touched.
     */
    fun recordPrefixTraits(identity: MachineIdentity, snapshot: PrefixTraits.Snapshot?): PrefixTraits.Change? = synchronized(lock) {
        try {
            val view = PrefixTraits.compute(snapshot ?: PrefixTraits.Snapshot())
            val change = PrefixTraits.compare(readPrefixState(), view)
            if (change !is PrefixTraits.Change.Same) {
                try {
                    writeAtomic(prefixFile, PrefixTraits.serialize(PrefixTraits.stored(view)))
                } catch (e: Exception) {
                    // Without the state file the next start compares against nothing; the event below stays valid.
                }
                val event = if (change is PrefixTraits.Change.Changed) Event.PREFIX_TRAITS_CHANGED else Event.PREFIX_TRAITS_CAPTURED
                record(event, MachineIdentityLog.fingerprint(identity), PrefixTraits.eventDetail(view, change))
            }
            change
        } catch (e: Exception) {
            null
        }
    }

    /** The prefix traits stored at the last start; null if there are none yet. */
    fun readPrefixState(): PrefixTraits.Stored? = try {
        if (prefixFile.isFile) PrefixTraits.parseStored(String(Files.readAllBytes(prefixFile.toPath()), StandardCharsets.UTF_8)) else null
    } catch (e: Exception) {
        null
    }

    /** Summary of the prefix traits for the diagnostics page: stored parts plus events of both histories. */
    fun prefixSummary(): PrefixTraits.Summary = synchronized(lock) { PrefixTraits.summarize(readLog(), readHistory(), readPrefixState()) }

    /** Summary of the system view for the diagnostics page: stored parts plus events of both histories. */
    fun systemSummary(): SystemView.Summary = synchronized(lock) { SystemView.summarize(readLog(), readHistory(), readSystemState()) }

    fun summary(): MachineIdentityLog.Summary = synchronized(lock) { MachineIdentityLog.summarize(readLog(), readHistory()) }

    fun readLog(): List<Entry> = read(logFile)

    fun readHistory(): List<Entry> = historyFile()?.let { read(it) }.orEmpty()

    private fun read(file: File): List<Entry> = try {
        if (file.isFile) MachineIdentityLog.parse(String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    private fun record(event: Event, fingerprint: String, detail: String = "") {
        val entry = Entry(clock(), fingerprint, versionCode, event, MachineIdentityLog.cleanDetail(detail))
        try {
            val log = readLog()
            val updated = MachineIdentityLog.append(log, entry)
            if (updated !== log) writeAtomic(logFile, MachineIdentityLog.serialize(updated))
        } catch (e: Exception) {
            // The log must never break the start.
        }
        try {
            val file = historyFile() ?: return
            if (file.absoluteFile.parentFile?.isDirectory != true) return
            val history = read(file)
            val plain = MachineIdentityLog.forHistory(entry)
            val updated = MachineIdentityLog.append(history, plain)
            if (updated !== history) writeAtomic(file, MachineIdentityLog.serialize(updated, withDetail = false))
        } catch (e: Exception) {
            // No access to the game folder: the history there is simply left out.
        }
    }

    private fun writeAtomic(file: File, text: String) = MachineIdentity.writeAtomic(file, text.toByteArray(StandardCharsets.UTF_8))
}
