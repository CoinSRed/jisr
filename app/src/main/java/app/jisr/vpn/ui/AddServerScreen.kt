package app.jisr.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jisr.vpn.core.LinkBuilder

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = C.Text,
    unfocusedTextColor = C.Text,
    focusedBorderColor = C.Teal,
    unfocusedBorderColor = C.GlassBorder,
    cursorColor = C.Teal,
    focusedLabelColor = C.Teal,
    unfocusedLabelColor = C.Muted
)

@Composable
private fun Field(label: String, value: String, number: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Uri),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Chips(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = C.Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                val sel = value == selected
                Text(
                    label,
                    color = if (sel) Color(0xFF10201F) else C.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (sel) C.Teal else C.GlassStrong)
                        .clickable { onSelect(value) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}

/** شاشة إضافة سيرفر: إدخال يدوي بالحقول، أو لصق رابط/عدة روابط. */
@Composable
fun AddServerScreen(onBack: () -> Unit, onSubmit: (List<String>) -> String?) {
    var tab by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    var proto by remember { mutableStateOf("vless") }
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var id by remember { mutableStateOf("") }
    var net by remember { mutableStateOf("ws") }
    var sec by remember { mutableStateOf("tls") }
    var sni by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var hostHeader by remember { mutableStateOf("") }
    var flow by remember { mutableStateOf("") }
    var pbk by remember { mutableStateOf("") }
    var sid by remember { mutableStateOf("") }
    var pasted by remember { mutableStateOf("") }

    fun saveManual() {
        val p = port.trim().toIntOrNull()
        when {
            host.isBlank() -> error = "اكتب عنوان السيرفر (Host)"
            p == null || p !in 1..65535 -> error = "المنفذ (Port) لازم يكون رقم صحيح"
            id.isBlank() -> error = if (proto == "trojan") "اكتب كلمة المرور" else "اكتب المعرّف (UUID)"
            sec == "reality" && pbk.isBlank() -> error = "Reality يحتاج المفتاح العام (Public Key)"
            else -> {
                val link = LinkBuilder.build(
                    proto, name.trim(), host.trim(), p, id.trim(), net, sec,
                    sni.trim(), path.trim(), hostHeader.trim(), flow.trim(), pbk.trim(), sid.trim()
                )
                error = onSubmit(listOf(link))
                if (error == null) onBack()
            }
        }
    }

    fun savePasted() {
        val links = pasted.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (links.isEmpty()) {
            error = "الصق رابط واحد على الأقل"
            return
        }
        error = onSubmit(links)
        if (error == null) onBack()
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("إضافة سيرفر", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("رجوع", color = C.Teal, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
        }

        Chips("طريقة الإضافة", listOf("0" to "يدوي", "1" to "لصق روابط"), tab.toString()) {
            tab = it.toInt()
            error = null
        }

        if (tab == 1) {
            OutlinedTextField(
                value = pasted,
                onValueChange = { pasted = it },
                label = { Text("vmess:// أو vless:// أو trojan:// (كل رابط في سطر)") },
                minLines = 5,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            GlassButton("إضافة", brick = true, modifier = Modifier.fillMaxWidth()) { savePasted() }
        } else {
            Chips("البروتوكول", listOf("vless" to "VLESS", "vmess" to "VMess", "trojan" to "Trojan"), proto) {
                proto = it
                if (it == "trojan" && sec == "none") sec = "tls"
                if (it != "vless" && sec == "reality") sec = "tls"
            }
            Field("الاسم (اختياري)", name) { name = it }
            Field("العنوان (Host)", host) { host = it }
            Field("المنفذ (Port)", port, number = true) { port = it }
            Field(if (proto == "trojan") "كلمة المرور" else "المعرّف (UUID)", id) { id = it }

            Chips("نوع النقل", listOf("tcp" to "TCP", "ws" to "WebSocket", "grpc" to "gRPC"), net) { net = it }

            val secOptions = buildList {
                if (proto != "trojan") add("none" to "بدون")
                add("tls" to "TLS")
                if (proto == "vless") add("reality" to "Reality")
            }
            Chips("التشفير", secOptions, sec) { sec = it }

            if (sec != "none") Field("SNI", sni) { sni = it }
            if (net == "ws" || net == "grpc") Field(if (net == "grpc") "اسم الخدمة (serviceName)" else "المسار (Path)", path) { path = it }
            if (net == "ws") Field("Host Header (اختياري)", hostHeader) { hostHeader = it }
            if (proto == "vless") Field("Flow (اختياري، مثل xtls-rprx-vision)", flow) { flow = it }
            if (sec == "reality") {
                Field("المفتاح العام (Public Key)", pbk) { pbk = it }
                Field("Short ID (اختياري)", sid) { sid = it }
            }

            GlassButton("حفظ السيرفر", brick = true, modifier = Modifier.fillMaxWidth()) { saveManual() }
        }

        error?.let { Text(it, color = C.BrickLight, fontSize = 14.sp) }

        Text(
            "ملاحظة: SSH و Shadowsocks غير مدعومين بعد في هذا الإصدار.",
            color = C.Muted, fontSize = 12.sp
        )
        Spacer(Modifier.height(24.dp))
    }
}
