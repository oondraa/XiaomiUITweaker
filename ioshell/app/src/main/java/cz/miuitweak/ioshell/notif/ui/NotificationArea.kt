package cz.miuitweak.ioshell.notif.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.miuitweak.ioshell.data.NotifMode
import cz.miuitweak.ioshell.notif.Notif
import cz.miuitweak.ioshell.notif.NotifAction
import cz.miuitweak.ioshell.notif.NotifGroup
import cz.miuitweak.ioshell.notif.grouped
import cz.miuitweak.ioshell.ui.Ios
import cz.miuitweak.ioshell.ui.glass

/** Co se ma stat s notifikaci – resi to aktivita (kvuli odemceni). */
class NotifCallbacks(
    val onOpen: (Notif) -> Unit,
    val onClear: (Notif) -> Unit,
    val onClearAll: (List<Notif>) -> Unit,
    val onMute: (Notif, Long) -> Unit,
    val onSettings: (Notif) -> Unit,
    val onAction: (Notif, NotifAction, String?) -> Unit,
)

private val CardShape = RoundedCornerShape(Ios.CardRadius)

/**
 * Notifikace na zamykacce.
 *  - zabalene (zamykacka): jen NOVE notifikace v rezimu Pocet / Balicek / Seznam
 *  - rozbalene (prejeti nahoru, Centrum oznameni): nove + historie s krizkem "vymazat vse"
 */
@Composable
fun NotificationArea(
    notifs: List<Notif>,
    mode: NotifMode,
    expanded: Boolean,
    cb: NotifCallbacks,
    onModeChange: (NotifMode) -> Unit,
    onPreview: (Notif) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fresh = notifs.filter { !it.seen }
    val history = notifs.filter { it.seen }
    var stackOpen by remember { mutableStateOf(false) }
    var openGroups by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(fresh.isEmpty()) { if (fresh.isEmpty()) stackOpen = false }

    val asList = expanded || stackOpen || mode == NotifMode.LIST

    LazyColumn(
        modifier
            .fillMaxSize()
            .pinch { zoomOut ->
                // stazeni prstu k sobe = kompaktnejsi, roztazeni = vic
                val order = listOf(NotifMode.COUNT, NotifMode.STACK, NotifMode.LIST)
                val i = order.indexOf(mode) + if (zoomOut) 1 else -1
                if (i in order.indices) onModeChange(order[i])
                stackOpen = false
            },
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        if (fresh.isNotEmpty()) {
            when {
                !asList && mode == NotifMode.COUNT -> item("count") {
                    CountPill(fresh.size, Modifier.animateItem()) { stackOpen = true }
                }
                !asList && mode == NotifMode.STACK -> item("stack") {
                    Stacked(fresh.size, Modifier.animateItem()) {
                        val top = fresh.first()
                        SwipeableCard(
                            top,
                            extra = if (fresh.size > 1) "${fresh.size - 1} ${dalsi(fresh.size - 1)}" else null,
                            onTap = if (fresh.size > 1) ({ stackOpen = true }) else null,
                            onOpen = { cb.onOpen(top) },
                            onClear = { cb.onClear(top) },
                            onLongPress = { onPreview(top) },
                            onMute = { cb.onMute(top, it) },
                            onSettings = { cb.onSettings(top) },
                        )
                    }
                }
                else -> groups(fresh.grouped(), openGroups, { openGroups = it }, cb, onPreview, "n")
            }
        }
        if (expanded) {
            if (history.isNotEmpty()) {
                item("hdr") {
                    HistoryHeader(Modifier.animateItem()) { cb.onClearAll(history) }
                }
                groups(history.grouped(), openGroups, { openGroups = it }, cb, onPreview, "h")
            } else if (fresh.isEmpty()) {
                item("empty") {
                    Text(
                        "Žádná starší oznámení",
                        color = Ios.TextSecondary,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun dalsi(n: Int) = when (n) {
    1 -> "další oznámení"
    in 2..4 -> "další oznámení"
    else -> "dalších oznámení"
}

private fun LazyListScope.groups(
    groups: List<NotifGroup>,
    open: Set<String>,
    setOpen: (Set<String>) -> Unit,
    cb: NotifCallbacks,
    onPreview: (Notif) -> Unit,
    prefix: String,
) {
    groups.forEach { g ->
        if (g.items.size == 1) {
            val n = g.top
            item("$prefix-${n.key}") {
                SwipeableCard(
                    n,
                    modifier = Modifier.animateItem(),
                    onOpen = { cb.onOpen(n) },
                    onClear = { cb.onClear(n) },
                    onLongPress = { onPreview(n) },
                    onMute = { cb.onMute(n, it) },
                    onSettings = { cb.onSettings(n) },
                )
            }
        } else if (g.key in open) {
            item("$prefix-g-${g.key}") {
                GroupHeader(
                    g.top.appName,
                    Modifier.animateItem(),
                    onLess = { setOpen(open - g.key) },
                    onClear = { cb.onClearAll(g.items) },
                )
            }
            g.items.forEach { n ->
                item("$prefix-${n.key}") {
                    SwipeableCard(
                        n,
                        modifier = Modifier.animateItem(),
                        onOpen = { cb.onOpen(n) },
                        onClear = { cb.onClear(n) },
                        onLongPress = { onPreview(n) },
                        onMute = { cb.onMute(n, it) },
                        onSettings = { cb.onSettings(n) },
                    )
                }
            }
        } else {
            item("$prefix-s-${g.key}") {
                Stacked(g.items.size, Modifier.animateItem()) {
                    SwipeableCard(
                        g.top,
                        extra = "${g.items.size - 1} ${dalsi(g.items.size - 1)}",
                        onTap = { setOpen(open + g.key) },
                        onOpen = { cb.onOpen(g.top) },
                        // vymazani zabalene skupiny smaze celou skupinu (jako iOS)
                        onClear = { cb.onClearAll(g.items) },
                        onLongPress = { onPreview(g.top) },
                        onMute = { cb.onMute(g.top, it) },
                        onSettings = { cb.onSettings(g.top) },
                    )
                }
            }
        }
    }
}

/** Karta s 1–2 "duchy" vykukujicimi zespodu = skupina / balicek. */
@Composable
private fun Stacked(count: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val ghosts = (count - 1).coerceIn(0, 2)
    Box(
        modifier
            .fillMaxWidth()
            .padding(bottom = (8 * ghosts).dp),
    ) {
        for (i in ghosts downTo 1) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(horizontal = (10 * i).dp)
                    .padding(top = (8 * i).dp, bottom = (8 * (ghosts - i)).dp)
                    .glass(CardShape, tint = Ios.GlassTint.copy(alpha = 0.45f - 0.1f * i)),
            )
        }
        content()
    }
}

@Composable
private fun CountPill(count: Int, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            "$count ${if (count == 1) "oznámení" else "oznámení"}",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .glass(RoundedCornerShape(50))
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun GroupHeader(app: String, modifier: Modifier, onLess: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(app, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Pill("Zobrazit méně", onLess)
        Spacer(Modifier.width(8.dp))
        CloseCircle(onClear)
    }
}

@Composable
private fun HistoryHeader(modifier: Modifier, onClearAll: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Centrum oznámení",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        CloseCircle(onClearAll)
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .glass(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun CloseCircle(onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .glass(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("✕", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Gesto dvema prsty: true = prsty k sobe (kompaktnejsi), false = od sebe. */
private fun Modifier.pinch(onPinch: (zoomOut: Boolean) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var start = 0f
        var done = false
        do {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = ev.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                val d = (pressed[0].position - pressed[1].position).getDistance()
                if (start == 0f) {
                    start = d
                } else if (!done) {
                    val r = d / start
                    if (r < 0.75f) {
                        onPinch(true); done = true
                    } else if (r > 1.33f) {
                        onPinch(false); done = true
                    }
                }
                ev.changes.forEach { it.consume() }
            }
        } while (ev.changes.any { it.pressed })
    }
}

/** Podrzeni karty: zvetseny nahled s celym textem + akce (Odpovedet apod.). */
@Composable
fun NotifPreview(
    n: Notif?,
    onDismiss: () -> Unit,
    onOpen: (Notif) -> Unit,
    onAction: (Notif, NotifAction, String?) -> Unit,
) {
    AnimatedVisibility(n != null, enter = fadeIn(), exit = fadeOut()) {
        val shown = remember { mutableStateOf<Notif?>(null) }
        if (n != null) shown.value = n
        val cur = shown.value ?: return@AnimatedVisibility
        var replyFor by remember(cur.key) { mutableStateOf<NotifAction?>(null) }
        var text by remember(cur.key) { mutableStateOf("") }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .animateEnterExit(enter = scaleIn(initialScale = 0.92f), exit = scaleOut(targetScale = 0.92f)),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CardBody(
                    cur,
                    full = true,
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .clickable { onOpen(cur) },
                )
                val actions = buildList {
                    cur.actions.forEach { add(it) }
                }
                Column(
                    Modifier
                        .width(250.dp)
                        .glass(RoundedCornerShape(14.dp)),
                ) {
                    ActionRow("Otevřít") { onOpen(cur) }
                    actions.forEach { a ->
                        ActionRow(a.title) {
                            if (a.remoteInput != null) replyFor = a else {
                                onAction(cur, a, null)
                                onDismiss()
                            }
                        }
                    }
                }
                val ra = replyFor
                if (ra != null) {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(ra) { focus.requestFocus() }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(22.dp))
                            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                            cursorBrush = SolidColor(Ios.Blue),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focus),
                            decorationBox = { inner ->
                                if (text.isEmpty()) Text(ra.title, color = Ios.TextSecondary, fontSize = 16.sp)
                                inner()
                            },
                        )
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(if (text.isBlank()) Color.Gray else Ios.Blue, CircleShape)
                                .clickable(enabled = text.isNotBlank()) {
                                    onAction(cur, ra, text)
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("↑", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Color.White,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    )
}
