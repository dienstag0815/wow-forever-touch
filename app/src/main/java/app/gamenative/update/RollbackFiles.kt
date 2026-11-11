package app.gamenative.update

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import java.io.File

/** The shared Downloads folder as the place that survives an uninstall: backup and old APK of a rollback, and the search for a backup after a fresh install. */
internal object RollbackFiles {
    fun downloadsDir(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    /** Lets file managers and the Downloads app see the new files at once; failures do not matter. */
    fun scan(context: Context, vararg files: File) {
        runCatching { MediaScannerConnection.scanFile(context.applicationContext, files.map { it.absolutePath }.toTypedArray(), null, null) }
    }

    /** The newest backup of this app in Downloads, or null (none, or the folder cannot be read without storage access). */
    fun newestBackup(): File? {
        val dir = downloadsDir()
        val name = RollbackLogic.newestBackup(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) ?: return null
        return File(dir, name)
    }
}
