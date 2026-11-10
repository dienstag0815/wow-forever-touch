package app.gamenative.cooldown

import app.gamenative.R
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.gamenative.wowicons.WowIcons
import timber.log.Timber

/**
 * "Symbole und Abklingzeiten prüfen": the check of the icon and cooldown display at the device. Shows in plain words whether
 * the addon TouchForever was found and delivered its symbol data, and whether the cooldown strip in the top left corner of
 * the game picture can be read right now (with the reason if not). The state is refreshed while the dialog is open.
 *
 * A native dialog on purpose: it is opened from the quick menu and must not depend on the screen's Compose state.
 */
object CooldownCheckDialog {
    private const val REFRESH_MS = 1000L

    fun show(context: Context) {
        CooldownService.init(context)
        val handler = Handler(Looper.getMainLooper())
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val summary = TextView(context).apply { textSize = 15f; setTypeface(typeface, Typeface.BOLD) }
        val details = TextView(context).apply { textSize = 13f }
        val hint = TextView(context).apply { textSize = 12f }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
            addView(summary)
            addView(details, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
            addView(hint, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        }
        val scroll = ScrollView(context).apply { addView(content) }

        var dialogOpen = true
        lateinit var refresh: Runnable
        refresh = Runnable {
            if (!dialogOpen) return@Runnable
            val report = CooldownCheck.build(
                iconsEnabled = WowIcons.isEnabled(context),
                bridge = WowIcons.bridgeData()?.let {
                    CooldownCheck.BridgeInfo(it.character, it.realm, it.slots.size, it.spells.size, it.totalEntries)
                },
                cooldownsEnabled = CooldownService.isEnabled,
                stripReadable = CooldownService.isStripReadable,
                stripReason = CooldownService.reason,
                coolingCount = CooldownService.coolingCount,
                lockedInCombat = CooldownService.valuesLockedInCombat,
            )
            summary.text = report.summary.resolve(context)
            details.text = report.lines.joinToString("\n") { it.resolve(context) }
            hint.text = report.hint.resolve(context)
            handler.postDelayed(refresh, REFRESH_MS)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.game_cc_title)
            .setView(scroll)
            .setPositiveButton(R.string.game_cc_close, null)
            .create()
        dialog.setOnDismissListener {
            dialogOpen = false
            handler.removeCallbacks(refresh)
        }
        try {
            dialog.show()
        } catch (e: RuntimeException) {
            Timber.w(e, "Symbole und Abklingzeiten prüfen: Dialog nicht anzeigbar")
            return
        }
        refresh.run()
    }
}
