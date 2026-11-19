package app.jisr.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jisr.vpn.data.Prefs
import app.jisr.vpn.vpn.VpnController
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(message: String?, onToggle: () -> Unit, onOpenServers: () -> Unit) {
    val status = VpnController.status
    val on = status == "connected"
    val busy = status == "connecting"
    val ring = if (on) C.Teal else Color(0xFF4A5256)
    val brush = if (on) Brush.linearGradient(listOf(C.TealLight, C.TealDark))
    else Brush.linearGradient(listOf(C.BrickLight, C.BrickDark))
    val server = Prefs.servers.firstOrNull { it.id == Prefs.selectedId }

    // عدّاد مدة الاتصال
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(on) {
        while (on) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val secs = if (on && VpnController.since > 0) ((now - VpnController.since) / 1000).coerceAtLeast(0) else 0L
    val timer = "%02d:%02d:%02d".format(secs / 3600, (secs % 3600) / 60, secs % 60)

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("جسر", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                if (on) "محمي" else "غير محمي",
                color = if (on) C.Teal else C.Muted,
                fontSize = 13.sp,
                modifier = Modifier.glass(99).padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        Box(
            Modifier.size(232.dp).clip(CircleShape).background(C.Glass).border(6.dp, ring, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(176.dp)
                    .clip(CircleShape)
                    .background(brush)
                    .border(1.dp, Color(0x66FFFFFF), CircleShape)
                    .clickable(enabled = !busy, onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PowerIcon(if (on) Color(0xFF10201F) else Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            busy -> "جارٍ الاتصال…"
                            on -> "قطع الاتصال"
                            else -> "اتصال"
                        },
                        color = if (on) Color(0xFF10201F) else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
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
        val sub = message ?: VpnController.error ?: if (on) timer else "اضغط الزر للاتصال"
        Text(sub, color = C.Muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.weight(1f))

        Row(
            Modifier.fillMaxWidth().glass().clickable(onClick = onOpenServers).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(server?.name ?: "لم تختر سيرفراً", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("السيرفر المختار", color = C.Muted, fontSize = 13.sp)
            }
            Text("‹", color = C.Muted, fontSize = 24.sp)
        }
        Spacer(Modifier.height(16.dp))
    }
}
