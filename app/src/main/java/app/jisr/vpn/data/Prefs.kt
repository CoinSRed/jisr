package app.jisr.vpn.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Server(val id: String, val name: String, val link: String)

/** تخزين محلي بسيط. كل شيء يبقى على الجهاز فقط. */
object Prefs {
    private var sp: SharedPreferences? = null

    fun init(ctx: Context) {
        if (sp == null) sp = ctx.applicationContext.getSharedPreferences("jisr", Context.MODE_PRIVATE)
    }

    private fun p(): SharedPreferences = sp ?: error("Prefs.init لم يُستدعَ")

    var servers: List<Server>
        get() {
            val arr = JSONArray(p().getString("servers", "[]"))
            return (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Server(o.getString("id"), o.getString("name"), o.getString("link"))
            }
        }
        set(value) {
            val arr = JSONArray()
            value.forEach {
                arr.put(JSONObject().put("id", it.id).put("name", it.name).put("link", it.link))
            }
            p().edit().putString("servers", arr.toString()).apply()
        }

    var selectedId: String?
        get() = p().getString("selected", null)
        set(value) {
            p().edit().putString("selected", value).apply()
        }

    var autoConnect: Boolean
        get() = p().getBoolean("autoConnect", false)
        set(value) {
            p().edit().putBoolean("autoConnect", value).apply()
        }

    var askedPhonePerm: Boolean
        get() = p().getBoolean("askedPhonePerm", false)
        set(value) {
            p().edit().putBoolean("askedPhonePerm", value).apply()
        }

    /** التبديل التلقائي لسيرفر ثاني عند فشل الاتصال. */
    var smartSwitch: Boolean
        get() = p().getBoolean("smartSwitch", true)
        set(value) {
            p().edit().putBoolean("smartSwitch", value).apply()
        }

    var dns: String
        get() = p().getString("dns", "1.1.1.1") ?: "1.1.1.1"
        set(value) {
            p().edit().putString("dns", value).apply()
        }
}
