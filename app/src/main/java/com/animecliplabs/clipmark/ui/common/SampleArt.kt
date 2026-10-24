package com.animecliplabs.clipmark.ui.common

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.random.Random

/**
 * A generated "anime sky" used when there is no photo (editing a preset, home preview).
 * Generated rather than bundled: no copyrighted art, no APK weight, and it has both a bright
 * sky and dark foreground, so readability features are visibly exercised.
 */
object SampleArt {
    const val WIDTH = 1080
    const val HEIGHT = 1350

    val bitmap: Bitmap by lazy { draw() }

    private fun draw(): Bitmap {
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = WIDTH.toFloat()
        val h = HEIGHT.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.72f,
            intArrayOf(0xFF1E1B4B.toInt(), 0xFF5B3F9E.toInt(), 0xFFE9829F.toInt(), 0xFFFFD29A.toInt()),
            floatArrayOf(0f, 0.38f, 0.75f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val rnd = Random(7)
        paint.color = 0xCCFFFFFF.toInt()
        repeat(60) {
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.35f, 1.5f + rnd.nextFloat() * 2.5f, paint)
        }

        paint.color = 0x66FFF1D6
        paint.maskFilter = BlurMaskFilter(90f, BlurMaskFilter.Blur.NORMAL)
        c.drawCircle(w * 0.62f, h * 0.55f, 170f, paint)
        paint.maskFilter = null
        paint.color = 0xFFFFF4DF.toInt()
        c.drawCircle(w * 0.62f, h * 0.55f, 105f, paint)

        fun ridge(color: Int, baseY: Float, amp: Float, seed: Int) {
            val r = Random(seed)
            val path = Path().apply {
                moveTo(0f, h)
                lineTo(0f, baseY)
                var x = 0f
                while (x <= w) {
                    lineTo(x, baseY - r.nextFloat() * amp)
                    x += w / 9f
                }
                lineTo(w, baseY)
                lineTo(w, h)
                close()
            }
            paint.color = color
            c.drawPath(path, paint)
        }
        ridge(0xFF8A6BB8.toInt(), h * 0.66f, 140f, 3)
        ridge(0xFF4A3778.toInt(), h * 0.76f, 120f, 11)
        ridge(0xFF231A3D.toInt(), h * 0.87f, 90f, 23)
        return bmp
    }
}
