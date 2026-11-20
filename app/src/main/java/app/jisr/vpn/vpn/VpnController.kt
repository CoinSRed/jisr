package app.jisr.vpn.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import app.jisr.vpn.core.ConfigBuilder
import app.jisr.vpn.core.LinkParser
import app.jisr.vpn.core.LogParser
import app.jisr.vpn.data.Prefs

/** حالة الاتصال التي تراها الواجهة، تُحدَّث من بثّ الخدمة. */
object VpnController {
    var status by mutableStateOf("disconnected") // disconnected | connecting | connected | error
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var since by mutableStateOf(0L)
        private set

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            status = intent?.getStringExtra(VpnProtocol.EXTRA_STATUS) ?: "disconnected"
            error = intent?.getStringExtra(VpnProtocol.EXTRA_ERROR)
            since = intent?.getLongExtra(VpnProtocol.EXTRA_SINCE, 0L) ?: 0L
        }
    }

    fun register(ctx: Context) {
        if (registered) return
        ContextCompat.registerReceiver(
            ctx.applicationContext,
            receiver,
            IntentFilter(VpnProtocol.ACTION_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        registered = true
        // اسأل الخدمة عن حالتها (إن كانت تعمل أصلاً)
        ctx.sendBroadcast(Intent(VpnProtocol.ACTION_QUERY).setPackage(ctx.packageName))
    }

    /** يرجع رسالة خطأ، أو null إذا بدأ الاتصال. */
    fun start(ctx: Context): String? {
        val server = Prefs.servers.firstOrNull { it.id == Prefs.selectedId }
            ?: return "اختر سيرفراً أولاً من تبويب السيرفرات"
        val node = LinkParser.parse(server.link) ?: return "رابط السيرفر غير صالح"
        LogParser.clear(ctx)
        val config = ConfigBuilder.build(node, Prefs.dns, LogParser.file(ctx).absolutePath)
        error = null
        status = "connecting"
        ctx.startService(
            Intent(ctx, JisrVpnService::class.java)
                .putExtra(VpnProtocol.EXTRA_CONFIG, config)
                .putExtra(VpnProtocol.EXTRA_DNS, Prefs.dns)
        )
        return null
    }

    fun stop(ctx: Context) {
        ctx.startService(Intent(ctx, JisrVpnService::class.java).setAction(VpnProtocol.ACTION_STOP))
    }
}
