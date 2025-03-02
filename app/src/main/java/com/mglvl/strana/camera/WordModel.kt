package com.mglvl.strana.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

data class Word(
    val word: String,
    val posTag: String,
    val bounds: android.graphics.Rect? = null
)

// Helper function to convert ImageProxy to Bitmap
fun ImageProxy.toScaledBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)

    // Set inSampleSize to downsample the image during decoding
    val options = BitmapFactory.Options().apply {
        inSampleSize = 4  // Downsample by factor of 4
    }

    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

    // Rotate the bitmap if needed based on the image rotation
    val rotationDegrees = imageInfo.rotationDegrees
    val rotatedBitmap = if (rotationDegrees != 0) {
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }

    // Scale down the bitmap to a reasonable size to avoid "Canvas: trying to draw too large bitmap" error
    return scaleBitmap(rotatedBitmap, 800)  // Reduced from 1280 to 800
}

// Helper function to scale down a bitmap while maintaining aspect ratio
fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height

    // If the bitmap is already smaller than the max dimension, return it as is
    if (width <= maxDimension && height <= maxDimension) {
        return bitmap
    }

    // Calculate the scaling factor
    val scaleFactor = if (width > height) {
        maxDimension.toFloat() / width.toFloat()
    } else {
        maxDimension.toFloat() / height.toFloat()
    }

    // Calculate new dimensions
    val newWidth = (width * scaleFactor).toInt()
    val newHeight = (height * scaleFactor).toInt()

    // Create and return the scaled bitmap
    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
}
