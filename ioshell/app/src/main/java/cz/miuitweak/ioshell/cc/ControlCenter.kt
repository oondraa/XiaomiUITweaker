package cz.miuitweak.ioshell.cc

import android.content.Intent
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.miuitweak.ioshell.media.AlbumArt
import cz.miuitweak.ioshell.media.MediaRepo
import cz.miuitweak.ioshell.ui.Ios
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ModuleBg = Color.White.copy(alpha = 0.14f)
private val OffCircle = Color.White.copy(alpha = 0.2f)

/**
 * Ovladaci centrum ve stylu iOS 18 (podle screenshotu z iPhonu): ovalne dlazdice s popiskem,
 * kolecka, vysoke posuvniky a prehravac s obalem dole. Prisazene ke spodnimu okraji.
 * [progress] 0..1 = jak moc je vytazene (ridi pruhlednost a "nafouknuti" z praveho horniho rohu).
 */
@Composable
fun ControlCenter(
    toggles: Toggles,
    progress: () -> Float,
    onClose: () -> Unit,
    torchOn: Boolean,
    /** globalni akce sluzby pristupnosti (snimek obrazovky, zamknout) */
    onGlobalAction: (Int) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(toggles.read()) }
    val media by MediaRepo.media.collectAsStateWithLifecycle()
    // po klepnuti chvili necist stav ze systemu (prepnuti trva) -> prepinac neskace sem a tam
    var holdUntil by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            if (System.currentTimeMillis() > holdUntil) state = toggles.read()
            delay(1000)
        }
    }
    fun act(block: suspend () -> Unit) {
        scope.launch {
            block()
            toggles.settle()
            state = toggles.read()
        }
    }

    /**
     * Systemovy prepinac: se Shizuku se prepne hned (a hned se ukaze novy stav),
     * bez Shizuku se otevre systemovy panel – nase ovladaci centrum se pritom musi zavrit,
     * jinak by panel zakryvalo.
     */
    fun sysToggle(optimistic: CcState, block: suspend () -> Unit) {
        if (!state.shizuku) {
            onClose()
            scope.launch { block() }
            return
        }
        state = optimistic
        holdUntil = System.currentTimeMillis() + 3000
        scope.launch {
            block()
            delay(3000)
            state = toggles.read()
        }
    }
    fun launchAndClose(i: Intent) {
        toggles.launch(i)
        onClose()
    }
    fun onOff(b: Boolean) = if (b) "Zapnuto" else "Vypnuto"
    val settings by cz.miuitweak.ioshell.data.Prefs.settings.collectAsStateWithLifecycle()
    var cellSize by remember { mutableStateOf(64.dp) }

    @Composable
    fun Tile(t: CcTile) {
        val (on, tint) = when (t) {
            CcTile.ROTATION -> state.rotationLock to Ios.Red
            CcTile.TORCH -> torchOn to Color(0xFF1C1C1E)
            CcTile.AIRPLANE -> state.airplane to Ios.Orange
            CcTile.DND -> state.dnd to Color(0xFF5E5CE6)
            CcTile.LOCATION -> state.location to Ios.Blue
            CcTile.DARK -> state.dark to Color(0xFF1C1C1E)
            else -> false to Color.White
        }
        Circle(t.icon, on, tint, cellSize) {
            when (t) {
                CcTile.ROTATION -> act { toggles.setRotationLock(!state.rotationLock) }
                CcTile.TORCH -> toggles.toggleTorch()
                CcTile.AIRPLANE -> sysToggle(state.copy(airplane = !state.airplane)) { toggles.setAirplane(!state.airplane) }
                CcTile.QR -> if (toggles.launchPackage("com.xiaomi.scanner")) onClose()
                    else launchAndClose(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                CcTile.DND -> act { toggles.setDnd(!state.dnd) }
                CcTile.CAMERA -> launchAndClose(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                CcTile.TIMER -> launchAndClose(Intent(AlarmClock.ACTION_SHOW_TIMERS))
                CcTile.ALARM -> launchAndClose(Intent(AlarmClock.ACTION_SHOW_ALARMS))
                CcTile.CALCULATOR -> if (toggles.launchPackage("com.miui.calculator")) onClose()
                    else launchAndClose(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))
                CcTile.RECORDER -> if (toggles.launchPackage("com.android.soundrecorder")) onClose()
                    else launchAndClose(Intent(android.provider.MediaStore.Audio.Media.RECORD_SOUND_ACTION))
                CcTile.SCREENSHOT -> {
                    onClose()
                    onGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                }
                CcTile.LOCK -> {
                    onClose()
                    onGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                }
                CcTile.HOTSPOT -> launchAndClose(
                    Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings"),
                )
                CcTile.LOCATION -> sysToggle(state.copy(location = !state.location)) { toggles.setLocation(!state.location) }
                CcTile.DARK -> sysToggle(state.copy(dark = !state.dark)) { toggles.setDark(!state.dark) }
                CcTile.NFC -> launchAndClose(Intent(Settings.ACTION_NFC_SETTINGS))
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress() }
            .background(Color.Black.copy(alpha = 0.55f))
            .combinedClickable(remember { MutableInteractionSource() }, null, onClick = onClose)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -60.dp.toPx()) onClose() },
                ) { _, dy -> total += dy }
            },
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .graphicsLayer {
                    val p = progress()
                    val s = 0.88f + 0.12f * p
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(1f, 0f)
                },
            // ne nalepene nahore ani uplne dole – zhruba stred, mirne niz
            contentAlignment = androidx.compose.ui.BiasAlignment(0f, 0.3f),
        ) {
            val margin = 22.dp
            val gap = 14.dp
            val cell = (maxWidth - margin * 2 - gap * 3) / 4
            cellSize = cell
            val wide = cell * 2 + gap

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = margin),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Pill(
                        Icons.Rounded.Wifi, "Wi-Fi", if (state.wifi) state.ssid ?: "Zapnuto" else "Vypnuto",
                        state.wifi, Ios.Blue, wide, cell,
                        onLong = {
                            toggles.openPanel(Settings.Panel.ACTION_WIFI)
                            onClose()
                        },
                    ) { sysToggle(state.copy(wifi = !state.wifi)) { toggles.setWifi(!state.wifi) } }
                    Pill(Icons.Rounded.SignalCellularAlt, "Mobilní data", onOff(state.data), state.data, Ios.Green, wide, cell) {
                        sysToggle(state.copy(data = !state.data)) { toggles.setData(!state.data) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Pill(
                        Icons.Rounded.Bluetooth, "Bluetooth", onOff(state.bluetooth), state.bluetooth, Ios.Blue, wide, cell,
                        onLong = {
                            toggles.openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
                            onClose()
                        },
                    ) { sysToggle(state.copy(bluetooth = !state.bluetooth)) { toggles.setBluetooth(!state.bluetooth) } }
                    Pill(Icons.Rounded.BatterySaver, "Úspora", onOff(state.powerSave), state.powerSave, Ios.Orange, wide, cell) {
                        sysToggle(state.copy(powerSave = !state.powerSave)) { toggles.setPowerSave(!state.powerSave) }
                    }
                }

                // vybrane dlazdice (poradi z nastaveni): prvni 4 vlevo vedle posuvniku, zbytek v radach po 4
                val tiles = settings.ccTiles
                @Composable
                fun TileOrGap(i: Int) {
                    val t = tiles.getOrNull(i)
                    if (t == null) Spacer(Modifier.size(cell)) else Tile(t)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            TileOrGap(0)
                            TileOrGap(1)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            TileOrGap(2)
                            TileOrGap(3)
                        }
                    }
                    VerticalSlider(Icons.Rounded.LightMode, state.brightness, cell, wide) { v ->
                        state = state.copy(brightness = v)
                        scope.launch { toggles.setBrightness(v) }
                    }
                    VerticalSlider(Icons.Rounded.VolumeUp, state.volume, cell, wide) { v ->
                        state = state.copy(volume = v)
                        toggles.setVolume(v)
                    }
                }
                tiles.drop(4).chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) { row.forEach { Tile(it) } }
                }

                NowPlaying(cell * 4 + gap * 3, cell * 1.7f)
            }
        }
    }
}

/** Ovalna dlazdice 2x1: ikona v kolecku + nazev + stav (jako iOS 18). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Pill(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    on: Boolean,
    onColor: Color,
    width: Dp,
    height: Dp,
    onLong: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier
            .size(width, height)
            .clip(RoundedCornerShape(50))
            .background(if (on) Color.White else ModuleBg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLong?.let {
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        it()
                    }
                },
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(height * 0.62f)
                .clip(CircleShape)
                .background(if (on) Color.Transparent else OffCircle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (on) onColor else Color.White, modifier = Modifier.size(height * 0.36f))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (on) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = if (on) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Circle(icon: ImageVector, on: Boolean, onTint: Color, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (on) Color.White else ModuleBg)
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (on) onTint else Color.White, modifier = Modifier.size(size * 0.42f))
    }
}

@Composable
private fun VerticalSlider(icon: ImageVector, value: Float, width: Dp, height: Dp, onChange: (Float) -> Unit) {
    var v by remember { mutableStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    if (!dragging) v = value
    Box(
        Modifier
            .size(width, height)
            .clip(RoundedCornerShape(50))
            .background(ModuleBg)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { ch, dy ->
                    ch.consume()
                    v = (v - dy / size.height).coerceIn(0f, 1f)
                    onChange(v)
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height * v)
                .background(Color.White),
        )
        Icon(
            icon, null,
            tint = if (v > 0.15f) Color(0xFF8E8E93) else Color.White,
            modifier = Modifier
                .padding(bottom = 18.dp)
                .size(26.dp),
        )
    }
}

/** Prehravac: obal alba vlevo, nazev, ovladani. */
@Composable
private fun NowPlaying(width: Dp, height: Dp) {
    val m by MediaRepo.media.collectAsStateWithLifecycle()
    Row(
        Modifier
            .size(width, height)
            .clip(RoundedCornerShape(28.dp))
            .background(ModuleBg)
            .combinedClickable(remember { MutableInteractionSource() }, null, onClick = {})
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(m?.art, height - 28.dp, 12.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                m?.title ?: "Nehraje",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val artist = m?.artist
            if (!artist.isNullOrBlank()) {
                Text(artist, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaButton(Icons.Rounded.FastRewind, 30.dp) { MediaRepo.previous() }
                MediaButton(if (m?.playing == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 38.dp) { MediaRepo.playPause() }
                MediaButton(Icons.Rounded.FastForward, 30.dp) { MediaRepo.next() }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaButton(icon: ImageVector, size: Dp, onClick: () -> Unit) {
    Icon(
        icon, null,
        tint = Color.White,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .combinedClickable(onClick = onClick),
    )
}
