package cz.miuitweak.ioshell.cc

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.wifi.WifiManager
import android.provider.Settings
import cz.miuitweak.ioshell.lock.Torch
import cz.miuitweak.ioshell.notif.NotifListener
import kotlinx.coroutines.delay

/** Okamzity stav prepinacu v ovladacim centru. */
data class CcState(
    val wifi: Boolean = false,
    val bluetooth: Boolean = false,
    val data: Boolean = false,
    val airplane: Boolean = false,
    val rotationLock: Boolean = false,
    val dnd: Boolean = false,
    /** 0..1 */
    val brightness: Float = 0.5f,
    val volume: Float = 0.5f,
    val shizuku: Boolean = false,
    val powerSave: Boolean = false,
    val location: Boolean = false,
    val dark: Boolean = false,
    /** nazev Wi-Fi site (kdyz ho Android prozradi), jinak null */
    val ssid: String? = null,
)

data class NowPlaying(val title: String, val artist: String, val playing: Boolean)

class Toggles(private val ctx: Context) {
    private val cr = ctx.contentResolver
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val wifiMgr = ctx.getSystemService(WifiManager::class.java)
    private val nm = ctx.getSystemService(NotificationManager::class.java)

    private val maxBrightness: Int by lazy {
        val id = Resources.getSystem().getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android")
        (if (id != 0) runCatching { Resources.getSystem().getInteger(id) }.getOrNull() else null)
            ?.takeIf { it > 0 } ?: 255
    }

    private fun global(key: String, def: Int = 0) = Settings.Global.getInt(cr, key, def)

    fun read(): CcState {
        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val bright = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, maxBrightness / 2)
        // posuvnik je "vnimany" jas (jako v Androidu/iOS), ne linearni hodnota podsviceni
        val brightPos = kotlin.math.sqrt(bright.toFloat() / maxBrightness)
        return CcState(
            wifi = wifiMgr.isWifiEnabled,
            bluetooth = global("bluetooth_on") != 0,
            data = global("mobile_data", 1) != 0,
            airplane = global(Settings.Global.AIRPLANE_MODE_ON) != 0,
            rotationLock = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1) == 0,
            dnd = nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL,
            brightness = brightPos.coerceIn(0f, 1f),
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol,
            shizuku = ShizukuShell.granted,
            powerSave = ctx.getSystemService(android.os.PowerManager::class.java).isPowerSaveMode,
            location = ctx.getSystemService(android.location.LocationManager::class.java).isLocationEnabled,
            dark = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES,
            ssid = runCatching {
                @Suppress("DEPRECATION")
                wifiMgr.connectionInfo?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
            }.getOrNull(),
        )
    }

    private fun onOff(b: Boolean) = if (b) "enable" else "disable"

    /** Kazdy prepinac: pres Shizuku naostro, jinak aspon otevre systemovy panel. */
    suspend fun setWifi(on: Boolean) {
        if (!ShizukuShell.run("svc wifi ${onOff(on)}")) openPanel(Settings.Panel.ACTION_WIFI)
    }

    suspend fun setBluetooth(on: Boolean) {
        if (!ShizukuShell.run("svc bluetooth ${onOff(on)}")) openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
    }

    suspend fun setData(on: Boolean) {
        if (!ShizukuShell.run("svc data ${onOff(on)}")) openPanel(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
    }

    suspend fun setAirplane(on: Boolean) {
        if (!ShizukuShell.run("cmd connectivity airplane-mode ${onOff(on)}")) {
            openSettings(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        }
    }

    suspend fun setRotationLock(locked: Boolean) {
        val v = if (locked) 0 else 1
        if (!ShizukuShell.run("settings put system accelerometer_rotation $v") && Settings.System.canWrite(ctx)) {
            Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, v)
        }
    }

    suspend fun setDnd(on: Boolean) {
        if (ShizukuShell.run("cmd notification set_dnd ${if (on) "priority" else "off"}")) return
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(
                if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL,
            )
        } else {
            openSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        }
    }

    suspend fun setPowerSave(on: Boolean) {
        if (!ShizukuShell.run("settings put global low_power ${if (on) 1 else 0}")) {
            openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        }
    }

    suspend fun setLocation(on: Boolean) {
        if (!ShizukuShell.run("cmd location set-location-enabled $on")) openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    }

    suspend fun setDark(on: Boolean) {
        if (!ShizukuShell.run("cmd uimode night ${if (on) "yes" else "no"}")) openSettings(Settings.ACTION_DISPLAY_SETTINGS)
    }

    private var lastBrightness = -1

    suspend fun setBrightness(f: Float) {
        val p = f.coerceIn(0.05f, 1f)
        val v = (p * p * maxBrightness).toInt().coerceAtLeast(1)
        if (v == lastBrightness) return
        lastBrightness = v
        val manual = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        if (Settings.System.canWrite(ctx)) {
            // s automatickym jasem MIUI rucni hodnotu ignoruje -> pri posunu prepnout na rucni
            if (Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, manual) != manual) {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, manual)
            }
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, v)
        } else {
            ShizukuShell.run("settings put system screen_brightness_mode 0; settings put system screen_brightness $v")
        }
    }

    fun setVolume(f: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (f.coerceIn(0f, 1f) * max).toInt(), 0)
    }

    fun toggleTorch() = Torch.toggle(ctx)

    // ---------- hudba ----------
    private fun controller(): MediaController? = runCatching {
        ctx.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(ctx, NotifListener::class.java))
            .firstOrNull()
    }.getOrNull()

    fun nowPlaying(): NowPlaying? {
        val c = controller() ?: return null
        val md = c.metadata ?: return null
        val title = md.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: return null
        val artist = md.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        return NowPlaying(title, artist, c.playbackState?.state == PlaybackState.STATE_PLAYING)
    }

    fun playPause() {
        val c = controller() ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() = controller()?.transportControls?.skipToNext()
    fun previous() = controller()?.transportControls?.skipToPrevious()

    // ---------- spousteni ----------
    fun openPanel(action: String) = openSettings(action)

    fun openSettings(action: String) {
        runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun launch(intent: Intent) {
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun launchPackage(pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        launch(i)
        return true
    }

    /** Po prepnuti chvili trva, nez se zmena projevi v nastaveni. */
    suspend fun settle() = delay(700)
}
