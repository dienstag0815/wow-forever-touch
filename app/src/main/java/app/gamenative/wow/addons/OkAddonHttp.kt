package app.gamenative.wow.addons

import android.content.Context
import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import app.gamenative.utils.Net
import java.util.concurrent.TimeUnit
import okhttp3.Protocol
import okhttp3.Request

/**
 * Der echte Netzzugang des Addon-Managers: OkHttp ohne automatische Weiterleitungen (jede wird in [AddonFetch] gegen die
 * Host-Liste geprüft). Kein Anmelde-Token: GitHub-API ohne Schlüssel, Antworten werden 1 Stunde zwischengespeichert.
 */
object OkAddonHttp : AddonHttp {
    private val client by lazy {
        Net.http.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
    }

    override fun get(url: String, headers: Map<String, String>): AddonResponse {
        val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        val response = client.newCall(request).execute()
        val map = HashMap<String, String>()
        for (name in response.headers.names()) response.header(name)?.let { map[name] = it }
        return AddonResponse(response.code, map, response.body.byteStream(), response::close)
    }
}

/** Liest den mitgelieferten Katalog aus den App-Dateien (assets/wow/addons/katalog.json). */
object AddonCatalogStore {
    const val ASSET = "wow/addons/katalog.json"

    fun load(context: Context): CatalogResult = try {
        AddonCatalogParser.parse(context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() })
    } catch (e: java.io.IOException) {
        CatalogResult.Invalid(UiText.res(R.string.launcher_addon_catalog_unreadable))
    }
}
