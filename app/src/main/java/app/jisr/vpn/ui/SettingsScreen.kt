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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jisr.vpn.data.Prefs

@Composable
fun SettingsScreen() {
    var auto by remember { mutableStateOf(Prefs.autoConnect) }
    var dns by remember { mutableStateOf(Prefs.dns) }
    val dnsOptions = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9")

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("الإعدادات", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))

        Row(
            Modifier.fillMaxWidth().glass().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("الاتصال التلقائي", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("يتصل عند فتح التطبيق", color = C.Muted, fontSize = 13.sp)
            }
            Switch(auto) {
                auto = it
                Prefs.autoConnect = it
            }
        }

        Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
            Text("خادم DNS", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dnsOptions.forEach { opt ->
                    val sel = opt == dns
                    Text(
                        opt,
                        color = if (sel) Color(0xFF10201F) else C.Text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (sel) C.Teal else C.GlassStrong)
                            .clickable {
                                dns = opt
                                Prefs.dns = opt
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }

        Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
            Text("الخصوصية", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "التطبيق ما يجمع أي بيانات. السيرفرات تُحفظ على جوالك فقط.",
                color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }

        Text("الإصدار 1.0.0", color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun Switch(on: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(width = 52.dp, height = 30.dp)
            .clip(CircleShape)
            .background(if (on) C.Teal else Color(0xFF4A5256))
            .clickable { onChange(!on) }
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(if (on) Color(0xFF10201F) else Color(0xFFEEF1F1)))
    }
}
