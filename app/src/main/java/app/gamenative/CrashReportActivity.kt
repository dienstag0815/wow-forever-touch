package app.gamenative

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

/**
 * Zeigt nach einem Absturz den Bericht (Kopf und Stacktrace) mit "Copy", "Share" und "Close". Läuft im eigenen Prozess
 * (`:crash`, siehe Manifest) und braucht deshalb weder Hilt, Compose, DataStore noch irgendeinen Zustand des Hauptprozesses:
 * nur Views aus dem Code, Texte aus den Ressourcen, der Bericht kommt als Intent-Extra ([CrashReport.EXTRA_REPORT]). Stürzt diese
 * Anzeige selbst ab, startet nichts mehr: der Handler ([CrashHandler]) ist in diesem Prozess nicht eingebaut.
 */
class CrashReportActivity : Activity() {

    private var report: String = ""

    /** Context mit der Sprache des Hauptprozesses (en/de); sie kommt als Extra, Einstellungen werden hier nicht gelesen. */
    private lateinit var texts: Context

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        texts = withLanguage(this, intent?.getStringExtra(CrashReport.EXTRA_LANGUAGE))
        report = intent?.getStringExtra(CrashReport.EXTRA_REPORT).orEmpty().ifEmpty { "(no report)" }
        setContentView(buildContent())
    }

    override fun onDestroy() {
        super.onDestroy()
        // Der leere Prozess :crash soll nicht liegen bleiben.
        if (isFinishing) android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left + dp(16), bars.top + dp(16), bars.right + dp(16), bars.bottom + dp(16))
            insets
        }

        root.addView(TextView(this).apply {
            text = texts.getString(R.string.crash_title)
            setTextColor(TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = texts.getString(R.string.crash_hint)
            setTextColor(TEXT_DIM)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(8), 0, dp(8))
        })

        val reportView = TextView(this).apply {
            text = report
            setTextColor(TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(PANEL)
        }
        root.addView(
            ScrollView(this).apply {
                isFillViewport = true
                addView(reportView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(12), 0, 0)
        }
        buttons.addView(button(R.string.crash_copy) { copyReport(it) })
        buttons.addView(button(R.string.crash_share) { shareReport() })
        buttons.addView(button(R.string.crash_close) { finishAndRemoveTask() })
        root.addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return root
    }

    private fun button(label: Int, onClick: (Button) -> Unit): Button = Button(this).apply {
        text = texts.getString(label)
        setOnClickListener { onClick(this) }
    }

    // Toast is poisoned in this project (utils/NoToast.kt) and there is no SnackbarManager in the ":crash" process: the button says it.
    private fun copyReport(button: Button) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(texts.getString(R.string.crash_share_subject), report))
        button.text = texts.getString(R.string.crash_copied)
    }

    private fun shareReport() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, texts.getString(R.string.crash_share_subject))
            putExtra(Intent.EXTRA_TEXT, report)
        }
        runCatching { startActivity(Intent.createChooser(send, texts.getString(R.string.crash_share_chooser))) }
    }

    companion object {
        private val BACKGROUND = Color.parseColor("#121212")
        private val PANEL = Color.parseColor("#1E1E1E")
        private val TEXT = Color.parseColor("#E6E6E6")
        private val TEXT_DIM = Color.parseColor("#B0B0B0")

        /** Context mit der Sprache [tag] ("de" oder sonst Englisch). */
        internal fun withLanguage(base: Context, tag: String?): Context {
            val locale = Locale(if (tag == "de") "de" else "en")
            val config = android.content.res.Configuration(base.resources.configuration)
            config.setLocale(locale)
            return base.createConfigurationContext(config)
        }
    }
}
