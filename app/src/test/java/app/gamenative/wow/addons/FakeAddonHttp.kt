package app.gamenative.wow.addons

import java.io.ByteArrayInputStream
import java.io.InputStream

/** Attrappe für [AddonHttp]: eine Tabelle URL -> Antwort, jeder Aufruf wird mitgeschrieben. */
class FakeAddonHttp : AddonHttp {
    class Reply(val code: Int = 200, val body: ByteArray = ByteArray(0), val headers: Map<String, String> = emptyMap(), val declareLength: Boolean = true)

    val replies = LinkedHashMap<String, Reply>()
    val calls = ArrayList<String>()

    fun text(url: String, text: String, code: Int = 200, headers: Map<String, String> = emptyMap()) {
        replies[url] = Reply(code, text.toByteArray(), headers)
    }

    fun bytes(url: String, data: ByteArray, declareLength: Boolean = true) {
        replies[url] = Reply(200, data, declareLength = declareLength)
    }

    fun redirect(url: String, to: String, code: Int = 302) {
        replies[url] = Reply(code, headers = mapOf("Location" to to))
    }

    override fun get(url: String, headers: Map<String, String>): AddonResponse {
        calls += url
        val r = replies[url] ?: return AddonResponse(404, emptyMap(), null)
        val h = HashMap(r.headers)
        if (r.declareLength && r.code in 200..299) h["content-length"] = r.body.size.toString()
        val stream: InputStream? = if (r.code in 200..299) ByteArrayInputStream(r.body) else null
        return AddonResponse(r.code, h, stream)
    }
}
