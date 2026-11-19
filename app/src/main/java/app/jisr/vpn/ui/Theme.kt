package app.jisr.vpn.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object C {
    val Bg = Color(0xFF1B1F21)
    val Text = Color(0xFFEEF1F1)
    val Muted = Color(0xFFA3ADB0)
    val Teal = Color(0xFF2EC4B6)
    val TealDark = Color(0xFF1F9A8F)
    val TealLight = Color(0xFF5EE0D3)
    val Brick = Color(0xFFB7472A)
    val BrickLight = Color(0xFFD2593C)
    val BrickDark = Color(0xFF8F3320)
    val Glass = Color(0x14FFFFFF)
    val GlassStrong = Color(0x1FFFFFFF)
    val GlassBorder = Color(0x29FFFFFF)
}

/** خلفية داكنة بتوهّجين (بني وتركوازي) ليظهر تأثير الزجاج فوقها. */
@Composable
fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .background(C.Bg)
            .background(
                Brush.radialGradient(
                    colors = listOf(C.Brick.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(100f, 160f),
                    radius = 900f
                )
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(C.Teal.copy(alpha = 0.32f), Color.Transparent),
                    center = Offset(1000f, 1500f),
                    radius = 1100f
                )
            ),
        content = content
    )
}

fun Modifier.glass(radius: Int = 16): Modifier =
    this
        .clip(RoundedCornerShape(radius.dp))
        .background(C.Glass)
        .border(BorderStroke(1.dp, C.GlassBorder), RoundedCornerShape(radius.dp))
