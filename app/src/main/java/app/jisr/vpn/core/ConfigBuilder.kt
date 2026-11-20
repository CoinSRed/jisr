package app.jisr.vpn.core

import org.json.JSONArray
import org.json.JSONObject

/** يبني ملف إعدادات Xray (JSON) من سيرفر واحد. */
object ConfigBuilder {

    fun build(n: Node, dns: String, logPath: String? = null): String {
        val root = JSONObject()
        val log = JSONObject().put("loglevel", "warning")
        if (logPath != null) log.put("error", logPath) // أخطاء المحرك تُكتب في ملف نقرأه لاحقاً
        root.put("log", log)
        root.put(
            "dns",
            JSONObject().put("servers", JSONArray().put(dns).put("1.1.1.1"))
        )
        root.put(
            "inbounds",
            JSONArray().put(
                JSONObject()
                    .put("tag", "tun-in")
                    .put("port", 0)
                    .put("protocol", "tun")
                    .put(
                        "settings",
                        JSONObject().put("name", "xray0").put("MTU", JSONArray().put(1500).put(1500))
                    )
                    .put(
                        "sniffing",
                        JSONObject()
                            .put("enabled", true)
                            .put("destOverride", JSONArray().put("http").put("tls"))
                    )
            )
        )
        root.put(
            "outbounds",
            JSONArray()
                .put(proxy(n))
                .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
                .put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
        )
        return root.toString()
    }

    private fun proxy(n: Node): JSONObject {
        val out = JSONObject().put("tag", "proxy").put("protocol", n.protocol)
        val settings = when (n.protocol) {
            "vless" -> {
                val user = JSONObject().put("id", n.id).put("encryption", "none")
                if (n.flow.isNotBlank()) user.put("flow", n.flow)
                JSONObject().put(
                    "vnext",
                    JSONArray().put(
                        JSONObject().put("address", n.host).put("port", n.port)
                            .put("users", JSONArray().put(user))
                    )
                )
            }
            "vmess" -> {
                val user = JSONObject().put("id", n.id).put("alterId", n.alterId).put("security", n.cipher)
                JSONObject().put(
                    "vnext",
                    JSONArray().put(
                        JSONObject().put("address", n.host).put("port", n.port)
                            .put("users", JSONArray().put(user))
                    )
                )
            }
            else -> JSONObject().put(
                "servers",
                JSONArray().put(
                    JSONObject().put("address", n.host).put("port", n.port).put("password", n.id)
                )
            )
        }
        out.put("settings", settings)
        out.put("streamSettings", stream(n))
        return out
    }

    private fun stream(n: Node): JSONObject {
        val net = if (n.network == "ws" || n.network == "grpc") n.network else "tcp"
        val s = JSONObject().put("network", net).put("security", n.security)

        if (n.security == "tls") {
            s.put(
                "tlsSettings",
                JSONObject()
                    .put("serverName", n.sni.ifBlank { n.host })
                    .put("fingerprint", n.fp.ifBlank { "chrome" })
            )
        } else if (n.security == "reality") {
            s.put(
                "realitySettings",
                JSONObject()
                    .put("serverName", n.sni)
                    .put("fingerprint", n.fp.ifBlank { "chrome" })
                    .put("publicKey", n.pbk)
                    .put("shortId", n.sid)
            )
        }

        if (net == "ws") {
            s.put(
                "wsSettings",
                JSONObject()
                    .put("path", n.path.ifBlank { "/" })
                    .put(
                        "headers",
                        JSONObject().put("Host", n.hostHeader.ifBlank { n.sni.ifBlank { n.host } })
                    )
            )
        } else if (net == "grpc") {
            s.put("grpcSettings", JSONObject().put("serviceName", n.serviceName.ifBlank { n.path }))
        }
        return s
    }
}
