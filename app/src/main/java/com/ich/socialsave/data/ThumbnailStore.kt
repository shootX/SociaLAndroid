package com.ich.socialsave.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File

/** JPEG thumbnails kept in the app's private storage, one file per post. */
class ThumbnailStore(context: Context) {
    val directory: File = File(context.filesDir, "thumbnails").apply { mkdirs() }

    fun file(name: String?): File? = name?.let { File(directory, it) }?.takeIf { it.exists() }

    /** Writes a new file for the post; the name changes each time so image caches never show a stale picture. */
    fun save(postId: String, jpeg: ByteArray): String? {
        val name = "$postId-${System.currentTimeMillis()}.jpg"
        return runCatching {
            File(directory, name).writeBytes(jpeg)
            name
        }.getOrNull()
    }

    fun delete(name: String?) {
        name?.let { File(directory, it).delete() }
    }

    fun deleteAll() {
        directory.listFiles()?.forEach { it.delete() }
    }

    companion object {
        private const val MAX_PIXELS = 900

        /** Decodes any image the web gives us and re-encodes it as a small JPEG, or null if it isn't an image. */
        fun jpegThumbnail(bytes: ByteArray, maxPixels: Int = MAX_PIXELS, quality: Int = 82): ByteArray? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPixels) sample *= 2
            val decoded = BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: return null

            val scale = minOf(1f, maxPixels.toFloat() / maxOf(decoded.width, decoded.height))
            val bitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true,
                ).also { if (it !== decoded) decoded.recycle() }
            } else {
                decoded
            }

            return ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                bitmap.recycle()
                out.toByteArray()
            }
        }
    }
}
