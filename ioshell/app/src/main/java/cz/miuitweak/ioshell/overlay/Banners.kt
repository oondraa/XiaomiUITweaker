package cz.miuitweak.ioshell.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import cz.miuitweak.ioshell.notif.Notif
import cz.miuitweak.ioshell.notif.NotifRepo
import cz.miuitweak.ioshell.notif.ui.CardBody
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * iOS banner pro novou notifikaci pri pouzivani telefonu.
 * Systemove "vyskakovaci" notifikace se pritom vypnou (globalni nastaveni Androidu),
 * k tomu appka potrebuje WRITE_SECURE_SETTINGS (povoli instalacni skript pres ADB).
 */
object Banners {
    fun applySystemHeadsUp(ctx: Context, enabled: Boolean) {
        runCatching {
            Settings.Global.putInt(ctx.contentResolver, "heads_up_notifications_enabled", if (enabled) 1 else 0)
        }.onFailure { android.util.Log.w("Banners", "heads-up toggle failed (WRITE_SECURE_SETTINGS?)", it) }
    }
}

class BannerHost(private val ctx: Context, private val wm: WindowManager, private val statusBarPx: Int) {
    private var view: FrameLayout? = null
    private var owner: OverlayOwner? = null
    private var current by mutableStateOf<Notif?>(null)
    private var token by mutableStateOf(0)
    private val handler = Handler(Looper.getMainLooper())

    fun show(n: Notif) {
        current = n
        token++
        if (view == null) attach()
    }

    fun remove() {
        handler.removeCallbacksAndMessages(null)
        view?.let { runCatching { wm.removeViewImmediate(it) } }
        owner?.destroy()
        view = null
        owner = null
        current = null
    }

    private fun attach() {
        val o = OverlayOwner()
        val frame = FrameLayout(ctx)
        frame.addView(ComposeView(ctx).apply { setContent { this@BannerHost.Content() } })
        o.attach(frame)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = statusBarPx
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        runCatching { wm.addView(frame, lp) }.onSuccess {
            view = frame
            owner = o
        }.onFailure { o.destroy() }
    }

    @androidx.compose.runtime.Composable
    private fun Content() {
        val n = current ?: return
        val scope = rememberCoroutineScope()
        val y = remember { Animatable(-300f) }
        fun dismiss() {
            scope.launch {
                y.animateTo(-400f, tween(200))
                handler.post { remove() }
            }
        }
        LaunchedEffect(token) {
            y.snapTo(-300f)
            y.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 380f))
            delay(5000)
            dismiss()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .graphicsLayer { translationY = y.value }
                .pointerInput(n.key) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -20f) dismiss() else scope.launch { y.animateTo(0f, spring()) } },
                    ) { ch, dy ->
                        ch.consume()
                        total += dy
                        scope.launch { y.snapTo((y.value + dy).coerceAtMost(30f)) }
                    }
                },
        ) {
            CardBody(
                n,
                maxLines = 3,
                bg = Color(0xFF2C2C2E).copy(alpha = 0.97f),
                modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) {
                    NotifRepo.open(ctx, n)
                    handler.post { remove() }
                },
            )
        }
    }
}
