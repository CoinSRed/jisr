package app.jisr.vpn.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import app.jisr.vpn.core.LogParser

/**
 * خدمة الـVPN. تعمل في عملية منفصلة (:vpn) وتُقتل العملية عند الإيقاف
 * حتى يبدأ المحرك نظيفاً في الاتصال التالي.
 */
class JisrVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var ssh: SshTunnel? = null
    private var current = "disconnected"
    private var currentError: String? = null
    private var since = 0L

    private val queryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            publish(current, currentError)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            queryReceiver,
            IntentFilter(VpnProtocol.ACTION_QUERY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == VpnProtocol.ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        val config = intent?.getStringExtra(VpnProtocol.EXTRA_CONFIG)
        val dns = intent?.getStringExtra(VpnProtocol.EXTRA_DNS) ?: "1.1.1.1"
        val sshJson = intent?.getStringExtra(VpnProtocol.EXTRA_SSH)
        if (config == null) {
            shutdown()
            return START_NOT_STICKY
        }
        Thread { connect(config, dns, sshJson) }.start()
        return START_NOT_STICKY
    }

    private fun connect(config: String, dns: String, sshJson: String?) {
        publish("connecting")
        try {
            // SSH أولاً: التطبيق مستثنى من النفق فيتصل بالسيرفر مباشرة
            if (sshJson != null) {
                try {
                    val t = SshTunnel(SshConfig.fromJson(sshJson), filesDir)
                    t.start()
                    ssh = t
                } catch (e: Throwable) {
                    throw IllegalStateException(sshFailure(e))
                }
            }

            val builder = Builder()
                .setSession("جسر")
                .setMtu(1500)
                .addAddress("10.10.14.1", 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(dns)
            // اتصالات التطبيق نفسه (المحرك وSSH) تخرج مباشرة وتتجنب الدوران داخل النفق
            builder.addDisallowedApplication(packageName)

            val pfd = builder.establish() ?: throw IllegalStateException("تعذّر إنشاء النفق")
            tun = pfd
            XrayEngine.start(this, config, pfd.fd)
            publish("connected")
        } catch (e: Throwable) {
            Log.e("JisrVpn", "connect failed", e)
            val msg = e.cause?.message ?: e.message ?: e.javaClass.simpleName
            publish("error", msg)
            cleanup()
            stopSelf()
            killSoon()
        }
    }

    /** يحوّل أخطاء SSH لرسالة عربية، ويكتب كلمة مفتاحية في السجل ليقرأها المساعد الذكي. */
    private fun sshFailure(e: Throwable): String {
        val m = (e.message ?: "").lowercase()
        return when {
            m.contains("auth") -> {
                appendLog("ssh: authentication failed")
                "فشل تسجيل الدخول: تأكد من اسم المستخدم وكلمة المرور"
            }
            m.contains("hostkey") || m.contains("host key") -> {
                appendLog("ssh: host key changed")
                "تغيّرت بصمة مفتاح السيرفر. قد يكون هناك اعتراض على الاتصال. احذف السيرفر وأضفه من جديد إذا كنت تثق بالتغيير"
            }
            m.contains("timeout") || m.contains("timed out") || m.contains("refused") || m.contains("unknownhost") || m.contains("unreachable") -> {
                appendLog("ssh: connection timeout")
                "السيرفر لا يستجيب، جرب سيرفراً آخر"
            }
            else -> e.message ?: "خطأ في اتصال SSH"
        }
    }

    private fun appendLog(line: String) {
        try {
            LogParser.file(this).appendText(line + "\n")
        } catch (_: Exception) {
        }
    }

    private fun cleanup() {
        XrayEngine.stop()
        try {
            ssh?.stop()
        } catch (_: Exception) {
        }
        ssh = null
        try {
            tun?.close()
        } catch (_: Exception) {
        }
        tun = null
    }

    private fun shutdown() {
        cleanup()
        publish("disconnected")
        stopSelf()
        killSoon()
    }

    override fun onRevoke() {
        shutdown()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(queryReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    private fun publish(status: String, error: String? = null) {
        current = status
        currentError = error
        since = if (status == "connected") (if (since == 0L) System.currentTimeMillis() else since) else 0L
        sendBroadcast(
            Intent(VpnProtocol.ACTION_STATUS)
                .setPackage(packageName)
                .putExtra(VpnProtocol.EXTRA_STATUS, status)
                .putExtra(VpnProtocol.EXTRA_ERROR, error)
                .putExtra(VpnProtocol.EXTRA_SINCE, since)
        )
    }

    private fun killSoon() {
        Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 600)
    }
}
