package app.jisr.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.remember
import app.jisr.vpn.core.ServerHealth
import app.jisr.vpn.data.Prefs
import app.jisr.vpn.ui.BottomBar
import app.jisr.vpn.ui.C
import app.jisr.vpn.ui.GlassBackground
import app.jisr.vpn.ui.HomeScreen
import app.jisr.vpn.ui.ServersScreen
import app.jisr.vpn.ui.SettingsScreen
import app.jisr.vpn.vpn.VpnController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        VpnController.register(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = C.Teal, background = C.Bg, surface = C.Bg)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    App()
                }
            }
        }
    }
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            message = VpnController.start(ctx)
        } else {
            message = "لازم توافق على إذن الـVPN عشان يشتغل الاتصال"
        }
    }

    fun connectFlow() {
        message = null
        val prep: Intent? = VpnService.prepare(ctx)
        if (prep != null) vpnPermission.launch(prep) else message = VpnController.start(ctx)
    }

    // إذن قراءة نوع الشبكة (4G/5G): يُطلب مرة واحدة فقط
    val phonePerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            ctx, android.Manifest.permission.READ_PHONE_STATE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted && !Prefs.askedPhonePerm) {
            Prefs.askedPhonePerm = true
            phonePerm.launch(android.Manifest.permission.READ_PHONE_STATE)
        }
    }

    var tried by remember { mutableStateOf(setOf<String>()) }
    var attempts by remember { mutableStateOf(0) }
    var failure by remember { mutableStateOf(0) }

    LaunchedEffect(VpnController.status) {
        when (VpnController.status) {
            "connected" -> {
                attempts = 0
                tried = emptySet()
            }
            "error" -> failure += 1
        }
    }

    // مراقبة السيرفر أثناء الاتصال: 3 فحوصات فاشلة متتالية تعني أنه انقطع
    LaunchedEffect(VpnController.status) {
        if (VpnController.status != "connected") return@LaunchedEffect
        val server = Prefs.servers.firstOrNull { it.id == Prefs.selectedId }
        val node = server?.let { app.jisr.vpn.core.LinkParser.parse(it.link) } ?: return@LaunchedEffect
        var misses = 0
        while (true) {
            kotlinx.coroutines.delay(8000)
            val ms = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                app.jisr.vpn.core.Diagnostics.tcpLatency(node.host, node.port, 2500)
            }
            misses = if (ms == null) misses + 1 else 0
            if (misses >= 3) {
                message = "انقطع الاتصال بالسيرفر، أحاول من جديد…"
                VpnController.stop(ctx)
                kotlinx.coroutines.delay(1500)
                failure += 1
                return@LaunchedEffect
            }
        }
    }

    // إعادة الاتصال: 3 محاولات فاشلة على نفس السيرفر ثم الانتقال للتالي
    LaunchedEffect(failure) {
        if (failure == 0 || !Prefs.smartSwitch) return@LaunchedEffect
        attempts += 1
        if (attempts < 3) {
            message = "فشل الاتصال، إعادة المحاولة ($attempts/3)…"
            kotlinx.coroutines.delay(1500L * attempts)
            message = VpnController.start(ctx) ?: message
        } else {
            attempts = 0
            val all = Prefs.servers
            val used = tried + listOfNotNull(Prefs.selectedId)
            tried = used
            val next = ServerHealth.best(all, used) ?: all.firstOrNull { it.id !in used }
            if (next != null) {
                Prefs.selectedId = next.id
                message = "فشل 3 مرات، انتقلت تلقائياً إلى: ${next.name}"
                kotlinx.coroutines.delay(800)
                message = VpnController.start(ctx) ?: message
            } else {
                message = "جرّبت كل السيرفرات ولم ينجح الاتصال. استخدم المساعد الذكي لمعرفة السبب."
            }
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(900)
        if (Prefs.autoConnect && VpnController.status == "disconnected") connectFlow()
    }

    GlassBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.weight(1f)) {
                when (tab) {
                    0 -> HomeScreen(
                        message = message,
                        onToggle = {
                            if (VpnController.status == "disconnected" || VpnController.status == "error") connectFlow()
                            else VpnController.stop(ctx)
                        },
                        onOpenServers = { tab = 1 }
                    )
                    1 -> ServersScreen(onPicked = { tab = 0 })
                    else -> SettingsScreen()
                }
            }
            BottomBar(tab) { tab = it }
        }
    }
}
