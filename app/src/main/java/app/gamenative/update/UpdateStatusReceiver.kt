package app.gamenative.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives the status of the PackageInstaller session started by [UpdateManager] (not exported). */
class UpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UpdateManager.ACTION_INSTALL_STATUS) return
        UpdateManager.onInstallStatus(context, intent)
    }
}
