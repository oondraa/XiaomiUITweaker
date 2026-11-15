package cz.miuitweak.ioshell.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Vlastni jednoduche ikony (zadne Apple assety). */

@Composable
fun LockGlyph(open: Boolean, color: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    val t by animateFloatAsState(if (open) 1f else 0f, label = "lock")
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val bodyTop = h * 0.45f
        val bodyLeft = w * 0.18f
        val bodyW = w * 0.64f
        drawRoundRect(
            color, Offset(bodyLeft, bodyTop), Size(bodyW, h * 0.5f),
            cornerRadius = CornerRadius(w * 0.1f),
        )
        // tremen: pri odemceni se zvedne a levy konec vyjede z tela zamku
        val sw = w * 0.1f
        val r = bodyW * 0.32f
        val cx = w / 2f
        val top = bodyTop - h * 0.32f - t * h * 0.1f
        val shackle = Path().apply {
            moveTo(cx - r, bodyTop + sw - t * h * 0.2f)
            lineTo(cx - r, top + r)
            arcTo(
                androidx.compose.ui.geometry.Rect(cx - r, top, cx + r, top + 2 * r),
                180f, 180f, false,
            )
            lineTo(cx + r, bodyTop + sw)
        }
        drawPath(shackle, color, style = Stroke(width = sw, cap = StrokeCap.Round))
    }
}

@Composable
fun FlashlightGlyph(color: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val head = Path().apply {
            moveTo(w * 0.22f, h * 0.06f)
            lineTo(w * 0.78f, h * 0.06f)
            lineTo(w * 0.78f, h * 0.2f)
            lineTo(w * 0.64f, h * 0.38f)
            lineTo(w * 0.36f, h * 0.38f)
            lineTo(w * 0.22f, h * 0.2f)
            close()
        }
        drawPath(head, color)
        drawRoundRect(
            color, Offset(w * 0.36f, h * 0.42f), Size(w * 0.28f, h * 0.52f),
            cornerRadius = CornerRadius(w * 0.06f),
        )
    }
}

@Composable
fun CameraGlyph(color: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val body = Path().apply {
            moveTo(w * 0.34f, h * 0.24f)
            lineTo(w * 0.40f, h * 0.14f)
            lineTo(w * 0.60f, h * 0.14f)
            lineTo(w * 0.66f, h * 0.24f)
            close()
        }
        drawPath(body, color)
        drawRoundRect(
            color, Offset(w * 0.06f, h * 0.22f), Size(w * 0.88f, h * 0.62f),
            cornerRadius = CornerRadius(w * 0.12f),
        )
        drawCircle(Color.Black.copy(alpha = 0.55f), radius = w * 0.2f, center = Offset(w / 2, h * 0.53f))
        drawCircle(color, radius = w * 0.13f, center = Offset(w / 2, h * 0.53f))
    }
}
