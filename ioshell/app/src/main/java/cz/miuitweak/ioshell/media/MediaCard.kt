package cz.miuitweak.ioshell.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.miuitweak.ioshell.ui.Ios
import cz.miuitweak.ioshell.ui.glass
import kotlinx.coroutines.delay

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun rememberPosition(m: Media): Long {
    val pos by produceState(m.positionNow(), m) {
        while (true) {
            value = m.positionNow()
            delay(500)
        }
    }
    return pos
}

@Composable
fun AlbumArt(art: ImageBitmap?, size: Dp, radius: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(Color(0xFF3A3A3C)),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(size * 0.45f))
        }
    }
}

/** Prehravac na zamykacce (jako iOS "Now Playing"). Klepnuti na obal = velky obal pres zamykacku. */
@Composable
fun MediaCard(m: Media, onArtClick: () -> Unit, modifier: Modifier = Modifier) {
    val pos = rememberPosition(m)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .glass(RoundedCornerShape(Ios.CardRadius))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(
                m.art, 52.dp, 9.dp,
                Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onArtClick),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (m.artist.isNotBlank()) {
                    Text(m.artist, color = Ios.TextSecondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Progress(m, pos)
        Controls(m)
    }
}

@Composable
private fun Progress(m: Media, pos: Long) {
    if (m.durationMs <= 0) return
    val f = (pos.toFloat() / m.durationMs).coerceIn(0f, 1f)
    Column(Modifier.padding(top = 12.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.25f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(f)
                    .height(5.dp)
                    .background(Color.White.copy(alpha = 0.9f)),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(fmt(pos), color = Ios.TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("-" + fmt(m.durationMs - pos), color = Ios.TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Controls(m: Media) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CtrlButton(Icons.Rounded.FastRewind, 36.dp) { MediaRepo.previous() }
        CtrlButton(if (m.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 46.dp) { MediaRepo.playPause() }
        CtrlButton(Icons.Rounded.FastForward, 36.dp) { MediaRepo.next() }
    }
}

@Composable
private fun CtrlButton(icon: ImageVector, size: Dp, onClick: () -> Unit) {
    Icon(
        icon, null,
        tint = Color.White,
        modifier = Modifier
            .size(size + 12.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(6.dp),
    )
}

/**
 * "Velky obal": pozadi = rozmazany obal alba, uprostred velky obal + prehravac.
 * Klepnuti na obal vrati normalni zamykacku.
 */
@Composable
fun BigArtBackground(m: Media) {
    val art = m.art ?: return
    Box(Modifier.fillMaxSize()) {
        // maly obrazek roztazeny pres celou obrazovku = levne rozmazani
        Image(
            art, null,
            Modifier
                .fillMaxSize()
                .blur(60.dp),
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Low,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
        )
    }
}

@Composable
fun BigArtContent(m: Media, onArtClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        AlbumArt(
            m.art, 300.dp, 16.dp,
            Modifier
                .shadow(24.dp, RoundedCornerShape(16.dp))
                .clickable(remember { MutableInteractionSource() }, null, onClick = onArtClick),
        )
        Spacer(Modifier.height(24.dp))
        MediaCard(m, onArtClick)
    }
}
