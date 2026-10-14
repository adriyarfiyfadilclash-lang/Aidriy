package com.aidriy.app.ui.ai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val Blue = Color(0xFF2563EB)
private val LightBlue = Color(0xFFDCE8FD)
private val Navy = Color(0xFF1E2A44)
private val Cyan = Color(0xFF5BD3FF)

/** The AI Assistant mascot, drawn in code (no image assets). [showSparkles] for the big version. */
@Composable
fun AiRobot(modifier: Modifier = Modifier, size: Dp = 140.dp, showSparkles: Boolean = true) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val s = w / 140f
        if (showSparkles) {
            drawCircle(Brush.radialGradient(listOf(LightBlue, Color.Transparent)), radius = 70 * s, center = Offset(70 * s, 74 * s))
            sparkle(Offset(18 * s, 62 * s), 12 * s)
            sparkle(Offset(122 * s, 92 * s), 8 * s)
            sparkle(Offset(114 * s, 28 * s), 5 * s)
        }
        // Antenna
        drawLine(Blue, Offset(70 * s, 22 * s), Offset(70 * s, 36 * s), strokeWidth = 4 * s)
        drawCircle(Blue, radius = 6 * s, center = Offset(70 * s, 18 * s))
        // Ears
        drawRoundRect(Blue, Offset(24 * s, 58 * s), Size(12 * s, 26 * s), CornerRadius(6 * s))
        drawRoundRect(Blue, Offset(104 * s, 58 * s), Size(12 * s, 26 * s), CornerRadius(6 * s))
        // Body
        drawRoundRect(Color.White, Offset(46 * s, 98 * s), Size(48 * s, 30 * s), CornerRadius(14 * s))
        drawRoundRect(LightBlue, Offset(46 * s, 98 * s), Size(48 * s, 30 * s), CornerRadius(14 * s), style = Stroke(2 * s))
        drawCircle(Blue, radius = 5 * s, center = Offset(70 * s, 113 * s))
        // Head
        drawRoundRect(Color.White, Offset(32 * s, 36 * s), Size(76 * s, 66 * s), CornerRadius(30 * s))
        drawRoundRect(LightBlue, Offset(32 * s, 36 * s), Size(76 * s, 66 * s), CornerRadius(30 * s), style = Stroke(2.5f * s))
        // Visor and eyes
        drawRoundRect(Navy, Offset(42 * s, 52 * s), Size(56 * s, 34 * s), CornerRadius(17 * s))
        drawOval(Cyan, Offset(54 * s, 62 * s), Size(10 * s, 14 * s))
        drawOval(Cyan, Offset(76 * s, 62 * s), Size(10 * s, 14 * s))
    }
}

private fun DrawScope.sparkle(center: Offset, r: Float) {
    val p = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x, center.y, center.x + r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + r)
        quadraticTo(center.x, center.y, center.x - r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - r)
        close()
    }
    drawPath(p, Blue)
}
