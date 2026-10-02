package com.aicamera.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * Free, fully offline, instant photo enhancement.
 * Applies auto contrast/levels, mild saturation boost and an unsharp-mask
 * style sharpen pass so zoomed-in / low-light shots look clearer without
 * any cloud call. This keeps colors natural (no AI hallucination) because
 * it only re-maps existing pixel values, it never invents detail.
 */
object ImageEnhancer {

    fun autoEnhance(source: Bitmap): Bitmap {
        val contrastBoosted = applyColorMatrix(source, buildEnhanceMatrix())
        return sharpen(contrastBoosted)
    }

    private fun buildEnhanceMatrix(): ColorMatrix {
        val saturation = ColorMatrix().apply { setSaturation(1.15f) }

        val contrast = 1.12f
        val brightness = 6f
        val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val combined = ColorMatrix()
        combined.postConcat(saturation)
        combined.postConcat(contrastMatrix)
        return combined
    }

    private fun applyColorMatrix(src: Bitmap, matrix: ColorMatrix): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    /** Simple 3x3 unsharp-mask convolution to recover edge clarity after zoom/upscale. */
    private fun sharpen(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 3 || h < 3) return src

        val kernel = floatArrayOf(
            0f, -1f, 0f,
            -1f, 5f, -1f,
            0f, -1f, 0f
        )

        val input = IntArray(w * h)
        src.getPixels(input, 0, w, 0, 0, w, h)
        val output = IntArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    output[y * w + x] = input[y * w + x]
                    continue
                }
                var r = 0f; var g = 0f; var b = 0f
                var k = 0
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val px = input[(y + ky) * w + (x + kx)]
                        val weight = kernel[k++]
                        r += ((px shr 16) and 0xFF) * weight
                        g += ((px shr 8) and 0xFF) * weight
                        b += (px and 0xFF) * weight
                    }
                }
                val rr = r.coerceIn(0f, 255f).toInt()
                val gg = g.coerceIn(0f, 255f).toInt()
                val bb = b.coerceIn(0f, 255f).toInt()
                output[y * w + x] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(output, 0, w, 0, 0, w, h)
        return result
    }
}
