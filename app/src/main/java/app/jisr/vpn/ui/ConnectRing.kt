package app.jisr.vpn.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** زر الاتصال: حلقة تدور أثناء الاتصال وتتوهّج عند النجاح. */
@Composable
fun ConnectRing(status: String, onClick: () -> Unit) {
    val on = status == "connected"
    val busy = status == "connecting"

    val inf = rememberInfiniteTransition(label = "ring")
    val rot by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "rot"
    )
    val glow by inf.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "glow"
    )

    val brush = if (on) Brush.linearGradient(listOf(C.TealLight, C.TealDark))
    else Brush.linearGradient(listOf(C.BrickLight, C.BrickDark))
    val fg = if (on) Color(0xFF0B1A19) else Color.White

    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val d = size.minDimension - 2 * stroke
            val topLeft = Offset(stroke, stroke)
            val arcSize = Size(d, d)
            drawCircle(
                color = if (on) C.Teal.copy(alpha = 0.55f) else Color(0x33FFFFFF),
                radius = d / 2,
                style = Stroke(stroke)
            )
            if (on) {
                drawCircle(
                    color = C.Teal.copy(alpha = glow * 0.35f),
                    radius = d / 2 + 6.dp.toPx(),
                    style = Stroke(14.dp.toPx())
                )
            }
            if (busy) {
                rotate(rot) {
                    drawArc(
                        color = C.Teal,
                        startAngle = 0f,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke * 1.6f, cap = StrokeCap.Round)
                    )
                }
            }
        }
        Box(
            Modifier
                .size(188.dp)
                .clip(CircleShape)
                .background(Color(0x14FFFFFF))
                .border(1.dp, C.GlassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(brush)
                    .border(1.dp, Color(0x59FFFFFF), CircleShape)
                    .clickable(enabled = !busy, onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PowerIcon(fg, 46)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            busy -> "جارٍ الاتصال"
                            on -> "قطع الاتصال"
                            else -> "اتصال"
                        },
                        color = fg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            }
        }
    }
}
