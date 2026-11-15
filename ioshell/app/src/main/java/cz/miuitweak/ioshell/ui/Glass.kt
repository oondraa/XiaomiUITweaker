package cz.miuitweak.ioshell.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * "Matne sklo": misto skutecneho rozmazani toho, co je pod prvkem, kreslime vyrez
 * z predem rozmazane tapety na stejnem miste. Tapetu kreslime sami, takze to sedi presne
 * a je to levne (zadny blur v kazdem snimku).
 */
@Stable
class GlassSource {
    var image: ImageBitmap? by mutableStateOf(null)
    var coords: LayoutCoordinates? = null
}

val LocalGlass = staticCompositionLocalOf<GlassSource?> { null }

/** Oznaci kontejner, ve kterem je nakreslena tapeta (cela plocha). */
fun Modifier.glassSource(src: GlassSource): Modifier = onGloballyPositioned { src.coords = it }

fun Modifier.glass(
    shape: Shape,
    tint: Color = Ios.GlassTint,
    edge: Boolean = true,
): Modifier = composed {
    val src = LocalGlass.current
    var rel by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    this
        .onGloballyPositioned { me ->
            val c = src?.coords
            if (c != null && c.isAttached && me.isAttached) {
                rel = c.localPositionOf(me, Offset.Zero)
                containerSize = c.size
            }
        }
        .drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply { addOutline(outline) }
            clipPath(path) {
                val img = src?.image
                if (img != null && containerSize.width > 0) {
                    val sx = img.width.toFloat() / containerSize.width
                    val sy = img.height.toFloat() / containerSize.height
                    val srcX = (rel.x * sx).roundToInt().coerceIn(0, img.width - 1)
                    val srcY = (rel.y * sy).roundToInt().coerceIn(0, img.height - 1)
                    val srcW = ceil(size.width * sx).toInt().coerceIn(1, img.width - srcX)
                    val srcH = ceil(size.height * sy).toInt().coerceIn(1, img.height - srcY)
                    drawImage(
                        img,
                        srcOffset = IntOffset(srcX, srcY),
                        srcSize = IntSize(srcW, srcH),
                        dstSize = IntSize(ceil(size.width).toInt(), ceil(size.height).toInt()),
                        filterQuality = FilterQuality.High,
                    )
                }
                drawRect(tint)
            }
            if (edge) drawPath(path, Ios.GlassEdge, style = Stroke(width = 1.dp.toPx()))
        }
}

@Composable
fun rememberGlassSource(image: ImageBitmap?): GlassSource =
    remember { GlassSource() }.also { it.image = image }
