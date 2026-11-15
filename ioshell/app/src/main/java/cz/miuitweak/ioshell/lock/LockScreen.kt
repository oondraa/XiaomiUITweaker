package cz.miuitweak.ioshell.lock

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import cz.miuitweak.ioshell.data.LockSettings
import cz.miuitweak.ioshell.data.NotifMode
import cz.miuitweak.ioshell.media.BigArtBackground
import cz.miuitweak.ioshell.media.BigArtContent
import cz.miuitweak.ioshell.media.Media
import cz.miuitweak.ioshell.media.MediaCard
import cz.miuitweak.ioshell.notif.Notif
import cz.miuitweak.ioshell.notif.ui.NotifCallbacks
import cz.miuitweak.ioshell.notif.ui.NotifPreview
import cz.miuitweak.ioshell.notif.ui.NotificationArea
import cz.miuitweak.ioshell.data.QuickAction
import cz.miuitweak.ioshell.data.WallpaperImages
import cz.miuitweak.ioshell.ui.CameraGlyph
import cz.miuitweak.ioshell.ui.ClockFonts
import cz.miuitweak.ioshell.ui.FlashlightGlyph
import cz.miuitweak.ioshell.ui.LocalGlass
import cz.miuitweak.ioshell.ui.LockGlyph
import cz.miuitweak.ioshell.ui.glass
import cz.miuitweak.ioshell.ui.glassSource
import cz.miuitweak.ioshell.ui.rememberGlassSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val Spring = spring<Float>(dampingRatio = 0.8f, stiffness = 400f)

/**
 * Zamykaci obrazovka ve stylu iOS. Pouziva se v LockActivity, v nahledu v nastaveni
 * a pozdeji i jako "Notification Center" pri stazeni shora.
 */
@Composable
fun LockScreen(
    settings: LockSettings,
    wallpaper: WallpaperImages?,
    locked: Boolean,
    torchOn: Boolean,
    modifier: Modifier = Modifier,
    exiting: Boolean = false,
    resetKey: Int = 0,
    interactive: Boolean = true,
    onSwipeUp: () -> Unit = {},
    onExited: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onAction: (QuickAction) -> Unit = {},
    onEdit: () -> Unit = {},
    notifs: List<Notif> = emptyList(),
    notifCallbacks: NotifCallbacks? = null,
    onModeChange: (NotifMode) -> Unit = {},
    /** true = otevrit rovnou s historii (Centrum oznameni pri stazeni shora). */
    initialExpanded: Boolean = false,
    showUnlockHint: Boolean = true,
    /** Zvyseni = vratit zamykacku zpet dolu (odemceni se nepovedlo / bylo zruseno). */
    bounceKey: Int = 0,
    /** Pevny horni okraj misto insets stavoveho radku (overlay okna insets nedostanou). */
    topInset: androidx.compose.ui.unit.Dp? = null,
    /** Co prave hraje – prehravac jako na iOS. */
    media: Media? = null,
    /** Kdyz neni null, odjezd nahoru neanimuje zamykacka sama, ale preda aktualni posun (kopii v overlay). */
    exitHandoff: ((Float) -> Unit)? = null,
    /** Zacal tah nahoru k odemceni (aktivita pak ukaze, co je pod zamykackou). */
    onUnlockDragStart: () -> Unit = {},
) {
    val glass = rememberGlassSource(wallpaper?.blurred)
    val offsetY = remember { Animatable(0f) }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var heightPx by remember { mutableFloatStateOf(1f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val lockedNow by rememberUpdatedLocked(locked)
    var expanded by remember { mutableStateOf(initialExpanded) }
    var preview by remember { mutableStateOf<Notif?>(null) }
    var bigArt by remember { mutableStateOf(false) }
    if (media == null) bigArt = false
    val hasNotifs by rememberUpdatedLocked(notifs.isNotEmpty())
    val bottomZone = with(LocalDensity.current) { 150.dp.toPx() }
    val dim by animateFloatAsState(if (expanded) 0.35f else 0f, label = "dim")

    LaunchedEffect(resetKey) {
        offsetY.snapTo(0f)
        offsetX.snapTo(0f)
        expanded = initialExpanded
        preview = null
    }
    LaunchedEffect(bounceKey) {
        if (bounceKey > 0) offsetY.animateTo(0f, Spring)
    }
    LaunchedEffect(exiting) {
        if (exiting) {
            val h = exitHandoff
            if (h != null) {
                h(offsetY.value)
            } else {
                offsetY.animateTo(-heightPx, tween(260, easing = FastOutSlowInEasing))
                onExited()
            }
        }
    }

    val gestures = if (!interactive) Modifier else Modifier
        .pointerInput(Unit) { detectTapGestures(onLongPress = { onEdit() }) }
        .pointerInput(Unit) {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                // Zespodu (nebo kdyz nejsou zadne notifikace) = odemknout.
                // Uprostred = otevrit/zavrit Centrum oznameni (historie), jako na iOS.
                val unlockDrag = down.position.y > heightPx - bottomZone || !hasNotifs
                var axis = 0 // 0 = nerozhodnuto, 1 = svisle, 2 = vodorovne
                var total = Offset.Zero
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    val d = ch.positionChange()
                    total += d
                    tracker.addPosition(ch.uptimeMillis, ch.position)
                    if (axis == 0) {
                        if (abs(total.y) > slop && abs(total.y) > abs(total.x)) {
                            axis = 1
                            if (unlockDrag && total.y < 0) onUnlockDragStart()
                        }
                        else if (abs(total.x) > slop) axis = 2
                    }
                    when (axis) {
                        1 -> {
                            ch.consume()
                            if (unlockDrag) {
                                val ny = (offsetY.value + d.y).coerceAtMost(0f)
                                scope.launch { offsetY.snapTo(ny) }
                            }
                        }
                        2 -> {
                            ch.consume()
                            // jen doleva (fotoaparat), doprava jen trochu s odporem
                            val nx = (offsetX.value + if (offsetX.value + d.x > 0) d.x * 0.2f else d.x)
                            scope.launch { offsetX.snapTo(nx) }
                        }
                    }
                }
                val v = tracker.calculateVelocity()
                when (axis) {
                    1 -> if (unlockDrag) {
                        val passed = offsetY.value < -heightPx * 0.2f || v.y < -1400f
                        if (passed) onSwipeUp()
                        // kdyz je zamceno, rozhodne aktivita (pocka na otisk / PIN) a pripadne vrati pres bounceKey
                        if (!passed) scope.launch { offsetY.animateTo(0f, Spring) }
                    } else {
                        // jen neskonzumovany pohyb (kdyz se seznam scrolluje, nezavira se)
                        if (total.y < -bottomZone * 0.35f) expanded = true
                        else if (total.y > bottomZone * 0.35f) expanded = false
                    }
                    2 -> {
                        if (offsetX.value < -widthPx * 0.3f || v.x < -1400f) onSwipeLeft()
                        scope.launch { offsetX.animateTo(0f, Spring) }
                    }
                }
            }
        }

    CompositionLocalProvider(LocalGlass provides glass) {
        Box(
            modifier
                .fillMaxSize()
                .onSizeChanged {
                    heightPx = it.height.toFloat()
                    widthPx = it.width.toFloat()
                }
                .then(gestures),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = offsetY.value
                        translationX = offsetX.value
                    }
                    .background(Color.Black)
                    .glassSource(glass),
            ) {
                if (wallpaper != null) {
                    Image(wallpaper.sharp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = bigArt && media != null,
                    enter = androidx.compose.animation.fadeIn(tween(350)),
                    exit = androidx.compose.animation.fadeOut(tween(300)),
                ) { if (media != null) BigArtBackground(media) }
                // jemne ztmaveni nahore a dole kvuli citelnosti
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent))),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f)))),
                )

                // v Centru oznameni se tapeta ztlumi
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = dim }
                        .background(Color.Black),
                )

                Column(Modifier.fillMaxSize()) {
                    LockContent(settings, locked, resetKey, topInset)
                    androidx.compose.animation.AnimatedContent(
                        targetState = bigArt && media != null,
                        transitionSpec = {
                            (androidx.compose.animation.fadeIn(tween(320)) +
                                androidx.compose.animation.scaleIn(tween(380, easing = FastOutSlowInEasing), initialScale = 0.88f))
                                .togetherWith(
                                    androidx.compose.animation.fadeOut(tween(200)) +
                                        androidx.compose.animation.scaleOut(tween(250), targetScale = 0.94f),
                                )
                        },
                        modifier = Modifier.weight(1f),
                        label = "bigArt",
                    ) { big ->
                    Column(Modifier.fillMaxSize()) {
                    if (big && media != null) {
                        // velky obal alba misto notifikaci
                        BigArtContent(
                            media,
                            onArtClick = { bigArt = false },
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = 12.dp),
                        )
                    } else {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            if (notifCallbacks != null) {
                                NotificationArea(
                                    notifs = notifs,
                                    mode = settings.notifMode,
                                    expanded = expanded,
                                    cb = notifCallbacks,
                                    onModeChange = onModeChange,
                                    onPreview = { preview = it },
                                )
                            }
                        }
                        if (media != null && !expanded) {
                            MediaCard(media, onArtClick = { bigArt = true }, modifier = Modifier.padding(bottom = 12.dp))
                        }
                    }
                    }
                    }
                    BottomBar(
                        settings = settings,
                        locked = locked,
                        torchOn = torchOn,
                        onAction = onAction,
                        showUnlockHint = showUnlockHint,
                        modifier = Modifier,
                    )
                }

                if (notifCallbacks != null) {
                    NotifPreview(
                        n = preview,
                        onDismiss = { preview = null },
                        onOpen = {
                            preview = null
                            notifCallbacks.onOpen(it)
                        },
                        onAction = notifCallbacks.onAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberUpdatedLocked(locked: Boolean): State<Boolean> {
    val s = remember { mutableStateOf(locked) }
    s.value = locked
    return s
}

@Composable
private fun LockContent(settings: LockSettings, locked: Boolean, resetKey: Int, topInset: androidx.compose.ui.unit.Dp?) {
    val ctx = LocalContext.current
    val now by produceState(LocalDateTime.now(), resetKey) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L + 30)
        }
    }
    val is24 = DateFormat.is24HourFormat(ctx)
    val time = now.format(DateTimeFormatter.ofPattern(if (is24) "H:mm" else "h:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE d. MMMM", Locale.getDefault()))
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }

    val color = Color(settings.clockColor)
    val font = ClockFonts.getOrElse(settings.clockFont) { ClockFonts[0] }
    val clockSize = (96f * settings.clockScale).sp

    Column(
        Modifier
            .fillMaxWidth()
            .then(if (topInset != null) Modifier.padding(top = topInset) else Modifier.statusBarsPadding())
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LockGlyph(open = !locked, color = Color.White.copy(alpha = 0.9f), size = 22.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            date,
            color = color.copy(alpha = 0.92f * settings.clockAlpha),
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            time,
            color = color.copy(alpha = settings.clockAlpha),
            style = TextStyle(
                fontFamily = font.family(settings.clockWeight),
                fontWeight = FontWeight(settings.clockWeight),
                fontSize = clockSize,
                lineHeight = clockSize,
                letterSpacing = (-1.5).sp,
                fontFeatureSettings = "tnum",
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
            modifier = Modifier.padding(top = 2.dp),
        )
        val pods by cz.miuitweak.ioshell.airpods.AirPods.state.collectAsState()
        val p = pods
        if (p != null && p.fresh) {
            cz.miuitweak.ioshell.airpods.AirPodsWidget(p, Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun BottomBar(
    settings: LockSettings,
    locked: Boolean,
    torchOn: Boolean,
    onAction: (QuickAction) -> Unit,
    showUnlockHint: Boolean,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 46.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            QuickButton(settings.leftButton, torchOn, onAction)
            AnimatedVisibility(!locked && showUnlockHint, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    "Přejeďte nahoru\npro otevření",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            QuickButton(settings.rightButton, torchOn, onAction)
        }
        Spacer(Modifier.height(28.dp))
        // "home indicator"
        Box(
            Modifier
                .width(134.dp)
                .height(5.dp)
                .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(50)),
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun QuickButton(action: QuickAction, torchOn: Boolean, onAction: (QuickAction) -> Unit) {
    if (action == QuickAction.NONE) {
        Spacer(Modifier.size(50.dp))
        return
    }
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 1.2f else 1f, Spring, label = "btn")
    val active = action == QuickAction.FLASHLIGHT && torchOn

    Box(
        Modifier
            .size(50.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (active) Modifier.background(Color.White, CircleShape)
                else Modifier.glass(CircleShape, tint = Color.Black.copy(alpha = 0.22f)),
            )
            .pointerInput(action) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    // jako na iPhonu: aktivuje se dlouhym stiskem, ne klepnutim (kapsa)
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAction(action)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val c = if (active) Color.Black else Color.White
        when (action) {
            QuickAction.FLASHLIGHT -> FlashlightGlyph(c)
            QuickAction.CAMERA -> CameraGlyph(c)
            QuickAction.NONE -> {}
        }
    }
}
