package cz.miuitweak.ioshell.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import cz.miuitweak.ioshell.notif.NotifListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Immutable
data class Media(
    val pkg: String,
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
    val durationMs: Long,
    /** pozice v okamziku [updatedAt] */
    val positionMs: Long,
    val updatedAt: Long,
    val speed: Float,
) {
    fun positionNow(): Long {
        if (!playing) return positionMs
        val p = positionMs + ((SystemClock.elapsedRealtime() - updatedAt) * speed).toLong()
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p
    }
}

/** Co prave hraje (Spotify, YouTube…). Funguje diky opravneni k notifikacim. */
object MediaRepo {
    private val _media = MutableStateFlow<Media?>(null)
    val media: StateFlow<Media?> get() = _media

    private var msm: MediaSessionManager? = null
    private var controller: MediaController? = null
    private val main = Handler(Looper.getMainLooper())

    private val cb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() {
            controller = null
            publish()
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pick(list.orEmpty())
    }

    /** Volat, az je pripojeny NotifListener (jinak Android sezeni nevyda). */
    fun start(ctx: Context) {
        appCtx = ctx.applicationContext
        if (msm != null) {
            refresh()
            return
        }
        val m = ctx.applicationContext.getSystemService(MediaSessionManager::class.java) ?: return
        val comp = ComponentName(ctx, NotifListener::class.java)
        runCatching {
            m.addOnActiveSessionsChangedListener(sessionsListener, comp, main)
            msm = m
            val list = m.getActiveSessions(comp)
            android.util.Log.d("MediaRepo", "start ok, sessions=${list.map { it.packageName }}")
            pick(list)
        }.onFailure { android.util.Log.e("MediaRepo", "start failed", it) }
    }

    private var appCtx: Context? = null

    /** Znovu se zeptat systemu (MIUI obcas neposle zmenu). */
    fun refresh() {
        val m = msm ?: return
        val c = appCtx ?: return
        runCatching { pick(m.getActiveSessions(ComponentName(c, NotifListener::class.java))) }
    }

    /** Prehravac primo z notifikace hudby (ta nese odkaz na sezeni) – nejspolehlivejsi cesta na MIUI. */
    fun fromToken(ctx: Context, token: android.media.session.MediaSession.Token) {
        if (controller?.sessionToken == token) {
            publish()
            return
        }
        val c = runCatching { MediaController(ctx.applicationContext, token) }.getOrNull() ?: return
        // kdyz hraje neco jineho, neprepinat na pozastavene
        val cur = controller
        if (cur != null && cur.playbackState?.state == PlaybackState.STATE_PLAYING &&
            c.playbackState?.state != PlaybackState.STATE_PLAYING
        ) return
        controller?.unregisterCallback(cb)
        controller = c
        c.registerCallback(cb, main)
        publish()
    }

    private fun pick(list: List<MediaController>) {
        // prednost ma to, co prave hraje
        // pozastavene prehravani Android obcas prestane hlasit jako aktivni -> drzet posledni (jako iOS u pauzy)
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull()
            ?: controller?.takeIf { it.playbackState != null && it.metadata != null }
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(cb)
            controller = best
            best?.registerCallback(cb, main)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val md = c?.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        android.util.Log.d("MediaRepo", "publish ${c?.packageName} title=$title state=${c?.playbackState?.state}")
        if (c == null || md == null || title.isNullOrBlank()) {
            _media.value = null
            return
        }
        val st = c.playbackState
        val art: Bitmap? = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val prev = _media.value
        _media.value = Media(
            pkg = c.packageName,
            title = title,
            artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
            // stejny obrazek nekonvertovat znovu
            art = if (prev != null && prev.title == title && prev.art != null && art != null) prev.art else art?.asImageBitmap(),
            playing = st?.state == PlaybackState.STATE_PLAYING,
            durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
            positionMs = st?.position ?: 0,
            updatedAt = st?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = st?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = controller?.transportControls?.skipToNext()
    fun previous() = controller?.transportControls?.skipToPrevious()
    fun seekTo(ms: Long) = controller?.transportControls?.seekTo(ms)
}
