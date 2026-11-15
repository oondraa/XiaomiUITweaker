package cz.miuitweak.ioshell.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Akce pro dve tlacitka dole na zamykacce. */
enum class QuickAction(val label: String) {
    NONE("Nic"),
    FLASHLIGHT("Svítilna"),
    CAMERA("Fotoaparát"),
}

/** Jak se ukazuji nove notifikace na zamykacce (jako iOS: Count / Stack / List). */
enum class NotifMode(val label: String) {
    COUNT("Počet"),
    STACK("Balíček"),
    LIST("Seznam"),
}

data class LockSettings(
    val enabled: Boolean = false,
    val clockFont: Int = 0,
    val clockWeight: Int = 600,
    val clockColor: Long = 0xFFFFFFFF,
    val clockAlpha: Float = 1f,
    val clockScale: Float = 1f,
    val leftButton: QuickAction = QuickAction.FLASHLIGHT,
    val rightButton: QuickAction = QuickAction.CAMERA,
    val notifMode: NotifMode = NotifMode.STACK,
    /** vybrane a serazene kulate dlazdice ovladaciho centra */
    val ccTiles: List<cz.miuitweak.ioshell.cc.CcTile> = cz.miuitweak.ioshell.cc.CcTile.DEFAULT,
    /** iOS bannery misto systemovych vyskakovacich notifikaci */
    val iosBanners: Boolean = false,
    /** okno s baterii AirPods po otevreni pouzdra */
    val airpodsPopup: Boolean = true,
    /** Zvysi se pri kazde zmene tapety -> znovu nacist obrazek. */
    val wallpaperVersion: Long = 0,
)

object Prefs {
    private lateinit var sp: SharedPreferences
    private val _settings = MutableStateFlow(LockSettings())
    val settings: StateFlow<LockSettings> get() = _settings

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("ioshell", Context.MODE_PRIVATE)
        _settings.value = read()
    }

    val current: LockSettings get() = _settings.value

    fun update(block: (LockSettings) -> LockSettings) {
        val s = block(_settings.value)
        _settings.value = s
        sp.edit()
            .putBoolean("enabled", s.enabled)
            .putInt("clockFont", s.clockFont)
            .putInt("clockWeight", s.clockWeight)
            .putLong("clockColor", s.clockColor)
            .putFloat("clockAlpha", s.clockAlpha)
            .putFloat("clockScale", s.clockScale)
            .putString("leftButton", s.leftButton.name)
            .putString("rightButton", s.rightButton.name)
            .putString("notifMode", s.notifMode.name)
            .putString("ccTiles", cz.miuitweak.ioshell.cc.CcTile.format(s.ccTiles))
            .putBoolean("iosBanners", s.iosBanners)
            .putBoolean("airpodsPopup", s.airpodsPopup)
            .putLong("wallpaperVersion", s.wallpaperVersion)
            .apply()
    }

    private fun read(): LockSettings {
        val d = LockSettings()
        fun action(key: String, def: QuickAction) =
            runCatching { QuickAction.valueOf(sp.getString(key, def.name)!!) }.getOrDefault(def)
        return LockSettings(
            enabled = sp.getBoolean("enabled", d.enabled),
            clockFont = sp.getInt("clockFont", d.clockFont),
            clockWeight = sp.getInt("clockWeight", d.clockWeight),
            clockColor = sp.getLong("clockColor", d.clockColor),
            clockAlpha = sp.getFloat("clockAlpha", d.clockAlpha),
            clockScale = sp.getFloat("clockScale", d.clockScale),
            leftButton = action("leftButton", d.leftButton),
            rightButton = action("rightButton", d.rightButton),
            notifMode = runCatching { NotifMode.valueOf(sp.getString("notifMode", d.notifMode.name)!!) }
                .getOrDefault(d.notifMode),
            ccTiles = cz.miuitweak.ioshell.cc.CcTile.parse(sp.getString("ccTiles", null)),
            iosBanners = sp.getBoolean("iosBanners", d.iosBanners),
            airpodsPopup = sp.getBoolean("airpodsPopup", d.airpodsPopup),
            wallpaperVersion = sp.getLong("wallpaperVersion", d.wallpaperVersion),
        )
    }
}
