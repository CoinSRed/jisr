package app.jisr.vpn.vpn

import com.jcraft.jsch.SocketFactory
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.Base64
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * يمرّر جلسة SSH داخل WebSocket عادي (RFC 6455): طلب ترقية قياسي بترويسات Host والمسار فقط،
 * ثم إطارات ثنائية. لا يوجد أي نص مخصّص يُحقن في الطلب.
 */
class WsSocketFactory(private val cfg: SshConfig) : SocketFactory {

    private var wsIn: InputStream? = null
    private var wsOut: OutputStream? = null

    override fun createSocket(host: String, port: Int): Socket {
        val base = Socket()
        base.connect(InetSocketAddress(host, port), 10000)

        val sock: Socket = if (cfg.tls) {
            val sniHost = cfg.sni.ifBlank { cfg.hostHeader.ifBlank { host } }
            val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(base, sniHost, port, true) as SSLSocket
            val params = ssl.sslParameters
            params.serverNames = listOf(SNIHostName(sniHost))
            ssl.sslParameters = params
            ssl.startHandshake()
            if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(sniHost, ssl.session)) {
                ssl.close()
                throw IOException("شهادة السيرفر لا تطابق الاسم $sniHost")
            }
            ssl
        } else {
            base
        }

        val hostHeader = cfg.hostHeader.ifBlank { cfg.sni.ifBlank { host } }
        val path = cfg.path.ifBlank { "/" }
        val inp = sock.getInputStream()
        val out = sock.getOutputStream()
        handshake(inp, out, hostHeader, path)

        val lock = Any()
        val o = WsOutput(out, lock)
        wsOut = o
        wsIn = WsInput(inp) { op, payload -> o.sendFrame(op, payload, 0, payload.size) }
        return sock
    }

    override fun getInputStream(socket: Socket): InputStream = wsIn ?: socket.getInputStream()
    override fun getOutputStream(socket: Socket): OutputStream = wsOut ?: socket.getOutputStream()

    private fun handshake(inp: InputStream, out: OutputStream, hostHeader: String, path: String) {
        val keyBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = Base64.getEncoder().encodeToString(keyBytes)
        val req = "GET $path HTTP/1.1\r\n" +
            "Host: $hostHeader\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: $key\r\n" +
            "Sec-WebSocket-Version: 13\r\n\r\n"
        out.write(req.toByteArray(Charsets.ISO_8859_1))
        out.flush()

        val sb = StringBuilder()
        while (!sb.endsWith("\r\n\r\n")) {
            val b = inp.read()
            if (b < 0) throw IOException("أُغلق الاتصال أثناء ترقية WebSocket")
            sb.append(b.toChar())
            if (sb.length > 16384) throw IOException("رد WebSocket غير صالح")
        }
        val status = sb.lineSequence().first()
        if (!status.contains(" 101")) throw IOException("فشلت ترقية WebSocket: $status")
    }
}

private class WsOutput(private val out: OutputStream, private val lock: Any) : OutputStream() {
    private val rnd = SecureRandom()

    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(buf: ByteArray, off: Int, len: Int) {
        sendFrame(2, buf, off, len)
    }

    override fun flush() {
        synchronized(lock) { out.flush() }
    }

    fun sendFrame(opcode: Int, buf: ByteArray, off: Int, len: Int) {
        val mask = ByteArray(4).also { rnd.nextBytes(it) }
        val header = ByteArrayOutputStream()
        header.write(0x80 or opcode)
        when {
            len < 126 -> header.write(0x80 or len)
            len <= 65535 -> {
                header.write(0x80 or 126)
                header.write(len shr 8)
                header.write(len and 0xff)
            }
            else -> {
                header.write(0x80 or 127)
                for (i in 7 downTo 0) header.write(((len.toLong() shr (8 * i)) and 0xff).toInt())
            }
        }
        header.write(mask)
        val masked = ByteArray(len) { (buf[off + it].toInt() xor mask[it and 3].toInt()).toByte() }
        synchronized(lock) {
            out.write(header.toByteArray())
            out.write(masked)
            out.flush()
        }
    }
}

private class WsInput(
    private val inp: InputStream,
    private val control: (opcode: Int, payload: ByteArray) -> Unit
) : InputStream() {

    private var remaining = 0L
    private var closed = false

    override fun read(): Int {
        val b = ByteArray(1)
        val n = read(b, 0, 1)
        return if (n < 0) -1 else (b[0].toInt() and 0xff)
    }

    override fun read(buf: ByteArray, off: Int, len: Int): Int {
        if (closed) return -1
        while (remaining == 0L) {
            if (!nextFrame()) return -1
        }
        val n = inp.read(buf, off, minOf(len.toLong(), remaining).toInt())
        if (n < 0) {
            closed = true
            return -1
        }
        remaining -= n
        return n
    }

    /** قراءة بايت وسط الإطار: مهلة القراءة هنا تُعاد بدل أن تُفسد حالة الإطار. */
    private fun readByte(): Int {
        var tries = 0
        while (true) {
            try {
                val v = inp.read()
                if (v < 0) throw EOFException()
                return v
            } catch (e: SocketTimeoutException) {
                if (++tries > 30) throw e
            }
        }
    }

    private fun nextFrame(): Boolean {
        val b0 = inp.read() // مهلة هنا آمنة: لم نستهلك شيئاً بعد
        if (b0 < 0) {
            closed = true
            return false
        }
        val b1 = readByte()
        val opcode = b0 and 0x0f
        val masked = (b1 and 0x80) != 0
        var len = (b1 and 0x7f).toLong()
        if (len == 126L) {
            len = ((readByte() shl 8) or readByte()).toLong()
        } else if (len == 127L) {
            len = 0
            repeat(8) { len = (len shl 8) or readByte().toLong() }
        }
        if (masked) repeat(4) { readByte() } // السيرفر لا يفترض أن يقنّع إطاراته

        when (opcode) {
            0, 1, 2 -> {
                remaining = len
                return true
            }
            8 -> {
                closed = true
                return false
            }
            9, 10 -> {
                val payload = ByteArray(len.toInt()) { readByte().toByte() }
                if (opcode == 9) control(10, payload)
                return true
            }
            else -> {
                var left = len
                while (left > 0) {
                    readByte()
                    left--
                }
                return true
            }
        }
    }
}
