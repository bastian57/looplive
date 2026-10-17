package com.daftech.looplive

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.Random

/**
 * Micro-trick anti-deteksi loop: menabur titik noise acak yang berubah
 * tiap ~200ms di atas video. Tak kasat mata (alpha sangat rendah),
 * tapi membuat hash tiap frame berbeda antar putaran loop.
 */
class NoiseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.WHITE
        alpha = 16
    }
    private val rnd = Random()
    private val pts = FloatArray(320)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w > 0 && h > 0 && isShown) {
            var i = 0
            while (i < pts.size) {
                pts[i] = rnd.nextFloat() * w
                pts[i + 1] = rnd.nextFloat() * h
                i += 2
            }
            canvas.drawPoints(pts, paint)
        }
        postInvalidateDelayed(200)
    }
}
