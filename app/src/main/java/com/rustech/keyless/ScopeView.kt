package com.rustech.keyless

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View

/**
 * A simple radar-style "scope" that plots detected iBeacons by estimated
 * distance (radius) and a stable pseudo-angle derived from the device address.
 * Purely a visual aid -- the angle has no real-world directional meaning since
 * a single BLE radio can't determine bearing, only distance.
 */
class ScopeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2ECC71")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        alpha = 90
    }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2ECC71")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        alpha = 200
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF7043")
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
    }

    /** Max distance, in meters, mapped to the outer edge of the scope. */
    var maxRangeMeters: Float = 15f

    private var beacons: List<IBeacon> = emptyList()
    private var sweepAngle = 0f
    private val handler = Handler(Looper.getMainLooper())
    private var animating = false

    private val tick = object : Runnable {
        override fun run() {
            sweepAngle = (sweepAngle + 4f) % 360f
            invalidate()
            handler.postDelayed(this, 30)
        }
    }

    fun updateBeacons(list: List<IBeacon>) {
        beacons = list
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animating) {
            animating = true
            handler.post(tick)
        }
    }

    override fun onDetachedFromWindow() {
        animating = false
        handler.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f - 16f
        if (radius <= 0) return

        // Concentric rings, every quarter of the max range.
        for (i in 1..4) {
            canvas.drawCircle(cx, cy, radius * i / 4f, ringPaint)
        }

        // Rotating sweep line -- purely cosmetic, gives the "scanning" feel.
        val rad = Math.toRadians(sweepAngle.toDouble())
        val sx = cx + radius * Math.cos(rad).toFloat()
        val sy = cy + radius * Math.sin(rad).toFloat()
        canvas.drawLine(cx, cy, sx, sy, sweepPaint)

        // Center dot = this phone.
        canvas.drawCircle(cx, cy, 8f, dotPaint)

        // Plot each detected beacon: radius from estimated distance, angle is
        // just a stable spread so dots don't overlap.
        beacons.forEachIndexed { index, beacon ->
            val dist = beacon.estimatedDistanceMeters().coerceIn(0.2, maxRangeMeters.toDouble())
            val r = (dist / maxRangeMeters) * radius
            val angle = (beacon.address.hashCode() + index * 37) % 360
            val a = Math.toRadians(angle.toDouble())
            val bx = cx + (r * Math.cos(a)).toFloat()
            val by = cy + (r * Math.sin(a)).toFloat()
            canvas.drawCircle(bx, by, 10f, dotPaint)
            canvas.drawText(beacon.minor.toString(), bx + 14f, by, labelPaint)
        }
    }
}
