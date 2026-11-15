package co.nehood.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import co.nehood.app.R
import co.nehood.app.data.AppException
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Prepara la foto antes de guardarla (RF-17, RNF-02):
 * corrige la rotación de la cámara, la reduce a máximo 1280 px y la comprime en JPEG.
 */
object ImageCompressor {

    private const val MAX_SIDE = 1280
    private const val QUALITY = 80

    fun compress(context: Context, uri: Uri): ByteArray {
        val resolver = context.contentResolver

        // 1. Leer solo el tamaño para decidir cuánto reducir al decodificar (ahorra memoria)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) throw AppException(R.string.err_image)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2

        // 2. Decodificar reducida
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw AppException(R.string.err_image)

        // 3. Rotar según el EXIF de la cámara
        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        // 4. Escalar al tamaño final y rotar en un solo paso
        val scale = min(1f, MAX_SIDE.toFloat() / max(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation)
        }
        val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        return ByteArrayOutputStream().use { out ->
            result.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            out.toByteArray()
        }
    }
}
