package app.jisr.vpn.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jisr.vpn.core.LinkParser
import app.jisr.vpn.data.Prefs
import app.jisr.vpn.data.Server
import java.util.UUID

@Composable
fun ServersScreen(onPicked: () -> Unit) {
    val ctx = LocalContext.current
    var servers by remember { mutableStateOf(Prefs.servers) }
    var selected by remember { mutableStateOf(Prefs.selectedId) }
    var note by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }

    /** يضيف الروابط الصالحة فقط. يرجع رسالة خطأ أو null عند النجاح. */
    fun addLinks(links: List<String>): String? {
        val added = links.mapNotNull { l ->
            LinkParser.parse(l)?.let { Server(UUID.randomUUID().toString(), it.name, l) }
        }
        if (added.isEmpty()) {
            return "ما لقيت رابط صالح. الصيغ المدعومة: vmess:// و vless:// و trojan://"
        }
        servers = servers + added
        Prefs.servers = servers
        if (selected == null) {
            selected = added.first().id
            Prefs.selectedId = selected
        }
        note = "تمت إضافة ${added.size} سيرفر"
        return null
    }

    fun importFromClipboard() {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty()
        val links = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        note = addLinks(links) ?: note
    }

    if (adding) {
        AddServerScreen(onBack = { adding = false }, onSubmit = { addLinks(it) })
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Text("السيرفرات", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("إضافة يدوياً", brick = true, modifier = Modifier.weight(1f)) { adding = true }
            GlassButton("من الحافظة", brick = false, modifier = Modifier.weight(1f)) { importFromClipboard() }
        }
        note?.let { Text(it, color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(12.dp))

        if (servers.isEmpty()) {
            Text(
                "ما عندك سيرفرات بعد. اضغط \"إضافة يدوياً\" لإدخال بيانات السيرفر، أو انسخ رابطه (vmess:// أو vless:// أو trojan://) واضغط \"من الحافظة\".",
                color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 24.dp)
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(servers, key = { it.id }) { s ->
                val isSel = s.id == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .glass()
                        .then(if (isSel) Modifier.border(2.dp, C.Teal, RoundedCornerShape(16.dp)) else Modifier)
                        .clickable {
                            selected = s.id
                            Prefs.selectedId = s.id
                            onPicked()
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(s.link.substringBefore("://").uppercase(), color = C.Muted, fontSize = 12.sp)
                    }
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "حذف",
                        tint = C.BrickLight,
                        modifier = Modifier.clickable {
                            servers = servers.filter { it.id != s.id }
                            Prefs.servers = servers
                            if (selected == s.id) {
                                selected = servers.firstOrNull()?.id
                                Prefs.selectedId = selected
                            }
                        }.padding(8.dp)
                    )
                }
            }
        }
    }
}
