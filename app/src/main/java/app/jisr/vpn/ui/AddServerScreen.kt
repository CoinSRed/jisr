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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
private fun Field(
    label: String,
    value: String,
    number: Boolean = false,
    secret: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                secret -> KeyboardType.Password
                number -> KeyboardType.Number
                else -> KeyboardType.Uri
            }
        ),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Chips(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title.isNotBlank()) Text(title, color = C.Muted, fontSize = 13.sp)
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
    var id by remember { mutableStateOf("") }          // UUID أو كلمة المرور
    var user by remember { mutableStateOf("") }        // SSH
    var method by remember { mutableStateOf("aes-256-gcm") } // Shadowsocks
    var net by remember { mutableStateOf("ws") }
    var sec by remember { mutableStateOf("tls") }
    var sni by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var hostHeader by remember { mutableStateOf("") }
    var flow by remember { mutableStateOf("") }
    var pbk by remember { mutableStateOf("") }
    var sid by remember { mutableStateOf("") }
    var pasted by remember { mutableStateOf("") }

    val isSsh = proto == "ssh"
    val isSs = proto == "shadowsocks"

    fun selectProto(p: String) {
        val wasOther = proto == "ssh" || proto == "shadowsocks"
        proto = p
        error = null
        when (p) {
            "ssh" -> {
                net = "tcp"; sec = "none"; port = "22"
            }
            "shadowsocks" -> {
                net = "tcp"; sec = "none"; port = "8388"
            }
            else -> {
                if (wasOther) {
                    net = "ws"; sec = "tls"; port = "443"
                }
                if (p == "trojan" && sec == "none") sec = "tls"
                if (p != "vless" && sec == "reality") sec = "tls"
            }
        }
    }

    fun saveManual() {
        val p = port.trim().toIntOrNull()
        when {
            host.isBlank() -> error = "اكتب عنوان السيرفر (Host)"
            p == null || p !in 1..65535 -> error = "المنفذ (Port) لازم يكون رقم صحيح"
            isSsh && user.isBlank() -> error = "اكتب اسم المستخدم"
            (isSsh || isSs || proto == "trojan") && id.isBlank() -> error = "اكتب كلمة المرور"
            !isSsh && !isSs && proto != "trojan" && id.isBlank() -> error = "اكتب المعرّف (UUID)"
            sec == "reality" && pbk.isBlank() -> error = "Reality يحتاج المفتاح العام (Public Key)"
            else -> {
                val link = when {
                    isSsh -> LinkBuilder.ssh(
                        name.trim(), host.trim(), p, user.trim(), id, net, sec,
                        sni.trim(), path.trim(), hostHeader.trim()
                    )
                    isSs -> LinkBuilder.shadowsocks(name.trim(), host.trim(), p, method, id)
                    else -> LinkBuilder.build(
                        proto, name.trim(), host.trim(), p, id.trim(), net, sec,
                        sni.trim(), path.trim(), hostHeader.trim(), flow.trim(), pbk.trim(), sid.trim()
                    )
                }
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
                label = { Text("vmess:// vless:// trojan:// ss:// ssh:// (كل رابط في سطر)") },
                minLines = 5,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            GlassButton("إضافة", brick = true, modifier = Modifier.fillMaxWidth()) { savePasted() }
        } else {
            Chips("البروتوكول", listOf("vless" to "VLESS", "vmess" to "VMess", "trojan" to "Trojan"), proto) { selectProto(it) }
            Chips("", listOf("shadowsocks" to "Shadowsocks", "ssh" to "SSH"), proto) { selectProto(it) }

            Field("الاسم (اختياري)", name) { name = it }
            Field("العنوان (Host)", host) { host = it }
            Field("المنفذ (Port)", port, number = true) { port = it }

            if (isSsh) Field("اسم المستخدم", user) { user = it }
            Field(
                if (isSsh || isSs || proto == "trojan") "كلمة المرور" else "المعرّف (UUID)",
                id,
                secret = isSsh || isSs
            ) { id = it }

            if (isSs) {
                Chips(
                    "طريقة التشفير",
                    listOf("aes-256-gcm" to "AES-256", "aes-128-gcm" to "AES-128", "chacha20-ietf-poly1305" to "ChaCha20"),
                    method
                ) { method = it }
            } else if (isSsh) {
                Chips("نوع الاتصال", listOf("tcp" to "SSH مباشر", "ws" to "عبر WebSocket"), net) {
                    net = it
                    if (it == "ws") {
                        sec = "tls"; port = "443"
                    } else {
                        sec = "none"; port = "22"
                    }
                }
                if (net == "ws") {
                    Chips("التشفير", listOf("none" to "بدون (80)", "tls" to "TLS (443)"), sec) {
                        sec = it
                        port = if (it == "tls") "443" else "80"
                    }
                    if (sec == "tls") Field("SNI (اختياري)", sni) { sni = it }
                    Field("Host Header (اختياري)", hostHeader) { hostHeader = it }
                    Field("المسار Path (اختياري)", path) { path = it }
                }
            } else {
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
            }

            GlassButton("حفظ السيرفر", brick = true, modifier = Modifier.fillMaxWidth()) { saveManual() }
        }

        error?.let { Text(it, color = C.BrickLight, fontSize = 14.sp) }

        if (isSsh && tab == 0) {
            Text(
                "SSH عادي بدون أي Payload. أول اتصال يحفظ بصمة السيرفر، وإذا تغيّرت لاحقاً يرفض الاتصال حمايةً لك.",
                color = C.Muted, fontSize = 12.sp
            )
        }
        Text(
            "روابط Shadowsocks التي فيها plugin (مثل v2ray-plugin) غير مدعومة.",
            color = C.Muted, fontSize = 12.sp
        )
        Spacer(Modifier.height(24.dp))
    }
}
