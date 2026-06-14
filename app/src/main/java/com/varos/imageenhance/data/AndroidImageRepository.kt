package com.varos.imageenhance.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import com.varos.imageenhance.domain.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.max

/**
 * [ImageRepository] backed by ContentResolver, EXIF and MediaStore.
 *
 * Two framework concerns are handled here so the rest of the app never has to:
 *  - **Memory:** large photos are decoded with inSampleSize so we never inflate
 *    a 50 MP image into RAM; the result is bounded to [maxEdge] on its long side.
 *  - **Rotation:** the EXIF orientation tag is read and baked into the bitmap so
 *    sideways phone photos display (and process) upright.
 */
class AndroidImageRepository(
    private val context: Context,
) : ImageRepository {

    private val resolver get() = context.contentResolver

    override fun createCaptureUri(): Uri {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    override suspend fun loadBitmap(uri: Uri, maxEdge: Int): Bitmap =
        withContext(Dispatchers.IO) {
            // Pass 1: read bounds only to compute a safe downsample factor.
            // Note: with inJustDecodeBounds, decodeStream always returns null and
            // instead fills bounds.outWidth/outHeight — so we must NOT treat that
            // null as a failure. We only guard the stream itself being unopenable.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val opened = resolver.openInputStream(uri)
                ?: throw IOException("Cannot open image stream for $uri")
            opened.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IOException("Unsupported or unreadable image for $uri")
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdge)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = (resolver.openInputStream(uri)
                ?: throw IOException("Cannot open image stream for $uri"))
                .use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw IOException("Failed to decode image for $uri")

            // EXIF orientation is best-effort: some providers / formats (PNG,
            // WebP, picker pipes) can't supply it, and that must never fail the
            // load — we just fall back to the decoded orientation.
            val rotation = runCatching {
                resolver.openInputStream(uri)?.use { readExifRotation(it) } ?: 0
            }.getOrDefault(0)
            applyRotation(decoded, rotation)
        }

    override suspend fun saveToGallery(bitmap: Bitmap, displayName: String): Uri =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ImageEnhance")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val item = resolver.insert(collection, values)
                ?: throw IOException("Failed to create MediaStore record")
            resolver.openOutputStream(item)?.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
            } ?: throw IOException("Failed to open output stream")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(item, values, null, null)
            }
            item
        }

    private fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        var longest = max(width, height)
        while (longest / 2 >= maxEdge) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private fun readExifRotation(input: java.io.InputStream): Int =
        when (ExifInterface(input).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    private fun applyRotation(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true,
        )
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }
}
