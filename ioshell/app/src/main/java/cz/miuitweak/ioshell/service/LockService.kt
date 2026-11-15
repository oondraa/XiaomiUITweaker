package cz.miuitweak.ioshell.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.provider.Settings
import androidx.core.content.ContextCompat
import cz.miuitweak.ioshell.MainActivity
import cz.miuitweak.ioshell.R
import cz.miuitweak.ioshell.data.Prefs
import cz.miuitweak.ioshell.lock.LockActivity

/**
 * Bezi na popredi (jinak ho MIUI zabije) a pri zhasnuti displeje pripravi nasi zamykacku,
 * takze po rozsviceni uz je nahore a systemova zamykacka MIUI neprobleskne.
 */
class LockService : Service() {

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Pojistka: displej se rozsvitil (napr. notifikaci) a nase zamykacka nebezi
            // (MIUI ji mezitim zavrelo / nenechalo spustit) -> spustit hned.
            if (intent.action == Intent.ACTION_SCREEN_ON && !LockActivity.active &&
                getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
            ) {
                showLock(context)
            }
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                // chvili pockat, az se MIUI zamkne -> nase okno pak lezi nad zamykackou uz pred rozsvicenim
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({ showLock(context) }, 350)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Zamykačka běží", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("XiaomiUITweaker")
            .setContentText("Zamykačka je zapnutá")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "lock_service"

        fun showLock(ctx: Context) {
            if (!Prefs.current.enabled) return
            // spusteni aktivity z pozadi povoli Android jen s opravnenim "zobrazeni pres ostatni aplikace"
            if (!Settings.canDrawOverlays(ctx)) return
            ctx.startActivity(
                Intent(ctx, LockActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION,
                ),
            )
        }

        fun start(ctx: Context) {
            if (cz.miuitweak.ioshell.overlay.EdgeService.instance != null) return
            ctx.startForegroundService(Intent(ctx, LockService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, LockService::class.java))
        }
    }
}
