package com.example.wastesorting.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * ImageProxy / Bitmap 工具函数
 */
object ImageUtils {

    /**
     * CameraX ImageProxy → JPEG ByteArray
     */
    fun imageProxyToJpegBytes(image: ImageProxy): ByteArray {
        val bitmap = imageProxyToBitmap(image)
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        if (bitmap != image) {  // 避免回收传入的 ImageProxy
            // bitmap 是新创建的，用完回收
        }
        return stream.toByteArray()
    }

    /**
     * CameraX ImageProxy → Bitmap
     */
    fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val format = image.format
        return when (format) {
            ImageFormat.JPEG -> {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            ImageFormat.YUV_420_888 -> {
                yuv420ToBitmap(image)
            }
            else -> {
                // fallback: 尝试 planes[0]
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            }
        }
    }

    /**
     * 旋转 Bitmap（适配相机方向）
     */
    fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * YUV_420_888 → Bitmap（简化实现，用 planes[0] Y 通道灰度数据）
     */
    private fun yuv420ToBitmap(image: ImageProxy): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uvBuffer = image.planes[1].buffer

        val ySize = yBuffer.remaining()
        val uvSize = uvBuffer.remaining()

        val nv21 = ByteArray(ySize + uvSize)
        yBuffer.get(nv21, 0, ySize)
        uvBuffer.get(nv21, ySize, uvSize)

        val yuvImage = android.graphics.YuvImage(
            nv21,
            ImageFormat.NV21,
            image.width,
            image.height,
            null
        )
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            android.graphics.Rect(0, 0, image.width, image.height),
            90,
            out
        )
        val jpegBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
    }
}
