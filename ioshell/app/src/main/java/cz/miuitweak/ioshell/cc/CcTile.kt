package cz.miuitweak.ioshell.cc

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.ui.graphics.vector.ImageVector

/** Kulate dlazdice ovladaciho centra, ktere si uzivatel vybira a radi v appce. */
enum class CcTile(val label: String, val icon: ImageVector) {
    ROTATION("Zámek otáčení", Icons.Rounded.ScreenLockRotation),
    TORCH("Svítilna", Icons.Rounded.FlashlightOn),
    AIRPLANE("Režim letadlo", Icons.Rounded.AirplanemodeActive),
    QR("Čtečka QR", Icons.Rounded.QrCodeScanner),
    DND("Nerušit", Icons.Rounded.Bedtime),
    CAMERA("Fotoaparát", Icons.Rounded.PhotoCamera),
    TIMER("Minutka", Icons.Rounded.Timer),
    CALCULATOR("Kalkulačka", Icons.Rounded.Calculate),
    ALARM("Budík", Icons.Rounded.Alarm),
    RECORDER("Diktafon", Icons.Rounded.Mic),
    SCREENSHOT("Snímek obrazovky", Icons.Rounded.Screenshot),
    LOCK("Zamknout", Icons.Rounded.Lock),
    HOTSPOT("Hotspot", Icons.Rounded.WifiTethering),
    LOCATION("Poloha", Icons.Rounded.LocationOn),
    DARK("Tmavý režim", Icons.Rounded.DarkMode),
    NFC("NFC", Icons.Rounded.Nfc),
    ;

    companion object {
        val DEFAULT = listOf(ROTATION, TORCH, AIRPLANE, QR, DND, CAMERA, TIMER, CALCULATOR)

        fun parse(s: String?): List<CcTile> {
            if (s == null) return DEFAULT
            return s.split(',').mapNotNull { n -> entries.firstOrNull { it.name == n } }
        }

        fun format(list: List<CcTile>) = list.joinToString(",") { it.name }
    }
}
