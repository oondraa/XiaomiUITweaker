package cz.miuitweak.ioshell.notif.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.miuitweak.ioshell.notif.Notif
import cz.miuitweak.ioshell.ui.Ios
import cz.miuitweak.ioshell.ui.glass
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val CardShape = RoundedCornerShape(Ios.CardRadius)
private val Spring = spring<Float>(dampingRatio = 0.8f, stiffness = 400f)

/** "nyní", "před 5 min", "20:14", "včera", "12. 11." */
fun relTime(t: Long, now: Long = System.currentTimeMillis()): String {
    val d = now - t
    if (d < 60_000) return "nyní"
    if (d < 3_600_000) return "před ${d / 60_000} min"
    val c = Calendar.getInstance().apply { timeInMillis = t }
    val today = Calendar.getInstance()
    val sameDay = c.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        c.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return SimpleDateFormat("H:mm", Locale.getDefault()).format(Date(t))
    today.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = c.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        c.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    if (yesterday) return "včera"
    return SimpleDateFormat("d. M.", Locale.getDefault()).format(Date(t))
}

/** Samotny obsah karty (bez gest) – pouziva se i v nahledu po podrzeni. */
@Composable
fun CardBody(
    n: Notif,
    modifier: Modifier = Modifier,
    extra: String? = null,
    maxLines: Int = 4,
    full: Boolean = false,
    bg: Color? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (bg != null) Modifier.background(bg, CardShape) else Modifier.glass(CardShape))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        NotifIcon(n)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    n.title,
                    color = Ios.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                Text(relTime(n.time), color = Ios.TextSecondary, fontSize = 13.sp)
            }
            Text(
                if (full) n.bigText else n.text,
                color = Ios.TextPrimary,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                maxLines = if (full) Int.MAX_VALUE else maxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (extra != null) {
                Text(extra, color = Ios.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun NotifIcon(n: Notif) {
    Box(Modifier.size(38.dp)) {
        if (n.avatar != null) {
            // komunikace: avatar + maly odznak aplikace (jako iOS 15+)
            Image(
                n.avatar, null,
                Modifier
                    .size(38.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            n.appIcon?.let {
                Image(
                    it, null,
                    Modifier
                        .size(17.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 3.dp, y = 3.dp)
                        .clip(RoundedCornerShape(5.dp)),
                )
            }
        } else if (n.appIcon != null) {
            Image(
                n.appIcon, null,
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(9.dp)),
            )
        } else {
            Box(
                Modifier
                    .size(38.dp)
                    .background(Color.Gray, RoundedCornerShape(9.dp)),
            )
        }
    }
}

/**
 * Karta s iOS gesty:
 *  - klepnuti = otevrit,  podrzeni = nahled + akce
 *  - doleva kousek = tlacitka Moznosti / Vymazat, doleva az do konce = vymazat
 *  - doprava = otevrit
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeableCard(
    n: Notif,
    onOpen: () -> Unit,
    onClear: () -> Unit,
    onLongPress: () -> Unit,
    onMute: (Long) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    extra: String? = null,
    onTap: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val offset = remember(n.key) { Animatable(0f) }
    // jen dve polohy: zavrena (0) nebo otevrena s tlacitkem Vymazat (-reveal). Zadne volne klouzani.
    val reveal = with(LocalDensity.current) { 92.dp.toPx() }
    val canReveal = n.clearable

    Box(modifier.fillMaxWidth()) {
        val progress = (-offset.value / reveal).coerceIn(0f, 1f)
        if (progress > 0f) {
            Row(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = progress },
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SideButton("Vymazat") {
                    scope.launch {
                        offset.animateTo(0f, tween(120))
                        onClear()
                    }
                }
            }
        }

        CardBody(
            n,
            extra = extra,
            modifier = Modifier
                .graphicsLayer { translationX = offset.value }
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = canReveal,
                    state = rememberDraggableState { d ->
                        scope.launch {
                            // za otevrenou polohu uz jen s velkym odporem, doprava vubec
                            val cur = offset.value
                            val next = if (cur + d < -reveal) cur + d * 0.15f else cur + d
                            offset.snapTo(next.coerceIn(-reveal * 1.15f, 0f))
                        }
                    },
                    onDragStopped = { v ->
                        val open = offset.value < -reveal * 0.5f || v < -1500f
                        offset.animateTo(if (open && v < 1500f) -reveal else 0f, Spring)
                    },
                )
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (offset.value != 0f) scope.launch { offset.animateTo(0f, Spring) }
                        else (onTap ?: onOpen)()
                    },
                    onLongClick = onLongPress,
                ),
        )
    }
}

@Composable
private fun SideButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .width(76.dp)
            .fillMaxHeight()
            .padding(vertical = 2.dp)
            .glass(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
