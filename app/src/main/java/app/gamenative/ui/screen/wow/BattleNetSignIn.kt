package app.gamenative.ui.screen.wow

import android.content.Context
import android.util.Base64
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.Crypto
import app.gamenative.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

object BattleNetSignIn {
    data class Login(val email: String, val password: String)

    fun load(context: Context): Login? {
        val prefs = prefs(context)
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val sealed = prefs.getString(KEY_PASSWORD, null) ?: return null
        return try {
            Login(email, String(Crypto.decrypt(Base64.decode(sealed, Base64.NO_WRAP))))
        } catch (e: Exception) {
            Timber.w(e, "Saved Battle.net login unreadable, clearing it")
            forget(context)
            null
        }
    }

    /** Ob eine Anmeldung gespeichert ist, ohne das Passwort zu entschlüsseln. */
    fun hasSaved(context: Context): Boolean {
        val prefs = prefs(context)
        return prefs.getString(KEY_EMAIL, null) != null && prefs.getString(KEY_PASSWORD, null) != null
    }

    /**
     * E-Mail und Passwort als Zeichenfelder für das Eintippen im Spiel. Das Passwort wird nie zu einem String: Aus den
     * entschlüsselten Bytes entsteht direkt ein CharArray, die Zwischenpuffer werden überschrieben; [Secret.wipe] überschreibt
     * die Zeichen, sobald sie getippt sind. Null, wenn nichts gespeichert oder nicht lesbar ist.
     */
    fun loadSecret(context: Context): Secret? {
        val prefs = prefs(context)
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val sealed = prefs.getString(KEY_PASSWORD, null) ?: return null
        var bytes: ByteArray? = null
        return try {
            bytes = Crypto.decrypt(Base64.decode(sealed, Base64.NO_WRAP))
            val buffer = Charsets.UTF_8.decode(java.nio.ByteBuffer.wrap(bytes))
            val password = CharArray(buffer.remaining())
            buffer.get(password)
            if (buffer.hasArray()) java.util.Arrays.fill(buffer.array(), '\u0000')
            Secret(email.toCharArray(), password)
        } catch (e: Exception) {
            Timber.w("Saved Battle.net login unreadable")
            null
        } finally {
            bytes?.fill(0)
        }
    }

    /** Anmeldedaten als Zeichenfelder; [wipe] überschreibt beide. */
    class Secret(val email: CharArray, val password: CharArray) {
        fun wipe() {
            email.fill('\u0000')
            password.fill('\u0000')
        }
    }

    fun save(context: Context, login: Login) {
        val sealed = Base64.encodeToString(Crypto.encrypt(login.password.toByteArray()), Base64.NO_WRAP)
        prefs(context).edit().putString(KEY_EMAIL, login.email).putString(KEY_PASSWORD, sealed).apply()
    }

    fun forget(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun writeLoginFile(gameRoot: File, login: Login) {
        try {
            val file = File(File(gameRoot, WowClientDownloader.FLAVOR_DIR), "login.txt")
            file.parentFile?.mkdirs()
            file.writeText("${login.email}\n${login.password}\n")
        } catch (e: Exception) {
            Timber.e(e, "Failed to write login.txt")
        }
    }

    // Also clears a login.txt left in the beta folder, which the full release no longer reads.
    fun removeLoginFile(gameRoot: File) {
        listOf(WowClientDownloader.FLAVOR_DIR, WowClientDownloader.LEGACY_FLAVOR_DIR).forEach { flavorDir ->
            try {
                val file = File(File(gameRoot, flavorDir), "login.txt")
                if (file.exists()) {
                    file.writeBytes(ByteArray(file.length().coerceAtMost(4096).toInt()))
                    file.delete()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove login.txt")
            }
        }
    }

    /** True once Hotfix.log is large enough to show the client has passed the login screen. */
    internal fun loginConsumed(hotfixLogBytes: Long?): Boolean =
        hotfixLogBytes != null && hotfixLogBytes > HOTFIX_LOG_CONSUMED_BYTES

    fun startLoginWatcher(gameRoot: File) {
        CoroutineScope(Dispatchers.IO).launch {
            val flavorDir = File(gameRoot, WowClientDownloader.FLAVOR_DIR)
            val hotfixLog = File(flavorDir, HOTFIX_LOG)
            hotfixLog.delete()
            // A fresh install's first boot streams assets and plays the intro cinematic before reaching the login screen.
            val deadline = System.currentTimeMillis() + LOGIN_FILE_TIMEOUT_MS
            try {
                while (isActive && System.currentTimeMillis() < deadline) {
                    delay(1000L)
                    val bytes = if (hotfixLog.exists()) hotfixLog.length() else null
                    if (loginConsumed(bytes)) break
                }
            } finally {
                removeLoginFile(gameRoot)
            }
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "battle_net_login"
    private const val KEY_EMAIL = "email"
    private const val KEY_PASSWORD = "password"
    private const val HOTFIX_LOG = "Logs/Hotfix.log"
    private const val HOTFIX_LOG_CONSUMED_BYTES = 50_000L
    private const val LOGIN_FILE_TIMEOUT_MS = 600_000L
}

@Composable
fun BattleNetCredentialDialog(
    /** "Überspringen" (auch Tippen daneben/Zurück): nichts wird gespeichert, der Start läuft ohne gespeicherte Anmeldung weiter. */
    onDismiss: () -> Unit,
    onConfirm: (BattleNetSignIn.Login) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val emailFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(DIALOG_SETTLE_MS)
        emailFocus.requestFocus()
        keyboard?.show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.login_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.login_dialog_hint), fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.login_dialog_email)) },
                    singleLine = true,
                    modifier = Modifier.focusRequester(emailFocus),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.login_dialog_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = email.isNotBlank() && password.isNotEmpty(),
                onClick = { onConfirm(BattleNetSignIn.Login(email.trim(), password)) },
            ) { Text(stringResource(R.string.login_dialog_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.login_dialog_skip)) } },
    )
}

private const val DIALOG_SETTLE_MS = 300L
