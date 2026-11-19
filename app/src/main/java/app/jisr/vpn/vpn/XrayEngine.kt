package app.jisr.vpn.vpn

import android.content.Context
import android.system.Os
import android.util.Log
import java.lang.reflect.Proxy

/**
 * غلاف المحرك (libv2ray / Xray-core).
 *
 * يُستدعى المحرك بالـreflection عمداً، حتى يُبنى التطبيق وتظهر الواجهة
 * حتى لو تغيّر شكل مكتبة المحرك بين الإصدارات. هذا الملف هو المكان الوحيد
 * الذي يحتاج تعديلاً إذا تغيّرت واجهة المكتبة.
 *
 * ملاحظة: ربط النفق (TUN) هنا يعتمد على متغير البيئة xray.tun.fd ومدخل tun
 * في Xray. هذا الجزء لم يُجرَّب على جهاز حقيقي بعد.
 */
object XrayEngine {
    private const val TAG = "JisrEngine"
    private var point: Any? = null

    fun start(ctx: Context, config: String, tunFd: Int) {
        // يجب ضبط المتغير قبل تحميل مكتبة المحرك لأول مرة في هذه العملية
        Os.setenv("xray.tun.fd", tunFd.toString(), true)

        val lib = try {
            Class.forName("libv2ray.Libv2ray")
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException("مكتبة المحرك (libv2ray) غير مضمّنة في هذا البناء")
        }

        val dir = ctx.filesDir.absolutePath
        lib.methods.firstOrNull { it.name == "initV2Env" }?.let { m ->
            val args = Array<Any?>(m.parameterCount) { dir }
            m.invoke(null, *args)
        }

        val supportCls = Class.forName("libv2ray.V2RayVPNServiceSupportsSet")
        val handler = Proxy.newProxyInstance(supportCls.classLoader, arrayOf(supportCls)) { _, method, _ ->
            when (method.returnType) {
                java.lang.Boolean.TYPE -> true
                java.lang.Long.TYPE -> 0L
                java.lang.Integer.TYPE -> 0
                else -> null
            }
        }

        val factory = lib.methods.first { it.name == "newV2RayPoint" }
        val p = if (factory.parameterCount >= 2) factory.invoke(null, handler, false) else factory.invoke(null, handler)
            ?: throw IllegalStateException("تعذّر إنشاء المحرك")
        point = p

        p.javaClass.getMethod("setConfigureFileContent", String::class.java).invoke(p, config)
        p.javaClass.getMethod("runLoop", java.lang.Boolean.TYPE).invoke(p, false)
        Log.i(TAG, "engine started")
    }

    fun stop() {
        try {
            point?.let { it.javaClass.getMethod("stopLoop").invoke(it) }
        } catch (e: Throwable) {
            Log.w(TAG, "stop failed", e)
        }
        point = null
    }
}
