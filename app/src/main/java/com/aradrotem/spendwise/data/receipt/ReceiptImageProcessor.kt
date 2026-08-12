package com.aradrotem.spendwise.data.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ProcessedReceiptImage(val file: File, val mimeType: String)

// Downsamples and JPEG-compresses a picked/captured receipt image into a new app-private cache
// file, so Compose never has to hold a full-resolution 10-20MP bitmap and uploads stay small.
// The user's original file (e.g. a gallery photo) is never opened for writing - only read - so
// it's never mutated.
class ReceiptImageProcessor(private val context: Context) {

    suspend fun process(sourceUri: Uri): Result<ProcessedReceiptImage> = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@withContext Result.failure(ReceiptError.UnreadableImage)
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION_PX)
            }
            val decoded = context.contentResolver.openInputStream(sourceUri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return@withContext Result.failure(ReceiptError.UnreadableImage)

            val scaled = downscaleIfNeeded(decoded, MAX_DIMENSION_PX)

            val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
            val outFile = File(receiptsDir, "${UUID.randomUUID()}.jpg")
            FileOutputStream(outFile).use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }

            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()

            Result.success(ProcessedReceiptImage(file = outFile, mimeType = "image/jpeg"))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Result.failure(ReceiptError.UnreadableImage)
        }
    }

    private fun downscaleIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longEdge
        val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    companion object {
        // Long-edge cap: comfortably readable for a receipt photo while keeping uploads small.
        const val MAX_DIMENSION_PX = 1600
        const val JPEG_QUALITY = 82
    }
}

// Pure/testable: the power-of-two downsample factor BitmapFactory.Options.inSampleSize expects,
// chosen so the bounded decode is already close to (but not below) maxDimension on its long edge -
// the final precise resize happens afterward in downscaleIfNeeded.
fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var sampleSize = 1
    var w = width
    var h = height
    while (w / 2 >= maxDimension || h / 2 >= maxDimension) {
        w /= 2
        h /= 2
        sampleSize *= 2
    }
    return sampleSize
}
