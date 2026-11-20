package app.jisr.vpn.core

import android.util.Base64
import org.json.JSONObject
import java.net.URLEncoder

/** يبني رابط سيرفر (vmess/vless/trojan) من حقول الإدخال اليدوي، بنفس صيغة LinkParser. */
object LinkBuilder {

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** SSH عادي (TCP مباشر) أو عبر WebSocket بترويسة Host ومسار عاديين. لا يوجد أي حقل Payload. */
    fun ssh(
        name: String, host: String, port: Int, user: String, pass: String,
        net: String, sec: String, sni: String, path: String, hostHeader: String
    ): String {
        val label = name.ifBlank { "$host:$port" }
        val q = mutableListOf("type=$net")
        if (net == "ws") {
            q += "security=$sec"
            if (sni.isNotBlank()) q += "sni=${enc(sni)}"
            if (path.isNotBlank()) q += "path=${enc(path)}"
            if (hostHeader.isNotBlank()) q += "host=${enc(hostHeader)}"
        }
        return "ssh://${enc(user)}:${enc(pass)}@$host:$port?${q.joinToString("&")}#${enc(label)}"
    }

    fun shadowsocks(name: String, host: String, port: Int, method: String, password: String): String {
        val label = name.ifBlank { "$host:$port" }
        val b64 = Base64.encodeToString(
            "$method:$password".toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE
        )
        return "ss://$b64@$host:$port#${enc(label)}"
    }

    fun build(
        proto: String,
        name: String,
        host: String,
        port: Int,
        id: String,
        net: String,
        sec: String,
        sni: String,
        path: String,
        hostHeader: String,
        flow: String,
        pbk: String,
        sid: String
    ): String {
        val label = name.ifBlank { "$host:$port" }
        if (proto == "vmess") {
            val j = JSONObject()
                .put("v", "2")
                .put("ps", label)
                .put("add", host)
                .put("port", port.toString())
                .put("id", id)
                .put("aid", "0")
                .put("scy", "auto")
                .put("net", net)
                .put("type", "none")
                .put("host", hostHeader)
                .put("path", path)
                .put("tls", if (sec == "tls") "tls" else "")
                .put("sni", sni)
            val b64 = Base64.encodeToString(j.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            return "vmess://$b64"
        }

        val q = mutableListOf<String>()
        q += "type=$net"
        q += "security=$sec"
        if (sni.isNotBlank()) q += "sni=${enc(sni)}"
        if (path.isNotBlank()) q += if (net == "grpc") "serviceName=${enc(path)}" else "path=${enc(path)}"
        if (hostHeader.isNotBlank()) q += "host=${enc(hostHeader)}"
        if (proto == "vless" && flow.isNotBlank()) q += "flow=${enc(flow)}"
        if (sec == "reality") {
            if (pbk.isNotBlank()) q += "pbk=${enc(pbk)}"
            if (sid.isNotBlank()) q += "sid=${enc(sid)}"
        }
        return "$proto://${enc(id)}@$host:$port?${q.joinToString("&")}#${enc(label)}"
    }
}
