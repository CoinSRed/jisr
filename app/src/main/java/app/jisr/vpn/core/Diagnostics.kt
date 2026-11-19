package app.jisr.vpn.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.jisr.vpn.data.Server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

enum class Level { OK, WARN, FAIL }

data class Finding(val level: Level, val title: String, val detail: String)

/**
 * المساعد الذكي: يفحص الشبكة والـDNS والسيرفر خطوة بخطوة، ويشرح السبب ويقترح الحل.
 * (فحص قائم على قواعد، يعمل على الجهاز بدون إرسال أي بيانات لأحد.)
 */
object Diagnostics {

    /** زمن الاتصال بمنفذ (ms)، أو null إذا فشل. */
    fun tcpLatency(host: String, port: Int, timeoutMs: Int = 3000): Long? = try {
        Socket().use { s ->
            val t0 = System.nanoTime()
            s.connect(InetSocketAddress(host, port), timeoutMs)
            ((System.nanoTime() - t0) / 1_000_000).coerceAtLeast(1)
        }
    } catch (e: Exception) {
        null
    }

    private fun hasInternet(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun run(ctx: Context, server: Server?, lastError: String?): List<Finding> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<Finding>()

            if (!hasInternet(ctx)) {
                out += Finding(
                    Level.FAIL, "لا يوجد إنترنت",
                    "تأكد من اتصال الواي فاي أو بيانات الجوال، وأن وضع الطيران مطفي."
                )
                return@withContext out
            }
            out += Finding(Level.OK, "الشبكة متصلة", "جوالك متصل بالإنترنت.")

            val dnsOk = try {
                InetAddress.getByName("cloudflare.com"); true
            } catch (e: Exception) {
                false
            }
            if (!dnsOk) {
                out += Finding(
                    Level.FAIL, "خادم DNS لا يستجيب",
                    "غيّر خادم DNS من الإعدادات (جرّب 8.8.8.8 أو 9.9.9.9) ثم أعد المحاولة."
                )
                return@withContext out
            }
            out += Finding(Level.OK, "DNS يعمل", "تحويل أسماء المواقع شغّال.")

            val base = tcpLatency("1.1.1.1", 443)
            out += when {
                base == null -> Finding(Level.WARN, "الإنترنت ضعيف", "ما قدرت أوصل لخادم الاختبار. قد تكون الشبكة ضعيفة أو محجوبة.")
                base > 400 -> Finding(Level.WARN, "الإنترنت بطيء (${base}ms)", "جرّب تقرّب من الراوتر أو تبدّل بين الواي فاي والبيانات.")
                else -> Finding(Level.OK, "سرعة الاستجابة جيدة (${base}ms)", "شبكتك بحالة ممتازة.")
            }

            if (server == null) {
                out += Finding(Level.WARN, "ما اخترت سيرفراً", "افتح تبويب السيرفرات واختر واحداً.")
            } else {
                val node = LinkParser.parse(server.link)
                if (node == null) {
                    out += Finding(Level.FAIL, "رابط السيرفر غير صالح", "احذف السيرفر وأضفه من جديد بالرابط الصحيح.")
                } else {
                    val ms = tcpLatency(node.host, node.port)
                    out += when {
                        ms == null -> Finding(
                            Level.FAIL, "السيرفر ما يرد",
                            "ممكن انتهى الحساب أو السيرفر متوقف أو المنفذ محجوب. جرّب سيرفراً ثانياً."
                        )
                        ms > 800 -> Finding(Level.WARN, "السيرفر بطيء (${ms}ms)", "جرّب سيرفراً أقرب لك، أو اختبر السيرفرات من تبويب السيرفرات.")
                        else -> Finding(Level.OK, "السيرفر يستجيب (${ms}ms)", "الوصول للسيرفر سليم.")
                    }
                }
            }

            lastError?.let { out += explain(it) }
            out
        }

    private fun explain(msg: String): Finding = when {
        msg.contains("libv2ray") || msg.contains("المحرك") ->
            Finding(Level.FAIL, "المحرك غير جاهز", "مكتبة الاتصال غير مضمّنة أو غير متوافقة في هذا البناء. أبلغ المطوّر بالنص: $msg")
        msg.contains("النفق") ->
            Finding(Level.FAIL, "تعذّر إنشاء النفق", "تأكد أنك وافقت على إذن الـVPN، وأن تطبيق VPN ثاني مو شغّال.")
        else ->
            Finding(Level.FAIL, "آخر خطأ في الاتصال", msg)
    }
}
