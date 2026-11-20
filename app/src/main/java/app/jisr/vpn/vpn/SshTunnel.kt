package app.jisr.vpn.vpn

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import kotlin.concurrent.thread

data class SshConfig(
    val host: String,
    val port: Int,
    val user: String,
    val password: String,
    val ws: Boolean,
    val tls: Boolean,
    val sni: String,
    val path: String,
    val hostHeader: String
) {
    fun toJson(): String = JSONObject()
        .put("host", host).put("port", port).put("user", user).put("password", password)
        .put("ws", ws).put("tls", tls).put("sni", sni).put("path", path).put("hostHeader", hostHeader)
        .toString()

    companion object {
        fun fromJson(s: String): SshConfig {
            val o = JSONObject(s)
            return SshConfig(
                o.getString("host"), o.getInt("port"), o.getString("user"), o.getString("password"),
                o.optBoolean("ws"), o.optBoolean("tls"), o.optString("sni"), o.optString("path"), o.optString("hostHeader")
            )
        }
    }
}

/**
 * نفق SSH: يتصل بالسيرفر (مباشرة أو عبر WebSocket) ويفتح خادم SOCKS5 محلي على 127.0.0.1،
 * وكل اتصال يصله يُمرَّر عبر قناة direct-tcpip داخل جلسة SSH. المحرك يوجّه حركة الجهاز لهذا المنفذ.
 */
class SshTunnel(private val cfg: SshConfig, private val filesDir: File) {

    private var session: Session? = null
    private var server: ServerSocket? = null
    @Volatile private var running = false

    fun start() {
        val jsch = JSch()
        jsch.hostKeyRepository = TofuRepo(File(filesDir, "hostkeys"))

        val s = jsch.getSession(cfg.user, cfg.host, cfg.port)
        s.setPassword(cfg.password)
        s.userInfo = PasswordInfo(cfg.password)
        s.setConfig("StrictHostKeyChecking", "ask") // أول اتصال يحفظ البصمة، وأي تغيّر لاحق يرفض الاتصال
        s.setConfig("PreferredAuthentications", "password,keyboard-interactive")
        s.setServerAliveInterval(15000)
        s.setServerAliveCountMax(3)
        if (cfg.ws) s.setSocketFactory(WsSocketFactory(cfg))
        s.connect(15000)
        session = s

        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(java.net.InetSocketAddress(InetAddress.getByName("127.0.0.1"), VpnProtocol.SSH_LOCAL_PORT), 64)
        server = ss
        running = true
        thread(isDaemon = true, name = "ssh-socks-accept") {
            while (running) {
                val c = try {
                    ss.accept()
                } catch (e: Exception) {
                    break
                }
                thread(isDaemon = true, name = "ssh-socks-conn") { handle(c) }
            }
        }
    }

    fun stop() {
        running = false
        try {
            server?.close()
        } catch (_: Exception) {
        }
        try {
            session?.disconnect()
        } catch (_: Exception) {
        }
        server = null
        session = null
    }

    /** SOCKS5 مبسّط: أمر CONNECT فقط، بدون مصادقة (الاستماع محلي فقط). */
    private fun handle(client: Socket) {
        try {
            val inp = client.getInputStream()
            val out = client.getOutputStream()

            if (inp.read() != 5) return
            val nMethods = inp.read()
            repeat(nMethods) { inp.read() }
            out.write(byteArrayOf(5, 0))
            out.flush()

            inp.read() // version
            val cmd = inp.read()
            inp.read() // reserved
            val atyp = inp.read()
            val host = when (atyp) {
                1 -> InetAddress.getByAddress(ByteArray(4) { inp.read().toByte() }).hostAddress ?: return
                3 -> {
                    val len = inp.read()
                    String(ByteArray(len) { inp.read().toByte() }, Charsets.US_ASCII)
                }
                4 -> InetAddress.getByAddress(ByteArray(16) { inp.read().toByte() }).hostAddress ?: return
                else -> return
            }
            val port = (inp.read() shl 8) or inp.read()

            if (cmd != 1) {
                out.write(byteArrayOf(5, 7, 0, 1, 0, 0, 0, 0, 0, 0))
                return
            }

            val s = session ?: return
            val ch = s.getStreamForwarder(host, port)
            val chIn = ch.inputStream
            val chOut = ch.outputStream
            try {
                ch.connect(10000)
            } catch (e: Exception) {
                out.write(byteArrayOf(5, 5, 0, 1, 0, 0, 0, 0, 0, 0))
                return
            }
            out.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
            out.flush()

            val up = thread(isDaemon = true, name = "ssh-pump-up") { pump(inp, chOut) }
            pump(chIn, out)
            try {
                ch.disconnect()
            } catch (_: Exception) {
            }
            up.interrupt()
        } catch (_: Exception) {
        } finally {
            try {
                client.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun pump(inp: InputStream, out: OutputStream) {
        val buf = ByteArray(16 * 1024)
        try {
            while (true) {
                val n = inp.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                out.flush()
            }
        } catch (_: Exception) {
        }
    }
}

private class PasswordInfo(private val password: String) : UserInfo, UIKeyboardInteractive {
    override fun getPassphrase(): String? = null
    override fun getPassword(): String = password
    override fun promptPassword(message: String?): Boolean = true
    override fun promptPassphrase(message: String?): Boolean = false
    override fun promptYesNo(message: String?): Boolean = true // يُستدعى فقط لأول بصمة (نقبلها ونحفظها)
    override fun showMessage(message: String?) {}
    override fun promptKeyboardInteractive(
        destination: String?, name: String?, instruction: String?, prompt: Array<out String>?, echo: BooleanArray?
    ): Array<String> = Array(prompt?.size ?: 0) { password }
}

/** يحفظ بصمة مفتاح السيرفر عند أول اتصال، ويرفض الاتصال لو تغيّرت لاحقاً (حماية من الاعتراض). */
private class TofuRepo(private val dir: File) : HostKeyRepository {
    private fun fp(key: ByteArray) = MessageDigest.getInstance("SHA-256").digest(key).joinToString("") { "%02x".format(it) }
    private fun file(host: String) = File(dir, host.replace(Regex("[^A-Za-z0-9._-]"), "_"))

    override fun check(host: String, key: ByteArray): Int {
        val f = file(host)
        if (!f.exists()) return HostKeyRepository.NOT_INCLUDED
        return if (f.readText().trim() == fp(key)) HostKeyRepository.OK else HostKeyRepository.CHANGED
    }

    override fun add(hostkey: HostKey, ui: UserInfo?) {
        dir.mkdirs()
        file(hostkey.host).writeText(fp(Base64.getDecoder().decode(hostkey.key)))
    }

    override fun remove(host: String?, type: String?) {}
    override fun remove(host: String?, type: String?, key: ByteArray?) {}
    override fun getKnownHostsRepositoryID(): String = "jisr-tofu"
    override fun getHostKey(): Array<HostKey> = emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
}
