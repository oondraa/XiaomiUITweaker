package cz.miuitweak.ioshell.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.miuitweak.ioshell.R

/** Tokeny podle IOS_SPEC.md (iOS 17/18 matne sklo, tmavy rezim). */
object Ios {
    val GlassTint = Color(0xFF1C1C1E).copy(alpha = 0.45f)
    val GlassTintLight = Color.White.copy(alpha = 0.18f)
    val GlassEdge = Color.White.copy(alpha = 0.10f)
    val TextPrimary = Color.White
    val TextSecondary = Color(0xFFEBEBF5).copy(alpha = 0.6f)

    val Blue = Color(0xFF0A84FF)
    val Green = Color(0xFF30D158)
    val Orange = Color(0xFFFF9F0A)
    val Red = Color(0xFFFF453A)

    val CardRadius = 22.dp
    val ButtonSize = 50.dp
}

/** Pismo hodin. [family] dostane vahu, aby slo i variabilni pismo (Nunito). */
class ClockFont(val name: String, val family: (Int) -> FontFamily)

/** Variabilni pismo – vaha jde nastavit plynule. */
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int): (Int) -> FontFamily = { w ->
    FontFamily(Font(res, weight = FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))))
}

/** Pismo s jedinou vahou (tloustka se ignoruje). */
private fun static(res: Int): (Int) -> FontFamily = { FontFamily(Font(res)) }

/** Pisma hodin (otevrene licence OFL, podobne tem na iOS). Poradi = ulozeny index, nove jen na konec. */
val ClockFonts: List<ClockFont> = listOf(
    ClockFont("Systém") { FontFamily.SansSerif },
    ClockFont("Inter", variable(R.font.clock_inter)),
    ClockFont("Outfit", variable(R.font.clock_outfit)),
    ClockFont("Manrope", variable(R.font.clock_manrope)),
    ClockFont("Vysoký", variable(R.font.clock_oswald)),
    ClockFont("Bebas", static(R.font.clock_bebas)),
    ClockFont("Patkový", variable(R.font.clock_playfair)),
    ClockFont("Šablona", variable(R.font.clock_stencil)),
    ClockFont("Mono", variable(R.font.clock_jbmono)),
)

/** Barvy hodin na vyber (iOS nabizi rade odstinu + vlastni). */
val ClockColors: List<Long> = listOf(
    0xFFFFFFFF, 0xFFF2E6D0, 0xFFC9B48A, 0xFFFFD60A, 0xFFFF9F0A,
    0xFFFF6482, 0xFFBF5AF2, 0xFF64D2FF, 0xFF30D158, 0xFF1C1C1E,
)
