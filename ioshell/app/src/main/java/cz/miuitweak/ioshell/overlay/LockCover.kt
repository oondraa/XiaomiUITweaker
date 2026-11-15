package cz.miuitweak.ioshell.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.miuitweak.ioshell.data.Prefs
import cz.miuitweak.ioshell.data.Wallpaper
import cz.miuitweak.ioshell.data.WallpaperImages
import cz.miuitweak.ioshell.lock.LockScreen
import cz.miuitweak.ioshell.notif.NotifRepo
import cz.miuitweak.ioshell.notif.ui.NotifCallbacks

/**
 * "Kopie" nasi zamykacky jako okno nad vsim, ktere zustava nakreslene i pri zhasnutem displeji.
 * Android pri zhasnuti zahodi obraz LockActivity a po rozsviceni ho ~0,1 s kresli znovu –
 * a v te chvili problikla zamykacka MIUI. Kopie tu mezeru zakryje.
 *
 * Bezpecnost: propousti dotyky (FLAG_NOT_TOUCHABLE) a po rozsviceni zmizi nejpozdeji za [MAX_MS],
 * takze nikdy nemuze nic zablokovat ani zakryt treba prichozi hovor.
 */
class LockCover(private val ctx: Context, private val wm: WindowManager, private val statusBarPx: Int) {

    private var view: FrameLayout? = null
    private var owner: OverlayOwner? = null
    private var lp: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private var visible = false
    private var exitOffset by androidx.compose.runtime.mutableFloatStateOf(0f)
    private var unlockedLook by mutableStateOf(false)
    private var exitAnim: android.animation.ValueAnimator? = null

    /**
     * Odemceni: zviditelnit kopii v poloze, kde ji uzivatel pustil, a nechat ji odjet nahoru.
     * LockActivity se mezitim zavre bez animace (MIUI by jinak prehralo "minimalizaci" appky).
     */
    fun playExit(fromPx: Float) {
        handler.removeCallbacksAndMessages(null)
        exitAnim?.cancel()
        attach()
        unlockedLook = true
        exitOffset = fromPx
        visible = true
        setAlpha(1f)
        val h = wm.currentWindowMetrics.bounds.height().toFloat()
        exitAnim = android.animation.ValueAnimator.ofFloat(fromPx, -h).apply {
            duration = 280
            interpolator = android.view.animation.DecelerateInterpolator(1.6f)
            addUpdateListener { exitOffset = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    hide()
                    exitOffset = 0f
                    unlockedLook = false
                }
            })
            start()
        }
    }

    /**
     * Okno se prida jednou (pri startu sluzby) a zustava NEVIDITELNE (alpha 0), ale uz nakreslene.
     * Pri zhasnuti se jen zviditelni – nemusi se nic kreslit, takze po rozsviceni neprobleskne
     * cerna (prazdne okno) ani MIUI zamykacka.
     */
    fun attach() {
        if (view != null) return
        val bounds = wm.currentWindowMetrics.bounds
        val density = ctx.resources.displayMetrics.density
        val o = OverlayOwner()
        val frame = FrameLayout(ctx)
        frame.addView(
            ComposeView(ctx).apply {
                setContent { CoverContent(bounds.width(), bounds.height(), (bounds.height() / density), statusBarPx / density) }
            },
        )
        o.attach(frame)
        // okno zacina POD stavovym radkem, aby ho nezakryvalo (LockActivity ho taky ukazuje)
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            bounds.height() - statusBarPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = statusBarPx
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            windowAnimations = 0
            alpha = 0f
        }
        runCatching { wm.addView(frame, p) }.onSuccess {
            view = frame
            owner = o
            lp = p
        }.onFailure {
            android.util.Log.e("LockCover", "add failed", it)
            o.destroy()
        }
    }

    private fun setAlpha(a: Float) {
        val v = view ?: return
        val p = lp ?: return
        if (p.alpha == a) return
        p.alpha = a
        runCatching { wm.updateViewLayout(v, p) }
    }

    fun show() {
        handler.removeCallbacksAndMessages(null)
        attach()
        visible = true
        setAlpha(1f)
    }

    /** Po rozsviceni: zmizet nejpozdeji za MAX_MS (driv, az se vykresli skutecna zamykacka). */
    fun hideLater() {
        handler.removeCallbacksAndMessages(null)
        if (visible) handler.postDelayed({ hide() }, MAX_MS)
    }

    fun hide() {
        handler.removeCallbacksAndMessages(null)
        visible = false
        setAlpha(0f)
    }

    fun detach() {
        handler.removeCallbacksAndMessages(null)
        view?.let { runCatching { wm.removeViewImmediate(it) } }
        owner?.destroy()
        view = null
        owner = null
        lp = null
    }

    @Composable
    private fun CoverContent(wPx: Int, hPx: Int, hDp: Float, sbDp: Float) {
        val settings by Prefs.settings.collectAsStateWithLifecycle()
        val notifs by NotifRepo.items.collectAsStateWithLifecycle()
        var wallpaper by remember { mutableStateOf<WallpaperImages?>(null) }
        LaunchedEffect(settings.wallpaperVersion) {
            wallpaper = Wallpaper.load(ctx, wPx, hPx, settings.wallpaperVersion)
        }
        // obsah ma vysku celeho displeje, horni cast (pod stavovym radkem) je oriznuta
        Box(
            Modifier
                .fillMaxSize()
                .wrapContentHeight(Alignment.Bottom, unbounded = true)
                .requiredHeight(hDp.dp)
                .graphicsLayer { translationY = exitOffset },
        ) {
            LockScreen(
                settings = settings,
                wallpaper = wallpaper,
                locked = !unlockedLook,
                torchOn = false,
                interactive = false,
                topInset = sbDp.dp,
                media = cz.miuitweak.ioshell.media.MediaRepo.media.collectAsStateWithLifecycle().value,
                notifs = notifs,
                notifCallbacks = NoCallbacks,
            )
        }
    }

    companion object {
        const val MAX_MS = 1000L
        private val NoCallbacks = NotifCallbacks({}, {}, {}, { _, _ -> }, {}, { _, _, _ -> })
    }
}
