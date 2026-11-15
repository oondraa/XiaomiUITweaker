package cz.miuitweak.ioshell.overlay

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.miuitweak.ioshell.MainActivity
import cz.miuitweak.ioshell.cc.ControlCenter
import cz.miuitweak.ioshell.cc.Toggles
import cz.miuitweak.ioshell.data.Prefs
import cz.miuitweak.ioshell.data.QuickAction
import cz.miuitweak.ioshell.data.Wallpaper
import cz.miuitweak.ioshell.data.WallpaperImages
import cz.miuitweak.ioshell.lock.LockScreen
import cz.miuitweak.ioshell.lock.Torch
import cz.miuitweak.ioshell.lock.LockActivity
import cz.miuitweak.ioshell.service.LockService
import cz.miuitweak.ioshell.notif.NotifRepo
import cz.miuitweak.ioshell.notif.ui.NotifCallbacks
import androidx.compose.ui.platform.AndroidUiDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Pruhy pres stavovy radek (jako na iPhonu):
 *  - stazeni z leve/prostredni casti  -> Centrum oznameni (= zamykacka s notifikacemi)
 *  - stazeni z praveho rohu            -> Ovladaci centrum
 * Bezi jako sluzba pristupnosti, protoze jen ta smi kreslit pres stavovy radek a zachytit tam dotyk.
 */
class EdgeService : AccessibilityService() {

    enum class Kind { NC, CC }

    companion object {
        @Volatile
        var instance: EdgeService? = null
            private set
    }

    private lateinit var wm: WindowManager
    private lateinit var km: KeyguardManager
    private lateinit var toggles: Toggles
    // AndroidUiDispatcher.Main ma v sobe frame clock -> animace mimo Compose nepadaji.
    // Handler chyb: kdyby cokoliv v panelu spadlo, panel se zavre a nezustane viset pres obrazovku.
    private val scope = CoroutineScope(
        SupervisorJob() + AndroidUiDispatcher.Main + CoroutineExceptionHandler { _, e ->
            android.util.Log.e("EdgeService", "panel error", e)
            panel?.remove()
        },
    )
    private val strips = mutableListOf<View>()
    private var panel: Panel? = null

    private var cover: LockCover? = null
    private var banner: BannerHost? = null
    private var airpodsPopup: cz.miuitweak.ioshell.airpods.AirPodsPopup? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Hlidani displeje bezi tady (ne ve sluzbe na popredi): sluzbu pristupnosti drzi system nazivu sam,
     * takze neni potreba trvala notifikace (ten ctverecek ve stavovem radku).
     */
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val enabled = Prefs.current.enabled && Settings.canDrawOverlays(context)
            android.util.Log.d("EdgeService", "screen ${intent.action} enabled=$enabled cover=${cover != null}")
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    panel?.remove()
                    banner?.remove()
                    airpodsPopup?.remove()
                    cz.miuitweak.ioshell.airpods.AirPods.stop(context)
                    if (!enabled) return
                    cover?.show()
                    // chvili pockat, az se MIUI zamkne -> LockActivity pak lezi nad zamykackou
                    handler.removeCallbacksAndMessages(null)
                    handler.postDelayed({ LockService.showLock(context) }, 350)
                }
                Intent.ACTION_SCREEN_ON -> {
                    cz.miuitweak.ioshell.airpods.AirPods.start(context)
                    cz.miuitweak.ioshell.media.MediaRepo.refresh()
                    if (enabled && !LockActivity.active && km.isKeyguardLocked) LockService.showLock(context)
                    cover?.hideLater()
                }
            }
        }
    }

    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(WindowManager::class.java)
        km = getSystemService(KeyguardManager::class.java)
        toggles = Toggles(this)
        Torch.init(this)
        cover = LockCover(this, wm, statusBarHeight()).also { if (Prefs.current.enabled) it.attach() }
        addStrips()
        cz.miuitweak.ioshell.media.MediaRepo.start(this)
        banner = BannerHost(this, wm, statusBarHeight())
        scope.launch {
            NotifRepo.banners.collect { n ->
                val pm = getSystemService(android.os.PowerManager::class.java)
                val ok = pm.isInteractive && !km.isKeyguardLocked && panel == null
                android.util.Log.d("Banner", "show? $ok interactive=${pm.isInteractive} locked=${km.isKeyguardLocked} panel=${panel != null}")
                if (ok) banner?.show(n)
            }
        }
        if (Prefs.current.iosBanners) Banners.applySystemHeadsUp(this, false)
        // AirPods: Bluetooth sken jen pri rozsvicenem displeji (setri baterii)
        airpodsPopup = cz.miuitweak.ioshell.airpods.AirPodsPopup(this, wm)
        cz.miuitweak.ioshell.airpods.AirPods.start(this)
        scope.launch {
            cz.miuitweak.ioshell.airpods.AirPods.popups.collect { s ->
                val pm = getSystemService(android.os.PowerManager::class.java)
                if (Prefs.current.airpodsPopup && pm.isInteractive && panel == null) airpodsPopup?.show(s)
            }
        }
        ContextCompat.registerReceiver(
            this, screenOff,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // tuhle praci ted dela sluzba pristupnosti -> sluzbu s trvalou notifikaci vypnout
        LockService.stop(this)
    }

    /**
     * MIUI po requestDismissKeyguard ukaze svoji zamykacku s hodinami misto PINu.
     * Udelame za uzivatele "prejeti nahoru" -> rovnou se ukaze PIN / otisk.
     */
    fun revealBouncer() {
        handler.postDelayed({
            if (!km.isKeyguardLocked) return@postDelayed
            val b = wm.currentWindowMetrics.bounds
            val path = android.graphics.Path().apply {
                moveTo(b.width() / 2f, b.height() * 0.80f)
                lineTo(b.width() / 2f, b.height() * 0.35f)
            }
            runCatching {
                dispatchGesture(
                    android.accessibilityservice.GestureDescription.Builder()
                        .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 180))
                        .build(),
                    null, null,
                )
            }
        }, 320)
    }

    /** Odemceno: kopie zamykacky odjede nahoru (LockActivity se zavre bez animace). */
    fun playUnlockExit(fromPx: Float): Boolean {
        val c = cover ?: return false
        c.playExit(fromPx)
        return true
    }

    /** LockActivity se po rozsviceni vykreslila -> kopie uz neni potreba. */
    fun lockDrawn() {
        // LockActivity se "obnovi" i pri zhasnutem displeji (hned po spusteni) – to se nepocita
        if (getSystemService(android.os.PowerManager::class.java).isInteractive) cover?.hide()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Pojistka: zmenilo se okno na obrazovce a nase zamykacka neni navrchu -> kopie nesmi nic zakryvat
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !LockActivity.resumedNow &&
            getSystemService(android.os.PowerManager::class.java).isInteractive
        ) cover?.hide()
    }
    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        removeStrips()
        addStrips()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        handler.removeCallbacksAndMessages(null)
        cover?.detach()
        banner?.remove()
        airpodsPopup?.remove()
        cz.miuitweak.ioshell.airpods.AirPods.stop(this)
        runCatching { unregisterReceiver(screenOff) }
        panel?.remove()
        removeStrips()
        scope.cancel()
        super.onDestroy()
    }

    // ---------------- pruhy nahore ----------------

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else (28 * resources.displayMetrics.density).toInt()
    }

    private fun addStrips() {
        val w = wm.currentWindowMetrics.bounds.width()
        val h = statusBarHeight()
        val ncW = (w * 0.62f).toInt()
        addStrip(Kind.NC, Gravity.START, ncW, h)
        addStrip(Kind.CC, Gravity.END, w - ncW, h)
    }

    private fun addStrip(kind: Kind, gravity: Int, width: Int, height: Int) {
        val v = View(this)
        v.setOnTouchListener(EdgeTouch(kind))
        val lp = WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = Gravity.TOP or gravity
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
        }
        runCatching { wm.addView(v, lp) }.onSuccess { strips += v }
    }

    private fun removeStrips() {
        strips.forEach { runCatching { wm.removeView(it) } }
        strips.clear()
    }

    private inner class EdgeTouch(private val kind: Kind) : View.OnTouchListener {
        private var startY = 0f
        private var opened = false
        private var vt: VelocityTracker? = null
        private val slop = ViewConfiguration.get(this@EdgeService).scaledTouchSlop

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            try {
                handle(e)
            } catch (t: Throwable) {
                android.util.Log.e("EdgeService", "touch error", t)
                panel?.remove()
            }
            return true
        }

        private fun handle(e: MotionEvent) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = e.rawY
                    opened = false
                    vt?.recycle()
                    vt = VelocityTracker.obtain().also { it.addMovement(e) }
                }
                MotionEvent.ACTION_MOVE -> {
                    vt?.addMovement(e)
                    val dy = e.rawY - startY
                    if (!opened && dy > slop) {
                        // na zamcene obrazovce resi notifikace nase zamykacka
                        if (kind == Kind.NC && km.isKeyguardLocked) return
                        // zamceno a nase zamykacka neni navrchu = MIUI zamykacka/PIN -> nic nepridavat pres ni
                        if (km.isKeyguardLocked && !LockActivity.resumedNow) return
                        open(kind)
                        opened = true
                    }
                    if (opened) panel?.drag(dy)
                    lastDragAt = android.os.SystemClock.uptimeMillis()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val t = vt
                    var vy = 0f
                    if (t != null) {
                        t.addMovement(e)
                        t.computeCurrentVelocity(1000)
                        vy = t.yVelocity
                        t.recycle()
                    }
                    vt = null
                    if (opened) panel?.release(vy)
                }
            }
        }
    }

    private var lastDragAt = 0L

    /** Vlastni handler jen na odstranovani oken – NIKDY se nemaze (driv to pri zhasnuti smazalo i odstraneni panelu). */
    private val removeHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun open(kind: Kind) {
        panel?.remove()
        panel = Panel(kind)
        lastDragAt = android.os.SystemClock.uptimeMillis()
        watchdog()
    }

    /** Pojistka 2: panel napul otevreny bez pohybu prstu dele nez 1,5 s se zavre. */
    private fun watchdog() {
        removeHandler.postDelayed(object : Runnable {
            override fun run() {
                val p = panel ?: return
                val idle = android.os.SystemClock.uptimeMillis() - lastDragAt
                if (!p.settled && idle > 1500) {
                    p.remove()
                    return
                }
                removeHandler.postDelayed(this, 500)
            }
        }, 500)
    }

    // ---------------- panel pres celou obrazovku ----------------

    private inner class Panel(val kind: Kind) {
        private val owner = OverlayOwner()
        private val height = wm.currentWindowMetrics.bounds.height().toFloat()
        val progress = Animatable(0f)
        private var removed = false
        /** uplne otevreny = teprve ted smi brat dotyky */
        var settled = false
            private set
        private lateinit var lp: WindowManager.LayoutParams

        private val container = object : FrameLayout(this@EdgeService) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) close()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }

        init {
            val compose = ComposeView(this@EdgeService).apply {
                setContent {
                    when (kind) {
                        Kind.NC -> Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationY = -height * (1f - progress.value) },
                        ) { NotificationCenter(onClose = ::remove) }
                        Kind.CC -> {
                            val torch by Torch.on.collectAsStateWithLifecycle()
                            ControlCenter(
                                toggles, { progress.value }, onClose = ::close, torchOn = torch,
                                onGlobalAction = { action -> handler.postDelayed({ performGlobalAction(action) }, 450) },
                            )
                        }
                    }
                }
            }
            container.addView(compose)
            owner.attach(container)

            // Pojistka 1: dokud neni panel uplne otevreny, nebere dotyky ani fokus
            // (tah prstem stejne patri pruhu nahore). Neviditelny panel tak nikdy nic nezablokuje.
            lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
                if (kind == Kind.CC && wm.isCrossWindowBlurEnabled) {
                    flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                    blurBehindRadius = 70
                }
            }
            wm.addView(container, lp)
        }

        private val span get() = if (kind == Kind.NC) height else height * 0.35f

        fun drag(dy: Float) {
            scope.launch { progress.snapTo((dy / span).coerceIn(0f, 1f)) }
        }

        fun release(vy: Float) {
            scope.launch {
                if (progress.value > 0.3f || vy > 1200f) {
                    progress.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 380f))
                    if (!removed) {
                        settled = true
                        lp.flags = lp.flags and
                            (WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE).inv()
                        runCatching { wm.updateViewLayout(container, lp) }
                    }
                } else {
                    progress.animateTo(0f, tween(180))
                    remove()
                }
            }
        }

        fun close() {
            scope.launch {
                progress.animateTo(0f, tween(220))
                remove()
            }
        }

        fun remove() {
            if (removed) return
            removed = true
            if (panel === this) panel = null
            // nejdriv hned prestat brat dotyky, samotne odstraneni az po dokonceni dotyku (jinak Compose spadne)
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            runCatching { wm.updateViewLayout(container, lp) }
            removeHandler.post {
                runCatching { wm.removeViewImmediate(container) }
                owner.destroy()
            }
        }
    }

    // ---------------- obsah Centra oznameni ----------------

    @Composable
    private fun NotificationCenter(onClose: () -> Unit) {
        val settings by Prefs.settings.collectAsStateWithLifecycle()
        val notifs by NotifRepo.items.collectAsStateWithLifecycle()
        val torch by Torch.on.collectAsStateWithLifecycle()
        var wallpaper by remember { mutableStateOf<WallpaperImages?>(null) }
        var exiting by remember { mutableStateOf(false) }
        val bounds = wm.currentWindowMetrics.bounds
        LaunchedEffect(settings.wallpaperVersion) {
            wallpaper = Wallpaper.load(this@EdgeService, bounds.width(), bounds.height(), settings.wallpaperVersion)
        }
        fun launch(i: Intent) {
            runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            onClose()
        }
        val cb = remember {
            NotifCallbacks(
                onOpen = { n ->
                    NotifRepo.open(this, n)
                    onClose()
                },
                onClear = { NotifRepo.dismiss(it) },
                onClearAll = { NotifRepo.dismissAll(it) },
                onMute = { n, ms -> NotifRepo.mute(n.pkg, ms) },
                onSettings = { n ->
                    launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, n.pkg))
                },
                onAction = { _, a, reply -> NotifRepo.runAction(this, a, reply) },
            )
        }
        LockScreen(
            settings = settings,
            wallpaper = wallpaper,
            locked = false,
            torchOn = torch,
            exiting = exiting,
            initialExpanded = true,
            showUnlockHint = false,
            topInset = with(androidx.compose.ui.platform.LocalDensity.current) { statusBarHeight().toDp() },
            onSwipeUp = { exiting = true },
            onExited = onClose,
            onSwipeLeft = { launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) },
            onAction = { a ->
                when (a) {
                    QuickAction.FLASHLIGHT -> Torch.toggle(this)
                    QuickAction.CAMERA -> launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                    QuickAction.NONE -> {}
                }
            },
            onEdit = { launch(Intent(this, MainActivity::class.java)) },
            media = cz.miuitweak.ioshell.media.MediaRepo.media.collectAsStateWithLifecycle().value,
            notifs = notifs,
            notifCallbacks = cb,
            onModeChange = { m -> Prefs.update { it.copy(notifMode = m) } },
        )
    }
}
