package cz.miuitweak.ioshell.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import cz.miuitweak.ioshell.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/** Tapeta oriznuta na velikost displeje + mala rozmazana kopie pro "matne sklo". */
class WallpaperImages(val sharp: ImageBitmap, val blurred: ImageBitmap)

object Wallpaper {
    private const val BLUR_DOWNSCALE = 8

    fun file(ctx: Context) = File(ctx.filesDir, "wallpaper.img")

    private var cacheKey: String? = null
    private var cache: WallpaperImages? = null

    suspend fun import(ctx: Context, uri: Uri) = withContext(Dispatchers.IO) {
        ctx.contentResolver.openInputStream(uri)!!.use { input ->
            file(ctx).outputStream().use { input.copyTo(it) }
        }
        Prefs.update { it.copy(wallpaperVersion = System.currentTimeMillis()) }
    }

    fun reset(ctx: Context) {
        file(ctx).delete()
        Prefs.update { it.copy(wallpaperVersion = System.currentTimeMillis()) }
    }

    suspend fun load(ctx: Context, width: Int, height: Int, version: Long): WallpaperImages =
        withContext(Dispatchers.Default) {
            val key = "${width}x$height@$version"
            cache?.takeIf { cacheKey == key }?.let { return@withContext it }

            val src = decode(ctx, width, height)
            val sharp = centerCrop(src, width, height)
            val small = Bitmap.createScaledBitmap(
                sharp, max(1, width / BLUR_DOWNSCALE), max(1, height / BLUR_DOWNSCALE), true,
            ).copy(Bitmap.Config.ARGB_8888, true)
            boxBlur(small, radius = 3, passes = 3)

            WallpaperImages(sharp.asImageBitmap(), small.asImageBitmap()).also {
                cache = it
                cacheKey = key
            }
        }

    private fun decode(ctx: Context, w: Int, h: Int): Bitmap {
        val f = file(ctx)
        if (f.exists()) {
            val bmp = runCatching {
                // ImageDecoder sam otoci fotku podle EXIF
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(f)) { dec, info, _ ->
                    var sample = 1
                    while (info.size.width / (sample * 2) >= w && info.size.height / (sample * 2) >= h) sample *= 2
                    dec.setTargetSampleSize(sample)
                    dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }.getOrNull()
            if (bmp != null) return bmp
        }
        return BitmapFactory.decodeResource(ctx.resources, R.drawable.default_wallpaper)
    }

    private fun centerCrop(src: Bitmap, w: Int, h: Int): Bitmap {
        val scale = max(w.toFloat() / src.width, h.toFloat() / src.height)
        val m = Matrix().apply {
            postScale(scale, scale)
            postTranslate((w - src.width * scale) / 2f, (h - src.height * scale) / 2f)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** 3x box blur ~ gauss. Bezi na malem obrazku (1/8), takze je to rychle. */
    private fun boxBlur(bmp: Bitmap, radius: Int, passes: Int) {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        val tmp = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        repeat(passes) {
            pass(px, tmp, w, h, radius, horizontal = true)
            pass(tmp, px, w, h, radius, horizontal = false)
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val outer = if (horizontal) h else w
        val inner = if (horizontal) w else h
        val div = 2 * r + 1
        fun idx(o: Int, i: Int) = if (horizontal) o * w + i else i * w + o
        fun clamp(i: Int) = i.coerceIn(0, inner - 1)
        for (o in 0 until outer) {
            var sr = 0
            var sg = 0
            var sb = 0
            for (k in -r..r) {
                val c = src[idx(o, clamp(k))]
                sr += (c shr 16) and 0xFF
                sg += (c shr 8) and 0xFF
                sb += c and 0xFF
            }
            for (i in 0 until inner) {
                dst[idx(o, i)] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val out = src[idx(o, clamp(i - r))]
                val inn = src[idx(o, clamp(i + r + 1))]
                sr += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                sg += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                sb += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }
}
