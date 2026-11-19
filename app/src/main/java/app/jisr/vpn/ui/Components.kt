package app.jisr.vpn.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush

/** أيقونة الطاقة مرسومة يدوياً (غير موجودة في مكتبة الأيقونات الأساسية). */
@Composable
fun PowerIcon(color: Color, size: Int = 52) {
    Canvas(Modifier.size(size.dp)) {
        val w = this.size.width
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        drawArc(
            color = color,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(w * 0.15f, w * 0.2f),
            size = Size(w * 0.7f, w * 0.7f),
            style = stroke
        )
        drawLine(color, Offset(w * 0.5f, w * 0.08f), Offset(w * 0.5f, w * 0.5f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
    }
}

@Composable
fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(0)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        NavItem(Icons.Default.Home, "الرئيسية", selected == 0) { onSelect(0) }
        NavItem(Icons.Default.Place, "السيرفرات", selected == 1) { onSelect(1) }
        NavItem(Icons.Default.Settings, "الإعدادات", selected == 2) { onSelect(2) }
    }
}

@Composable
private fun RowScope.NavItem(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val tint = if (active) C.Teal else C.Muted
    Column(
        Modifier
            .weight(1f)
            .height(56.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** زر زجاجي بتدرّج لوني ولمعة. */
@Composable
fun GlassButton(text: String, brick: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val brush = if (brick) Brush.linearGradient(listOf(C.BrickLight, C.BrickDark))
    else Brush.linearGradient(listOf(C.TealLight, C.TealDark))
    Box(
        modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(brush)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (brick) Color.White else Color(0xFF10201F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}
