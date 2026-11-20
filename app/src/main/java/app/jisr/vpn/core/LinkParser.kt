package app.jisr.vpn.core

import android.util.Base64
import org.json.JSONObject
import java.net.URLDecoder

data class Node(
    val protocol: String,
    val name: String,
    val host: String,
    val port: Int,
    val id: String,
    val network: String = "tcp",
    val security: String = "none",
    val sni: String = "",
    val path: String = "",
    val hostHeader: String = "",
    val flow: String = "",
    val fp: String = "",
    val pbk: String = "",
    val sid: String = "",
    val alterId: Int = 0,
    val cipher: String = "auto",
    val serviceName: String = "",
    val user: String = ""
)

/** يقرأ روابط vmess:// و vless:// و trojan:// ويحوّلها إلى Node. */
object LinkParser {

    fun parse(link: String): Node? = try {
        when {
            link.startsWith("vmess://") -> parseVmess(link)
            link.startsWith("vless://") -> parseUri(link, "vless")
            link.startsWith("trojan://") -> parseUri(link, "trojan")
            link.startsWith("ss://") -> parseSs(link)
            link.startsWith("ssh://") -> parseSsh(link)
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    private fun decodeB64(input: String): String {
        val n = input.replace('-', '+').replace('_', '/')
        val padded = n + "=".repeat((4 - n.length % 4) % 4)
        return String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
    }

    private fun parseVmess(link: String): Node? {
        val json = JSONObject(decodeB64(link.removePrefix("vmess://").trim()))
        val host = json.optString("add")
        val port = json.optString("port").toIntOrNull() ?: return null
        if (host.isBlank()) return null
        val net = json.optString("net").ifBlank { "tcp" }
        return Node(
            protocol = "vmess",
            name = json.optString("ps").ifBlank { "$host:$port" },
            host = host,
            port = port,
            id = json.optString("id"),
            network = net,
            security = if (json.optString("tls") == "tls") "tls" else "none",
            sni = json.optString("sni").ifBlank { json.optString("host") },
            path = json.optString("path"),
            hostHeader = json.optString("host"),
            fp = json.optString("fp"),
            alterId = json.optString("aid").toIntOrNull() ?: 0,
            cipher = json.optString("scy").ifBlank { "auto" },
            serviceName = json.optString("path")
        )
    }

    /** Shadowsocks: صيغة SIP002 (ss://base64(method:pass)@host:port) والصيغة القديمة. روابط الإضافات (plugin) غير مدعومة. */
    private fun parseSs(link: String): Node? {
        var rest = link.removePrefix("ss://")
        val name = if (rest.contains("#")) URLDecoder.decode(rest.substringAfter("#"), "UTF-8") else ""
        rest = rest.substringBefore("#")
        if (rest.contains("?") && rest.substringAfter("?").contains("plugin")) return null
        rest = rest.substringBefore("?").trimEnd('/')

        val method: String
        val password: String
        val host: String
        val port: Int
        if (rest.contains("@")) {
            var userInfo = rest.substringBeforeLast("@")
            val hp = rest.substringAfterLast("@")
            if (userInfo.contains("%")) userInfo = URLDecoder.decode(userInfo.replace("+", "%2B"), "UTF-8")
            val decoded = if (userInfo.contains(":")) userInfo else decodeB64(userInfo)
            method = decoded.substringBefore(":")
            password = decoded.substringAfter(":", "")
            host = hp.substringBeforeLast(":")
            port = hp.substringAfterLast(":", "").toIntOrNull() ?: return null
        } else {
            val decoded = decodeB64(rest)
            val at = decoded.lastIndexOf('@')
            if (at < 0) return null
            val up = decoded.substring(0, at)
            val hp = decoded.substring(at + 1)
            method = up.substringBefore(":")
            password = up.substringAfter(":", "")
            host = hp.substringBeforeLast(":")
            port = hp.substringAfterLast(":", "").toIntOrNull() ?: return null
        }
        if (host.isBlank() || method.isBlank() || password.isBlank()) return null
        return Node(
            protocol = "shadowsocks",
            name = name.ifBlank { "$host:$port" },
            host = host,
            port = port,
            id = password,
            cipher = method
        )
    }

    /** SSH: ssh://user:pass@host:port?type=tcp|ws&security=none|tls&sni=..&path=..&host=..#name */
    private fun parseSsh(link: String): Node? {
        var rest = link.substringAfter("://")
        val name = if (rest.contains("#")) URLDecoder.decode(rest.substringAfterLast("#"), "UTF-8") else ""
        rest = rest.substringBefore("#")
        val query = if (rest.contains("?")) rest.substringAfter("?") else ""
        val authority = rest.substringBefore("?").trimEnd('/')
        if (!authority.contains("@")) return null

        val ui = authority.substringBeforeLast("@")
        val hp = authority.substringAfterLast("@")
        val user = URLDecoder.decode(ui.substringBefore(":"), "UTF-8")
        val pass = URLDecoder.decode(ui.substringAfter(":", ""), "UTF-8")
        val host = hp.substringBeforeLast(":")
        val port = hp.substringAfterLast(":", "").toIntOrNull() ?: 22
        if (host.isBlank() || user.isBlank()) return null

        val q = query.split("&")
            .filter { it.contains("=") }
            .associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), "UTF-8") }

        return Node(
            protocol = "ssh",
            name = name.ifBlank { "$host:$port" },
            host = host,
            port = port,
            id = pass,
            user = user,
            network = if (q["type"] == "ws") "ws" else "tcp",
            security = if (q["security"] == "tls") "tls" else "none",
            sni = q["sni"] ?: "",
            path = q["path"] ?: "",
            hostHeader = q["host"] ?: ""
        )
    }

    private fun parseUri(link: String, proto: String): Node? {
        var rest = link.substringAfter("://")
        val name = if (rest.contains("#")) URLDecoder.decode(rest.substringAfterLast("#"), "UTF-8") else ""
        rest = rest.substringBefore("#")
        val query = if (rest.contains("?")) rest.substringAfter("?") else ""
        val authority = rest.substringBefore("?").trimEnd('/')
        if (!authority.contains("@")) return null

        val userInfo = URLDecoder.decode(authority.substringBeforeLast("@"), "UTF-8")
        val hostPort = authority.substringAfterLast("@")

        val host: String
        val port: Int
        if (hostPort.startsWith("[")) {
            host = hostPort.substringAfter("[").substringBefore("]")
            port = hostPort.substringAfter("]:", "").toIntOrNull() ?: 443
        } else {
            host = hostPort.substringBeforeLast(":")
            port = hostPort.substringAfterLast(":", "").toIntOrNull() ?: 443
        }
        if (host.isBlank() || userInfo.isBlank()) return null

        val q = query.split("&")
            .filter { it.contains("=") }
            .associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), "UTF-8") }

        return Node(
            protocol = proto,
            name = name.ifBlank { "$host:$port" },
            host = host,
            port = port,
            id = userInfo,
            network = q["type"] ?: "tcp",
            security = q["security"] ?: if (proto == "trojan") "tls" else "none",
            sni = q["sni"] ?: q["peer"] ?: "",
            path = q["path"] ?: "",
            hostHeader = q["host"] ?: "",
            flow = q["flow"] ?: "",
            fp = q["fp"] ?: "",
            pbk = q["pbk"] ?: "",
            sid = q["sid"] ?: "",
            serviceName = q["serviceName"] ?: ""
        )
    }
}
