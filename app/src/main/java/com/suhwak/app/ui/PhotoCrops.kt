package com.suhwak.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.suhwak.app.ml.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Cuts the row of a word out of the photo it was read from, with the English word framed in blue and the
 * meaning in green, so a found word can be checked against the book at a glance.
 */
object PhotoCrops {
    private const val WORD_COLOR = 0xFF1E6BFF.toInt()
    private const val MEANING_COLOR = 0xFF1FA34A.toInt()
    private const val DROPPED_COLOR = 0xFFE5484D.toInt()

    /** One strip to cut: the word's box, the meaning's box (if on its own line) and whether it was left out. */
    data class Spot(val key: String, val word: Box, val meaning: Box?, val dropped: Boolean = false)

    /**
     * Opens the photo upright (as the recognizer saw it, [width] × [height]) at most [maxSide] pixels on its
     * longest side. Returns the picture and its scale against the recognizer's pixels.
     */
    suspend fun open(context: Context, uri: Uri, width: Int, height: Int, maxSide: Int = 2000): Pair<Bitmap, Float>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val longest = max(width, height).coerceAtLeast(1)
                var sample = 1
                while (longest / (sample * 2) >= maxSide) sample *= 2
                val bmp = if (Build.VERSION.SDK_INT >= 28) {
                    val src = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                    android.graphics.ImageDecoder.decodeBitmap(src) { d, _, _ ->
                        d.setTargetSampleSize(sample)
                        d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    val raw = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }!!
                    val rotation = context.contentResolver.openInputStream(uri)!!.use {
                        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                            else -> 0f
                        }
                    }
                    if (rotation == 0f) raw
                    else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation) }, true)
                }
                bmp to bmp.width / width.toFloat()
            }.getOrNull()
        }

    /** Cuts every spot's row out of [photo] (scaled by [scale]), at most [maxWidth] pixels wide. */
    suspend fun cut(photo: Bitmap, scale: Float, spots: List<Spot>, maxWidth: Int = 720): Map<String, ImageBitmap> =
        withContext(Dispatchers.Default) {
            spots.mapNotNull { s -> runCatching { s.key to strip(photo, scale, s, maxWidth) }.getOrNull() }.toMap()
        }

    private fun strip(photo: Bitmap, scale: Float, s: Spot, maxWidth: Int): ImageBitmap {
        val boxes = listOfNotNull(s.word, s.meaning)
        val h = (s.word.bottom - s.word.top).coerceAtLeast(10)
        val l = ((boxes.minOf { it.left } - h * 0.6f) * scale).roundToInt().coerceIn(0, photo.width - 1)
        val r = ((boxes.maxOf { it.right } + h * 0.6f) * scale).roundToInt().coerceIn(l + 1, photo.width)
        val t = ((boxes.minOf { it.top } - h * 0.4f) * scale).roundToInt().coerceIn(0, photo.height - 1)
        val b = ((boxes.maxOf { it.bottom } + h * 0.4f) * scale).roundToInt().coerceIn(t + 1, photo.height)
        val k = min(1f, maxWidth / (r - l).toFloat())
        val out = Bitmap.createBitmap(((r - l) * k).roundToInt().coerceAtLeast(1), ((b - t) * k).roundToInt().coerceAtLeast(1), Bitmap.Config.RGB_565)
        val canvas = Canvas(out)
        canvas.scale(k, k)
        canvas.drawBitmap(photo, -l.toFloat(), -t.toFloat(), null)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f / k }
        fun frame(box: Box, color: Int) {
            paint.color = color
            val pad = h * 0.12f * scale
            canvas.drawRoundRect(
                RectF(box.left * scale - l - pad, box.top * scale - t - pad, box.right * scale - l + pad, box.bottom * scale - t + pad),
                6f / k, 6f / k, paint,
            )
        }
        frame(s.word, if (s.dropped) DROPPED_COLOR else WORD_COLOR)
        s.meaning?.let { frame(it, MEANING_COLOR) }
        return out.asImageBitmap()
    }

    /**
     * The whole photo, at most [maxSide] pixels, with found words framed in green and left-out ones in red,
     * to see at a glance whether every headword on the page was caught.
     */
    fun map(photo: Bitmap, scale: Float, spots: List<Spot>, maxSide: Int = 1600): ImageBitmap {
        val k = min(1f, maxSide / max(photo.width, photo.height).toFloat())
        val out = Bitmap.createBitmap((photo.width * k).roundToInt().coerceAtLeast(1), (photo.height * k).roundToInt().coerceAtLeast(1), Bitmap.Config.RGB_565)
        val canvas = Canvas(out)
        canvas.scale(k, k)
        canvas.drawBitmap(photo, 0f, 0f, null)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f / k }
        spots.forEach { s ->
            paint.color = if (s.dropped) DROPPED_COLOR else MEANING_COLOR
            val w = s.word
            canvas.drawRect(w.left * scale, w.top * scale, w.right * scale, w.bottom * scale, paint)
        }
        return out.asImageBitmap()
    }
}
