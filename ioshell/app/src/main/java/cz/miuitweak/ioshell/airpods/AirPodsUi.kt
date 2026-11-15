package cz.miuitweak.ioshell.airpods

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.miuitweak.ioshell.overlay.OverlayOwner
import cz.miuitweak.ioshell.ui.Ios
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun ringColor(p: Int, charging: Boolean) = when {
    charging -> Ios.Green
    p <= 20 -> Ios.Red
    else -> Ios.Green
}

/** Krouzek s baterii (jako widget Baterie / okno AirPods na iOS). */
@Composable
fun BatteryRing(icon: ImageVector, pct: Int?, charging: Boolean, label: String, size: Dp = 64.dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val sw = 5.dp.toPx()
                val inset = sw / 2
                val arc = Size(this.size.width - sw, this.size.height - sw)
                drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
                if (pct != null) {
                    drawArc(
                        ringColor(pct, charging), -90f, 360f * pct / 100f, false, Offset(inset, inset), arc,
                        style = Stroke(sw, cap = StrokeCap.Round),
                    )
                }
            }
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.42f))
            if (charging) {
                Icon(
                    Icons.Rounded.Bolt, null, tint = Ios.Green,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .background(Color(0xFF1C1C1E), CircleShape),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(pct?.let { "$it %" } ?: "–", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = Ios.TextSecondary, fontSize = 12.sp)
    }
}

/** Radek baterie AirPods pod hodinami na zamykacce. */
@Composable
fun AirPodsWidget(s: AirPodsState, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.25f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Headphones, null, tint = Color.White, modifier = Modifier.size(16.dp))
        listOfNotNull(
            s.combined?.takeIf { s.left == null && s.right == null }?.let { "$it %" },
            s.left?.let { "L $it %" + if (s.chargingLeft) "⚡" else "" },
            s.right?.let { "P $it %" + if (s.chargingRight) "⚡" else "" },
            s.case?.let { "Pouzdro $it %" + if (s.chargingCase) "⚡" else "" },
        ).forEach { Text(it, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
    }
}

/** Okno po otevreni pouzdra (dole, jako iPhone). */
class AirPodsPopup(private val ctx: Context, private val wm: WindowManager) {
    private var view: FrameLayout? = null
    private var owner: OverlayOwner? = null
    private var state by mutableStateOf<AirPodsState?>(null)
    private var token by mutableIntStateOf(0)
    private val handler = Handler(Looper.getMainLooper())

    fun show(s: AirPodsState) {
        state = s
        token++
        if (view != null) return
        val o = OverlayOwner()
        val frame = FrameLayout(ctx)
        frame.addView(ComposeView(ctx).apply { setContent { this@AirPodsPopup.Content() } })
        o.attach(frame)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            y = (20 * ctx.resources.displayMetrics.density).toInt()
        }
        runCatching { wm.addView(frame, lp) }.onSuccess {
            view = frame
            owner = o
        }.onFailure { o.destroy() }
    }

    fun remove() {
        handler.removeCallbacksAndMessages(null)
        view?.let { runCatching { wm.removeViewImmediate(it) } }
        owner?.destroy()
        view = null
        owner = null
    }

    @Composable
    private fun Content() {
        val s = state ?: return
        val live by AirPods.state.collectAsStateLive(s)
        val scope = rememberCoroutineScope()
        val y = remember { Animatable(600f) }
        fun dismiss() {
            scope.launch {
                y.animateTo(700f, tween(220))
                handler.post { remove() }
            }
        }
        LaunchedEffect(token) {
            y.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 300f))
            delay(8000)
            dismiss()
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .graphicsLayer { translationY = y.value }
                .clip(RoundedCornerShape(38.dp))
                .background(Color(0xFF1C1C1E).copy(alpha = 0.98f))
                .clickable { dismiss() }
                .padding(top = 20.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(live.model, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                if (live.left == null && live.right == null) {
                    // jen celkova baterie z Bluetooth (pouzdro bylo zavrene / bez rozpisu)
                    BatteryRing(Icons.Rounded.Headphones, live.combined, false, "Sluchátka")
                } else {
                    BatteryRing(Icons.Rounded.Headphones, live.left, live.chargingLeft, "Levé")
                    BatteryRing(Icons.Rounded.Headphones, live.right, live.chargingRight, "Pravé")
                }
                if (live.case != null) BatteryRing(Icons.Rounded.Inventory2, live.case, live.chargingCase, "Pouzdro")
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Hotovo",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .width(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ios.Blue)
                    .clickable { dismiss() }
                    .padding(vertical = 14.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** StateFlow -> stav s vychozi hodnotou, kdyz zrovna nic neprislo. */
@Composable
private fun kotlinx.coroutines.flow.StateFlow<AirPodsState?>.collectAsStateLive(def: AirPodsState) =
    androidx.compose.runtime.produceState(def, this) { collect { if (it != null) value = it } }
