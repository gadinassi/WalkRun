package com.example.walkrun.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val StartGreen = Color(0xFF2E7D32)
val StopRed = Color(0xFFC62828)

enum class Glyph { PLAY, PAUSE, STOP }

/**
 * Round button with a media symbol: ▶ start/resume, ‖ pause, ■ stop.
 * The symbols are drawn, so they do not depend on an icon library, and they do not mirror in RTL.
 */
@Composable
fun RoundIconButton(
    glyph: Glyph,
    color: Color,
    size: Dp,
    description: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) color else color.copy(alpha = 0.4f),
        shadowElevation = 6.dp,
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
    ) {
        Canvas(Modifier.fillMaxSize().padding(size * 0.3f)) { drawGlyph(glyph, Color.White) }
    }
}

private fun DrawScope.drawGlyph(glyph: Glyph, color: Color) {
    val w = size.width
    val h = size.height
    when (glyph) {
        Glyph.PLAY -> {
            // Moved a little to the right, so the triangle looks centered in the circle.
            val path = Path().apply {
                moveTo(w * 0.18f, 0f)
                lineTo(w * 0.18f, h)
                lineTo(w * 1.02f, h / 2f)
                close()
            }
            drawPath(path, color)
        }
        Glyph.PAUSE -> {
            val barW = w * 0.3f
            val r = CornerRadius(barW * 0.2f)
            drawRoundRect(color, Offset(w * 0.1f, 0f), Size(barW, h), r)
            drawRoundRect(color, Offset(w * 0.6f, 0f), Size(barW, h), r)
        }
        Glyph.STOP -> {
            val inset = w * 0.08f
            drawRoundRect(
                color, Offset(inset, inset), Size(w - 2 * inset, h - 2 * inset), CornerRadius(w * 0.08f)
            )
        }
    }
}
