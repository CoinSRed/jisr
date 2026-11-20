package app.jisr.vpn.core

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

/**
 * يقرأ سجلات محرك الاتصال (Xray) ويحوّلها لرسائل واضحة.
 * المحرك يكتب أخطاءه في ملف داخل مجلد التطبيق (انظر ConfigBuilder).
 */
object LogParser {

    fun file(ctx: Context): File = File(ctx.filesDir, "xray.log")

    fun clear(ctx: Context) {
        try {
            file(ctx).writeText("")
        } catch (_: Exception) {
        }
    }

    /** آخر جزء من السجل (حتى 32 كيلوبايت). */
    fun tail(ctx: Context, maxBytes: Long = 32 * 1024): String {
        return try {
            val f = file(ctx)
            if (!f.exists() || f.length() == 0L) return ""
            RandomAccessFile(f, "r").use { raf ->
                val len = raf.length()
                val start = (len - maxBytes).coerceAtLeast(0)
                raf.seek(start)
                val buf = ByteArray((len - start).toInt())
                raf.readFully(buf)
                String(buf, Charsets.UTF_8)
            }
        } catch (_: Exception) {
            ""
        }
    }

    private val code403 = Regex("\\b403\\b")

    /** يمسح السجل من الأحدث للأقدم، وأول سطر يطابق قاعدة يحدد الرسالة. */
    fun analyze(text: String): Finding? {
        if (text.isBlank()) return null
        for (line in text.lines().asReversed()) {
            val l = line.lowercase()
            if (l.isBlank()) continue

            if (has(l, "certificate", "x509", "tls handshake", "handshake failure")) {
                return Finding(Level.FAIL, "مشكلة في شهادة TLS", "تأكد من قيمة SNI ومن أن السيرفر يدعم TLS.", "tls")
            }
            // أخطاء تسجيل الدخول الصريحة (آمنة لقطع الاتصال بسببها)
            if (has(l, "unauthorized", "credential", "invalid user", "invalid request user", "invalid id", "invalid password", "authentication") ||
                code403.containsMatchIn(l)
            ) {
                return Finding(Level.FAIL, "فشل تسجيل الدخول", "تأكد من صحة الـ UUID أو كلمة المرور.", "auth")
            }
            if (has(l, "timeout", "timed out", "connection refused", "refused")) {
                return Finding(Level.FAIL, "السيرفر لا يستجيب", "جرب سيرفر آخر.", "server")
            }
            if (has(l, "no such host")) {
                return Finding(Level.FAIL, "تعذّر تحويل اسم السيرفر", "تأكد من العنوان (Host) أو غيّر خادم DNS من الإعدادات.", "host")
            }
            // كلمات عامة: تُعرض في التشخيص فقط ولا تقطع الاتصال
            if (has(l, "invalid", "auth")) {
                return Finding(Level.WARN, "فشل تسجيل الدخول (محتمل)", "تأكد من صحة الـ UUID أو كلمة المرور.", "auth_weak")
            }
        }
        return null
    }

    private fun has(line: String, vararg keys: String) = keys.any { line.contains(it) }
}
