package cz.miuitweak.ioshell.lock

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import cz.miuitweak.ioshell.notif.NotifRepo
import cz.miuitweak.ioshell.notif.ui.NotifCallbacks
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.miuitweak.ioshell.MainActivity
import cz.miuitweak.ioshell.data.Prefs
import cz.miuitweak.ioshell.data.QuickAction
import cz.miuitweak.ioshell.data.Wallpaper
import cz.miuitweak.ioshell.data.WallpaperImages

/**
 * Nase zamykacka. Lezi NAD systemovou zamykackou MIUI (ta zustava kvuli PIN/otisku).
 * Odemknuti otiskem ji nezavre (jako Face ID) – zavre se az prejetim nahoru.
 */
class LockActivity : ComponentActivity() {

    private lateinit var km: KeyguardManager
    private var locked by mutableStateOf(true)
    private var exiting by mutableStateOf(false)
    private var resetKey by mutableIntStateOf(0)
    private var bounceKey by mutableIntStateOf(0)
    private var wallpaper by mutableStateOf<WallpaperImages?>(null)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_USER_PRESENT -> locked = false
                Intent.ACTION_SCREEN_OFF -> {
                    setOpaque(true)
                    exiting = false
                    resetKey++
                    locked = km.isKeyguardLocked || km.isDeviceSecure
                }
                Intent.ACTION_SCREEN_ON -> locked = km.isKeyguardLocked
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        active = true
        km = getSystemService(KeyguardManager::class.java)
        locked = km.isKeyguardLocked
        Torch.init(this)

        // Jen ukazat pres zamykacku. NErozsvecovat displej – startujeme pri zhasnuti displeje.
        setShowWhenLocked(true)
        // Okno je v motivu pruhledne (aby pri tahu nahoru byla videt plocha pod nim),
        // ale bezne ho drzime nepruhledne – jen nepruhledne okno zakryje zamykacku MIUI.
        setOpaque(true)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onBackPressedDispatcher.addCallback(this) { /* zpet nic nedela, jako na zamykacce */ }

        ContextCompat.registerReceiver(
            this, receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val bounds = windowManager.currentWindowMetrics.bounds
        val notifCallbacks = NotifCallbacks(
            onOpen = { n ->
                withUnlock {
                    NotifRepo.open(this, n)
                    exiting = true
                }
            },
            onClear = { NotifRepo.dismiss(it) },
            onClearAll = { NotifRepo.dismissAll(it) },
            onMute = { n, ms -> NotifRepo.mute(n.pkg, ms) },
            onSettings = { n ->
                withUnlock {
                    runCatching {
                        startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, n.pkg)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    exiting = true
                }
            },
            onAction = { _, a, reply -> NotifRepo.runAction(this, a, reply) },
        )
        setContent {
            val settings by Prefs.settings.collectAsStateWithLifecycle()
            val torchOn by Torch.on.collectAsStateWithLifecycle()
            val notifs by NotifRepo.items.collectAsStateWithLifecycle()
            LaunchedEffect(settings.wallpaperVersion) {
                wallpaper = Wallpaper.load(this@LockActivity, bounds.width(), bounds.height(), settings.wallpaperVersion)
            }
            LockScreen(
                settings = settings,
                wallpaper = wallpaper,
                locked = locked,
                torchOn = torchOn,
                exiting = exiting,
                resetKey = resetKey,
                onSwipeUp = ::unlock,
                bounceKey = bounceKey,
                onUnlockDragStart = {
                    // odemceno (otiskem) -> pod zamykackou ukazat skutecnou plochu / appku
                    if (!km.isKeyguardLocked) setOpaque(false)
                },
                media = cz.miuitweak.ioshell.media.MediaRepo.media.collectAsStateWithLifecycle().value,
                notifs = notifs,
                notifCallbacks = notifCallbacks,
                onModeChange = { m -> Prefs.update { it.copy(notifMode = m) } },
                exitHandoff = if (cz.miuitweak.ioshell.overlay.EdgeService.instance == null) null else { off ->
                    NotifRepo.markAllSeen()
                    if (cz.miuitweak.ioshell.overlay.EdgeService.instance?.playUnlockExit(off) == true) {
                        // kopie je nahore -> zavrit uplne bez animace
                        window.setWindowAnimations(0)
                        finishAndRemoveTask()
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    } else {
                        finishAndRemoveTask()
                    }
                },
                onExited = {
                    // co bylo videt na zamykacce, jde po odemceni do historie (jako iOS)
                    NotifRepo.markAllSeen()
                    finishAndRemoveTask()
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                },
                onSwipeLeft = ::openCamera,
                onAction = { a ->
                    when (a) {
                        QuickAction.FLASHLIGHT -> Torch.toggle(this)
                        QuickAction.CAMERA -> openCamera()
                        QuickAction.NONE -> {}
                    }
                },
                onEdit = { withUnlock { openSettings() } },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setOpaque(true)
        exiting = false
        resetKey++
        locked = km.isKeyguardLocked
    }

    override fun onPause() {
        resumedNow = false
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        resumedNow = true
        // az se opravdu vykresli (2 snimky), schovat "kopii" z EdgeService
        window.decorView.postOnAnimation {
            window.decorView.postOnAnimation { cz.miuitweak.ioshell.overlay.EdgeService.instance?.lockDrawn() }
        }
        locked = km.isKeyguardLocked
    }

    override fun onDestroy() {
        active = false
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    companion object {
        /** Bezi instance zamykacky? (pro pojistku pri rozsviceni v LockService) */
        @Volatile
        var active = false
            private set

        /** nase zamykacka je prave navrchu (ne MIUI zamykacka / PIN) */
        @Volatile
        var resumedNow = false
            private set
    }

    private fun setOpaque(opaque: Boolean) {
        runCatching { setTranslucent(!opaque) }
    }

    private var dismissRequestedAt = 0L

    private fun unlock() {
        // nase "prejeti" pro PIN nesmi znovu spustit odemykani
        if (android.os.SystemClock.uptimeMillis() - dismissRequestedAt < 2000) return
        if (!km.isKeyguardLocked) {
            locked = false
            setOpaque(false)
            exiting = true
            return
        }
        // Rychle prejeti hned po rozsviceni: otisk jeste nedobehl. Chvili pockat (jako Face ID),
        // MIUI zamykacku s PINem ukazat az kdyz se opravdu neodemkne.
        lifecycleScope.launch {
            repeat(14) {
                delay(50)
                if (!km.isKeyguardLocked) {
                    locked = false
                    setOpaque(false)
                    exiting = true
                    return@launch
                }
            }
            dismissRequestedAt = android.os.SystemClock.uptimeMillis()
            // PIN (MIUI) nesmi nic zakryvat
            cz.miuitweak.ioshell.overlay.EdgeService.instance?.lockDrawn()
            km.requestDismissKeyguard(this@LockActivity, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    locked = false
                    exiting = true
                }

                override fun onDismissCancelled() {
                    bounceKey++
                }

                override fun onDismissError() {
                    bounceKey++
                }
            })
        }
    }

    /** Kdyz je zamceno, vyzada PIN/otisk (systemovy dialog MIUI) a pak provede akci. */
    private fun withUnlock(action: () -> Unit) {
        if (!km.isKeyguardLocked) {
            locked = false
            action()
            return
        }
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                locked = false
                action()
            }
        })
    }

    private fun openCamera() {
        val secure = km.isKeyguardLocked
        val i = Intent(
            if (secure) MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE
            else MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(i) }
    }

    private fun openSettings() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finishAndRemoveTask()
    }
}
