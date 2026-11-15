package cz.miuitweak.ioshell

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.miuitweak.ioshell.cc.ShizukuShell
import cz.miuitweak.ioshell.data.NotifMode
import cz.miuitweak.ioshell.data.Prefs
import cz.miuitweak.ioshell.data.QuickAction
import cz.miuitweak.ioshell.data.Wallpaper
import cz.miuitweak.ioshell.data.WallpaperImages
import cz.miuitweak.ioshell.lock.LockActivity
import cz.miuitweak.ioshell.lock.LockScreen
import cz.miuitweak.ioshell.service.LockService
import cz.miuitweak.ioshell.ui.ClockColors
import cz.miuitweak.ioshell.ui.ClockFonts
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Prefs.current.enabled) LockService.start(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = Color.Black) { SettingsScreen() }
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val activity = ctx as ComponentActivity
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // stav opravneni se obnovi pri kazdem navratu do appky
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val overlayOk = remember(tick) { Settings.canDrawOverlays(ctx) }
    val notifOk = remember(tick) {
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val listenerOk = remember(tick) {
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    }
    val a11yOk = remember(tick) {
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().contains("${ctx.packageName}/")
    }
    val writeOk = remember(tick) { Settings.System.canWrite(ctx) }
    val shizukuOk = remember(tick) { ShizukuShell.granted }
    val batteryOk = remember(tick) {
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { Wallpaper.import(ctx, uri) }
    }

    // tapeta pro nahled
    val cfg = LocalConfiguration.current
    var wallpaper by remember { mutableStateOf<WallpaperImages?>(null) }
    val bounds = activity.windowManager.currentWindowMetrics.bounds
    LaunchedEffect(settings.wallpaperVersion) {
        wallpaper = Wallpaper.load(ctx, bounds.width(), bounds.height(), settings.wallpaperVersion)
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("XiaomiUITweaker", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)

        // ---------- nahled ----------
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val previewH = 420.dp
            val scale = previewH.value / cfg.screenHeightDp
            Box(
                Modifier
                    .size(cfg.screenWidthDp.dp * scale, previewH)
                    .clip(RoundedCornerShape(28.dp))
                    .clickable { ctx.startActivity(Intent(ctx, LockActivity::class.java)) },
                contentAlignment = Alignment.Center,
            ) {
                LockScreen(
                    settings = settings,
                    wallpaper = wallpaper,
                    locked = true,
                    torchOn = false,
                    interactive = false,
                    modifier = Modifier
                        .requiredSize(cfg.screenWidthDp.dp, cfg.screenHeightDp.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
        }
        Text(
            "Klepni na náhled pro vyzkoušení naostro (přejetím nahoru zavřeš).",
            color = Color.Gray, fontSize = 13.sp,
        )

        // ---------- zapnuti ----------
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Zamykačka zapnutá", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("Zobrazí se pokaždé po zhasnutí displeje", color = Color.Gray, fontSize = 13.sp)
                }
                Switch(
                    checked = settings.enabled,
                    enabled = overlayOk,
                    onCheckedChange = { on ->
                        Prefs.update { it.copy(enabled = on) }
                        if (on) LockService.start(ctx) else LockService.stop(ctx)
                    },
                )
            }
        }

        // ---------- opravneni ----------
        Card {
            Section("Oprávnění")
            PermissionRow("Zobrazení přes ostatní aplikace", overlayOk) {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")),
                )
            }
            PermissionRow("Přístup k oznámením (notifikace na zamykačce)", listenerOk) {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            PermissionRow("Přístupnost: stažení shora (Centrum oznámení + Ovládací centrum)", a11yOk) {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            PermissionRow("Shizuku: Wi-Fi/BT/data jedním klepnutím", shizukuOk) {
                if (ShizukuShell.running) ShizukuShell.requestPermission()
                else runCatching {
                    ctx.startActivity(
                        ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                            ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api")),
                    )
                }
                tick++
            }
            PermissionRow("Změna nastavení systému (jas)", writeOk) {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")),
                )
            }
            PermissionRow("Oznámení (běžící služba)", notifOk) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            PermissionRow("Bez omezení baterie", batteryOk) {
                ctx.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${ctx.packageName}"),
                    ),
                )
            }
            PermissionRow("MIUI: vyskakovací okna na pozadí + zamykací obrazovka", null) {
                openMiuiPermissions(ctx)
            }
            PermissionRow("MIUI: Autostart", null) { openMiuiAutostart(ctx) }
        }

        // ---------- tapeta ----------
        Card {
            Section("Tapeta")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Vybrat fotku") }
                OutlinedButton(onClick = { Wallpaper.reset(ctx) }) { Text("Výchozí") }
            }
        }

        // ---------- hodiny ----------
        Card {
            Section("Hodiny")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ClockFonts.forEachIndexed { i, f ->
                    val selected = settings.clockFont == i
                    Box(
                        Modifier
                            .size(76.dp, 64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF2C2C2E))
                            .border(2.dp, if (selected) Color.White else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickable { Prefs.update { it.copy(clockFont = i) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "12",
                            color = Color.White,
                            fontSize = 30.sp,
                            fontFamily = f.family(settings.clockWeight),
                            fontWeight = FontWeight(settings.clockWeight),
                        )
                    }
                }
            }
            Label("Tloušťka")
            Slider(
                value = settings.clockWeight.toFloat(),
                onValueChange = { v -> Prefs.update { it.copy(clockWeight = v.toInt()) } },
                valueRange = 100f..900f,
            )
            Label("Barva")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ClockColors.forEach { c ->
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(
                                3.dp,
                                if (settings.clockColor == c) Color(0xFF0A84FF) else Color.White.copy(alpha = 0.2f),
                                CircleShape,
                            )
                            .clickable { Prefs.update { it.copy(clockColor = c) } },
                    )
                }
            }
            Label("Průhlednost")
            Slider(
                value = settings.clockAlpha,
                onValueChange = { v -> Prefs.update { it.copy(clockAlpha = v) } },
                valueRange = 0.3f..1f,
            )
            Label("Velikost")
            Slider(
                value = settings.clockScale,
                onValueChange = { v -> Prefs.update { it.copy(clockScale = v) } },
                valueRange = 0.7f..1.8f,
            )
        }

        // ---------- notifikace ----------
        Card {
            Section("Notifikace na zamykačce")
            Label("Zobrazení (na zamykačce jde přepnout i roztažením/stažením dvěma prsty)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NotifMode.entries.forEach { m ->
                    FilterChip(
                        selected = settings.notifMode == m,
                        onClick = { Prefs.update { it.copy(notifMode = m) } },
                        label = { Text(m.label) },
                    )
                }
            }
        }

        // ---------- ovladaci centrum ----------
        Card {
            Section("Ovládací centrum")
            Label("Zaškrtni, co chceš mít v ovládacím centru. Šipkami změníš pořadí (první 4 jsou vedle posuvníků).")
            val enabled = settings.ccTiles
            val all = enabled + cz.miuitweak.ioshell.cc.CcTile.entries.filter { it !in enabled }
            all.forEach { t ->
                val on = t in enabled
                val idx = enabled.indexOf(t)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Checkbox(
                        checked = on,
                        onCheckedChange = { c ->
                            Prefs.update { st -> st.copy(ccTiles = if (c) st.ccTiles + t else st.ccTiles - t) }
                        },
                    )
                    androidx.compose.material3.Icon(t.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(t.label, color = if (on) Color.White else Color.Gray, modifier = Modifier.weight(1f))
                    if (on) {
                        Text(
                            "▲",
                            color = if (idx > 0) Color(0xFF0A84FF) else Color.DarkGray,
                            modifier = Modifier
                                .clickable(enabled = idx > 0) {
                                    Prefs.update { st ->
                                        val l = st.ccTiles.toMutableList()
                                        java.util.Collections.swap(l, idx, idx - 1)
                                        st.copy(ccTiles = l)
                                    }
                                }
                                .padding(10.dp),
                        )
                        Text(
                            "▼",
                            color = if (idx < enabled.size - 1) Color(0xFF0A84FF) else Color.DarkGray,
                            modifier = Modifier
                                .clickable(enabled = idx < enabled.size - 1) {
                                    Prefs.update { st ->
                                        val l = st.ccTiles.toMutableList()
                                        java.util.Collections.swap(l, idx, idx + 1)
                                        st.copy(ccTiles = l)
                                    }
                                }
                                .padding(10.dp),
                        )
                    }
                }
            }
        }

        // ---------- bannery + airpods ----------
        Card {
            Section("Další")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("iOS bannery", color = Color.White, fontSize = 16.sp)
                    Text("Nové notifikace při používání telefonu ve stylu iPhonu místo Androidích", color = Color.Gray, fontSize = 13.sp)
                }
                Switch(checked = settings.iosBanners, onCheckedChange = { on ->
                    Prefs.update { it.copy(iosBanners = on) }
                    cz.miuitweak.ioshell.overlay.Banners.applySystemHeadsUp(ctx, !on)
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AirPods okno", color = Color.White, fontSize = 16.sp)
                    Text("Po otevření pouzdra ukáže baterii sluchátek a pouzdra", color = Color.Gray, fontSize = 13.sp)
                }
                Switch(checked = settings.airpodsPopup, onCheckedChange = { on -> Prefs.update { it.copy(airpodsPopup = on) } })
            }
        }

        // ---------- tlacitka ----------
        Card {
            Section("Tlačítka dole")
            Label("Levé")
            ActionChips(settings.leftButton) { a -> Prefs.update { it.copy(leftButton = a) } }
            Label("Pravé")
            ActionChips(settings.rightButton) { a -> Prefs.update { it.copy(rightButton = a) } }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1C1C1E))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun Section(text: String) =
    Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)

@Composable
private fun Label(text: String) = Text(text, color = Color.Gray, fontSize = 13.sp)

@Composable
private fun PermissionRow(title: String, ok: Boolean?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            when (ok) {
                true -> "✓"
                false -> "Povolit"
                null -> "Otevřít"
            },
            color = if (ok == true) Color(0xFF30D158) else Color(0xFF0A84FF),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ActionChips(selected: QuickAction, onSelect: (QuickAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickAction.entries.forEach { a ->
            FilterChip(selected = selected == a, onClick = { onSelect(a) }, label = { Text(a.label) })
        }
    }
}

private fun openMiuiPermissions(ctx: android.content.Context) {
    val miui = Intent("miui.intent.action.APP_PERM_EDITOR")
        .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
        .putExtra("extra_pkgname", ctx.packageName)
    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
    runCatching { ctx.startActivity(miui) }.onFailure { ctx.startActivity(fallback) }
}

private fun openMiuiAutostart(ctx: android.content.Context) {
    val i = Intent().setComponent(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
    )
    runCatching { ctx.startActivity(i) }.onFailure { openMiuiPermissions(ctx) }
}
