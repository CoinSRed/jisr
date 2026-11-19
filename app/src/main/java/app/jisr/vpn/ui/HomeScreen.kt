package app.jisr.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.TrafficStats
import android.os.Process
import app.jisr.vpn.core.Diagnostics
import app.jisr.vpn.core.LinkParser
import app.jisr.vpn.core.NetworkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.jisr.vpn.core.Finding
import app.jisr.vpn.core.Level
import app.jisr.vpn.core.ServerHealth
import app.jisr.vpn.data.Prefs
import app.jisr.vpn.vpn.VpnController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(message: String?, onToggle: () -> Unit, onOpenServers: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val status = VpnController.status
    val on = status == "connected"
    val server = Prefs.servers.firstOrNull { it.id == Prefs.selectedId }

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(on) {
        while (on) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val secs = if (on && VpnController.since > 0) ((now - VpnController.since) / 1000).coerceAtLeast(0) else 0L
    val timer = "%02d:%02d:%02d".format(secs / 3600, (secs % 3600) / 60, secs % 60)

    var report by remember { mutableStateOf<List<Finding>?>(null) }
    var checking by remember { mutableStateOf(false) }

    // نوع الشبكة (Wi-Fi / 4G / 5G)
    var net by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            net = NetworkInfo.label(ctx)
            delay(3000)
        }
    }

    // السرعة اللحظية (حركة تطبيق جسر على الشبكة، تشمل الإضافات الداخلية للنفق)
    var down by remember { mutableStateOf(0L) }
    var up by remember { mutableStateOf(0L) }
    LaunchedEffect(on) {
        if (!on) {
            down = 0L
            up = 0L
            return@LaunchedEffect
        }
        val uid = Process.myUid()
        var lastRx = TrafficStats.getUidRxBytes(uid)
        var lastTx = TrafficStats.getUidTxBytes(uid)
        while (true) {
            delay(1000)
            val rx = TrafficStats.getUidRxBytes(uid)
            val tx = TrafficStats.getUidTxBytes(uid)
            if (rx >= 0 && lastRx >= 0) down = (rx - lastRx).coerceAtLeast(0)
            if (tx >= 0 && lastTx >= 0) up = (tx - lastTx).coerceAtLeast(0)
            lastRx = rx
            lastTx = tx
        }
    }

    // زمن الاستجابة المباشر للسيرفر المتصل
    var liveMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(on, server?.id) {
        if (!on) {
            liveMs = null
            return@LaunchedEffect
        }
        val node = server?.let { LinkParser.parse(it.link) } ?: return@LaunchedEffect
        while (true) {
            val ms = withContext(Dispatchers.IO) { Diagnostics.tcpLatency(node.host, node.port, 2500) }
            liveMs = ms ?: -1L
            delay(5000)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (on) C.Teal else C.Brick))
                Text("جسر", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (net.isNotEmpty()) {
                    Text(
                        net,
                        color = C.Text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.glass(99).padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Text(
                    if (on) "محمي" else "غير محمي",
                    color = if (on) C.Teal else C.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.glass(99).padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        ConnectRing(status, onToggle)
        Spacer(Modifier.height(12.dp))

        Text(
            when (status) {
                "connected" -> "متصل"
                "connecting" -> "جارٍ الاتصال…"
                "error" -> "فشل الاتصال"
                else -> "غير متصل"
            },
            color = if (on) C.Teal else C.Text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        if (on) {
            Text(timer, color = C.Text, fontSize = 30.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
        } else {
            Text(
                message ?: VpnController.error ?: "اضغط الزر للاتصال",
                color = if (VpnController.error != null || message != null) C.BrickLight else C.Muted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (on) {
            Spacer(Modifier.height(18.dp))
            val l = liveMs
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Modifier.weight(1f), "تنزيل", fmtSpeed(down), C.Teal)
                StatTile(Modifier.weight(1f), "رفع", fmtSpeed(up), C.BrickLight)
                StatTile(
                    Modifier.weight(1f), "الاستجابة",
                    when {
                        l == null -> "…"
                        l > 0 -> "${l}ms"
                        else -> "—"
                    },
                    C.Text
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // السيرفر المختار
        val ms = server?.let { ServerHealth.latency[it.id] }
        Row(
            Modifier.fillMaxWidth().glass().clickable(onClick = onOpenServers).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(server?.name ?: "لم تختر سيرفراً", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("السيرفر المختار", color = C.Muted, fontSize = 13.sp)
            }
            if (ms != null) {
                Text(
                    if (ms > 0) "${ms}ms" else "لا يرد",
                    color = if (ms in 1..300) C.Teal else if (ms > 0) C.Muted else C.BrickLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // المساعد الذكي
        Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("المساعد الذكي", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            val r = report
            if (r == null) {
                Text(
                    "يفحص الشبكة والـDNS والسيرفر، ويشرح لك سبب أي مشكلة وكيف تحلها.",
                    color = C.Muted, fontSize = 13.sp
                )
            } else {
                r.forEach { f ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(
                                when (f.level) {
                                    Level.OK -> C.Teal
                                    Level.WARN -> Color(0xFFE0B341)
                                    Level.FAIL -> C.BrickLight
                                }
                            )
                        )
                        Column {
                            Text(f.title, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(f.detail, color = C.Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
            GlassButton(
                if (checking) "جارٍ الفحص…" else "افحص الاتصال",
                brick = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!checking) {
                    checking = true
                    scope.launch {
                        report = Diagnostics.run(ctx, server, VpnController.error)
                        checking = false
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun fmtSpeed(bytesPerSec: Long): String = when {
    bytesPerSec >= 1_048_576 -> "%.1f MB/s".format(bytesPerSec / 1_048_576.0)
    bytesPerSec >= 1024 -> "${bytesPerSec / 1024} KB/s"
    else -> "$bytesPerSec B/s"
}

@Composable
private fun StatTile(modifier: Modifier, label: String, value: String, color: Color) {
    Column(
        modifier.glass().padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = C.Muted, fontSize = 12.sp)
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
