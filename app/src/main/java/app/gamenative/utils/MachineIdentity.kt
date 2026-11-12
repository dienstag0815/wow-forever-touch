package app.gamenative.utils

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.Locale
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * Stable, own Windows machine identity of one WoW Forever installation.
 *
 * The prebuilt Wine prefix ships one fixed MachineGuid / product id / install date for every
 * installation, and Wine derives the computer name from the Android hostname (which the DNS
 * shim turns into the current IP). Blizzard therefore saw either "the same PC for everybody" or
 * "a new PC in every network". This identity is generated once, kept in the app storage and
 * applied to the prefix at every start (see [MachineIdentityApply]).
 *
 * Pure logic: no Android classes, so it runs in plain JUnit. The values are never logged:
 * [toString] hides them.
 */
class MachineIdentity(
    val machineGuid: String,
    val computerName: String,
    val volumeSerial: String,
    val productId: String,
    val installDate: Long,
) {
    override fun toString(): String = "MachineIdentity(***)"

    override fun equals(other: Any?): Boolean = other is MachineIdentity &&
        machineGuid == other.machineGuid &&
        computerName == other.computerName &&
        volumeSerial == other.volumeSerial &&
        productId == other.productId &&
        installDate == other.installDate

    override fun hashCode(): Int = machineGuid.hashCode()

    fun isValid(): Boolean = GUID.matches(machineGuid) &&
        COMPUTER_NAME.matches(computerName) &&
        SERIAL.matches(volumeSerial) &&
        volumeSerial != "00000000" &&
        PRODUCT_ID.matches(productId) &&
        installDate in MIN_INSTALL_DATE..MAX_INSTALL_DATE

    fun serialize(): String = buildString {
        append(KEY_GUID).append('=').append(machineGuid).append('\n')
        append(KEY_NAME).append('=').append(computerName).append('\n')
        append(KEY_SERIAL).append('=').append(volumeSerial).append('\n')
        append(KEY_PRODUCT).append('=').append(productId).append('\n')
        append(KEY_INSTALL).append('=').append(installDate).append('\n')
    }

    /** Result of [inspect]: a usable identity, no file at all, or a file that cannot be used ([reason] names it, never a value). */
    sealed class Inspection {
        class Valid(val identity: MachineIdentity) : Inspection()
        object Missing : Inspection()
        class Invalid(val reason: String) : Inspection()
    }

    companion object {
        /** Id of the one container this identity belongs to. */
        const val WOW_CONTAINER_ID = "wow_forever"

        /** Environment variable read by the DNS shim (gethostname / self resolution). */
        const val ENV_HOSTNAME = "WOWFOREVER_HOSTNAME"

        const val FILE_NAME = "wow_identity.properties"

        private const val KEY_GUID = "machineGuid"
        private const val KEY_NAME = "computerName"
        private const val KEY_SERIAL = "volumeSerial"
        private const val KEY_PRODUCT = "productId"
        private const val KEY_INSTALL = "installDate"

        private const val NAME_PREFIX = "DESKTOP-"
        private const val NAME_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        private const val NAME_SUFFIX_LENGTH = 7

        // 2010-01-01 .. 2038-01-19 (the registry stores InstallDate as a signed 32 bit dword)
        private const val MIN_INSTALL_DATE = 1_262_304_000L
        private const val MAX_INSTALL_DATE = 2_147_483_647L

        private val GUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        private val COMPUTER_NAME = Regex("^DESKTOP-[A-Z0-9]{7}$")
        private val SERIAL = Regex("^[0-9A-F]{8}$")
        private val PRODUCT_ID = Regex("^00330-8[0-9]{4}-[0-9]{5}-AAOEM$")

        private val lock = Any()

        private fun secureRandom(): Random = SecureRandom().asKotlinRandom()

        /** A random UUID v4 in lower case with dashes. */
        fun randomGuid(random: Random): String {
            val bytes = random.nextBytes(16)
            bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte()
            bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
            val hex = bytes.joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
            return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-" +
                hex.substring(16, 20) + "-" + hex.substring(20, 32)
        }

        fun generate(random: Random = secureRandom(), nowSeconds: Long = System.currentTimeMillis() / 1000): MachineIdentity {
            val name = NAME_PREFIX + String(CharArray(NAME_SUFFIX_LENGTH) { NAME_ALPHABET[random.nextInt(NAME_ALPHABET.length)] })
            var serial: Int
            do {
                serial = random.nextInt()
            } while (serial == 0)
            // Locale.ROOT: with the default locale %d writes Arabic-Indic / Persian / Devanagari digits on some phones, the
            // stored product id would then fail isValid() and a new identity would be made at every start.
            val product = "00330-8%04d-%05d-AAOEM".format(Locale.ROOT, random.nextInt(10_000), random.nextInt(100_000))
            return MachineIdentity(
                machineGuid = randomGuid(random),
                computerName = name,
                volumeSerial = "%08X".format(Locale.ROOT, serial),
                productId = product,
                installDate = nowSeconds.coerceIn(MIN_INSTALL_DATE, MAX_INSTALL_DATE),
            )
        }

        /** `key=value` lines of the stored text (whitespace around key and value is ignored, other lines and unknown keys are skipped). */
        internal fun readValues(text: String): Map<String, String> {
            val values = HashMap<String, String>()
            for (line in text.lines()) {
                val eq = line.indexOf('=')
                if (eq <= 0) continue
                values[line.substring(0, eq).trim()] = line.substring(eq + 1).trim()
            }
            return values
        }

        /** Parses the stored text; null when a field is missing or the result is not valid. */
        fun parse(text: String?): MachineIdentity? {
            if (text == null) return null
            val values = readValues(text)
            val install = values[KEY_INSTALL]?.toLongOrNull() ?: return null
            val identity = MachineIdentity(
                machineGuid = values[KEY_GUID] ?: return null,
                computerName = values[KEY_NAME] ?: return null,
                volumeSerial = values[KEY_SERIAL] ?: return null,
                productId = values[KEY_PRODUCT] ?: return null,
                installDate = install,
            )
            return if (identity.isValid()) identity else null
        }

        /** Like [load], but tells apart "no file" from "file unusable" and names the reason (for the identity log). */
        fun inspect(file: File): Inspection {
            if (!file.isFile) return Inspection.Missing
            val text = try {
                String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
            } catch (e: IOException) {
                return Inspection.Invalid("lesefehler")
            }
            val values = readValues(text)
            for (key in listOf(KEY_GUID, KEY_NAME, KEY_SERIAL, KEY_PRODUCT)) {
                if (values[key] == null) return Inspection.Invalid("feld_fehlt:$key")
            }
            if (values[KEY_INSTALL]?.toLongOrNull() == null) return Inspection.Invalid("feld_fehlt:$KEY_INSTALL")
            val identity = parse(text) ?: return Inspection.Invalid("feld_ungueltig:" + invalidField(values))
            return Inspection.Valid(identity)
        }

        private fun invalidField(values: Map<String, String>): String {
            val guid = values[KEY_GUID].orEmpty()
            val serial = values[KEY_SERIAL].orEmpty()
            val install = values[KEY_INSTALL]?.toLongOrNull() ?: 0L
            return when {
                !GUID.matches(guid) -> KEY_GUID
                !COMPUTER_NAME.matches(values[KEY_NAME].orEmpty()) -> KEY_NAME
                !SERIAL.matches(serial) || serial == "00000000" -> KEY_SERIAL
                !PRODUCT_ID.matches(values[KEY_PRODUCT].orEmpty()) -> KEY_PRODUCT
                install !in MIN_INSTALL_DATE..MAX_INSTALL_DATE -> KEY_INSTALL
                else -> "unbekannt"
            }
        }

        /** The text of an identity file; null when there is no file or it cannot be read. */
        fun readText(file: File): String? {
            if (!file.isFile) return null
            return try {
                String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
            } catch (e: IOException) {
                null
            }
        }

        /** Names of the fields of a stored identity (as in the file and in the identity log). */
        val FIELD_NAMES: List<String> = listOf(KEY_GUID, KEY_NAME, KEY_SERIAL, KEY_PRODUCT, KEY_INSTALL)

        /** Result of [repair]: the identity with its valid fields kept, [fields] names the ones that were made new (never a value). */
        class Repair(val identity: MachineIdentity, val fields: List<String>)

        /**
         * A file that no longer passes [isValid] as a whole (a field is missing, damaged, or a later version checks more strictly) is
         * not thrown away: every field that is still valid stays, only the others are made new. Null when nothing in [text] is usable
         * (then a complete new identity is the only way) or when nothing needs repair.
         */
        fun repair(text: String?, random: Random = secureRandom(), nowSeconds: Long = System.currentTimeMillis() / 1000): Repair? {
            if (text == null) return null
            val values = readValues(text)
            val fresh = generate(random, nowSeconds)
            val kept = LinkedHashMap<String, String>()
            val renewed = ArrayList<String>()
            fun pick(key: String, ok: (String) -> Boolean, new: String) {
                val old = values[key]
                if (old != null && ok(old)) {
                    kept[key] = old
                } else {
                    kept[key] = new
                    renewed += key
                }
            }
            pick(KEY_GUID, { GUID.matches(it) }, fresh.machineGuid)
            pick(KEY_NAME, { COMPUTER_NAME.matches(it) }, fresh.computerName)
            pick(KEY_SERIAL, { SERIAL.matches(it) && it != "00000000" }, fresh.volumeSerial)
            pick(KEY_PRODUCT, { PRODUCT_ID.matches(it) }, fresh.productId)
            pick(KEY_INSTALL, { it.toLongOrNull()?.let { v -> v in MIN_INSTALL_DATE..MAX_INSTALL_DATE } == true }, fresh.installDate.toString())
            if (renewed.isEmpty() || renewed.size == FIELD_NAMES.size) return null
            val identity = MachineIdentity(
                machineGuid = kept.getValue(KEY_GUID),
                computerName = kept.getValue(KEY_NAME),
                volumeSerial = kept.getValue(KEY_SERIAL),
                productId = kept.getValue(KEY_PRODUCT),
                installDate = kept.getValue(KEY_INSTALL).toLong(),
            )
            return Repair(identity, renewed)
        }

        /** Reads the identity file; null when missing, unreadable or invalid. */
        fun load(file: File): MachineIdentity? {
            if (!file.isFile) return null
            return try {
                parse(String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8))
            } catch (e: IOException) {
                null
            }
        }

        /**
         * Writes via temp file + sync + rename so a crash or a stop of the app (an update, a kill) never leaves a half written file:
         * the old file stays until the new one is complete on disk. [afterTempWritten] runs between the two steps (tests simulate the
         * abort there). A leftover temp file is overwritten by the next write and never read.
         */
        internal fun writeAtomic(file: File, bytes: ByteArray, afterTempWritten: () -> Unit = {}) {
            val dir = file.absoluteFile.parentFile ?: throw IOException("no parent folder")
            Files.createDirectories(dir.toPath())
            val temp = File(dir, file.name + ".tmp")
            try {
                FileOutputStream(temp).use { out ->
                    out.write(bytes)
                    out.flush()
                    out.fd.sync()
                }
                afterTempWritten()
                try {
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (e: AtomicMoveNotSupportedException) {
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (e: IOException) {
                temp.delete()
                throw e
            } catch (e: RuntimeException) {
                temp.delete()
                throw e
            }
            syncFolder(dir)
        }

        /** Best effort: makes the rename itself durable. Not every file system lets a folder be opened; that is no error. */
        private fun syncFolder(dir: File) {
            try {
                java.nio.channels.FileChannel.open(dir.toPath(), java.nio.file.StandardOpenOption.READ).use { it.force(true) }
            } catch (e: Exception) {
                // Not supported here.
            }
        }

        fun save(file: File, identity: MachineIdentity): Boolean = try {
            writeAtomic(file, identity.serialize().toByteArray(StandardCharsets.UTF_8))
            true
        } catch (e: IOException) {
            false
        }

        /**
         * Valid file stays untouched. Missing or invalid: generate a new identity and store it.
         * If storing fails the generated identity is still returned for this run.
         *
         * Not used by the app anymore: [MachineIdentityStore] does the same, but writes the identity log, notices a failed
         * save and tries to store the same identity again at the next start (a new one at every start went unnoticed here).
         */
        fun loadOrCreate(
            file: File,
            random: Random = secureRandom(),
            nowSeconds: Long = System.currentTimeMillis() / 1000,
        ): MachineIdentity = synchronized(lock) {
            load(file) ?: generate(random, nowSeconds).also { save(file, it) }
        }

        /** True for a name the shim may hand out as hostname (<= 63 chars, letters, digits, '-'). */
        fun isValidHostname(name: String?): Boolean =
            name != null && name.isNotEmpty() && name.length <= 63 &&
                name.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' }
    }
}
