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
 * Radar-style scope with green glow rings, animated sweep line with fade trail,
 * and colour-coded beacon dots. Angle is a stable pseudo-bearing derived from
 * the device address — it has no real directional meaning, just spreads dots.
 */
class ScopeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── Paints ──────────────────────────────────────────────────────────────

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#080D0F")
        style = Paint.Style.FILL
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.parseColor("#1F4A35")   // dim green border
    }

    private val ringGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.parseColor("#2A5E42")
    }

    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
        color = Color.parseColor("#152E22")
    }

    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#00E87A")
        alpha = 220
    }

    private val selfPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E87A")
        style = Paint.Style.FILL
    }

    private val selfRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E87A")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        alpha = 80
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6B35")
        style = Paint.Style.FILL
    }

    private val dotGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6B35")
        style = Paint.Style.FILL
        alpha = 50
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8F5EE")
        textSize = 22f
        typeface = android.graphics.Typeface.MONOSPACE
    }

    private val labelSmallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7A9A86")
        textSize = 17f
        typeface = android.graphics.Typeface.MONOSPACE
    }

    private val rangeLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A5E42")
        textSize = 18f
        typeface = android.graphics.Typeface.MONOSPACE
    }

    // ── State ────────────────────────────────────────────────────────────────

    var maxRangeMeters: Float = 15f
    private var beacons: List<IBeacon> = emptyList()
    private var sweepAngle = 0f
    private val handler = Handler(Looper.getMainLooper())
    private var animating = false

    private val tick = object : Runnable {
        override fun run() {
            sweepAngle = (sweepAngle + 3f) % 360f
            invalidate()
            handler.postDelayed(this, 25)
        }
    }

    fun updateBeacons(list: List<IBeacon>) {
        beacons = list
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animating) { animating = true; handler.post(tick) }
    }

    override fun onDetachedFromWindow() {
        animating = false
        handler.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    // ── Draw ─────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f - 24f
        if (radius <= 0) return

        // Background fill
        canvas.drawCircle(cx, cy, radius + 4f, bgPaint)

        // Crosshair lines (very subtle)
        canvas.drawLine(cx - radius, cy, cx + radius, cy, crosshairPaint)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, crosshairPaint)

        // Concentric rings (4 rings = 25% 50% 75% 100% of max range)
        for (i in 1..4) {
            val r = radius * i / 4f
            val paint = if (i == 4) ringGlowPaint else ringPaint
            canvas.drawCircle(cx, cy, r, paint)

            // Range label on outermost ring only
            if (i < 4) {
                val labelMeters = (maxRangeMeters * i / 4).toInt()
                canvas.drawText("${labelMeters}m", cx + r + 4f, cy - 4f, rangeLabelPaint)
            }
        }

        // Sweep trail (fading arc behind the sweep line using SweepGradient)
        drawSweepTrail(canvas, cx, cy, radius)

        // Sweep line
        val sweepRad = Math.toRadians(sweepAngle.toDouble())
        val sx = cx + radius * Math.cos(sweepRad).toFloat()
        val sy = cy + radius * Math.sin(sweepRad).toFloat()
        canvas.drawLine(cx, cy, sx, sy, sweepPaint)

        // Center dot = this phone
        canvas.drawCircle(cx, cy, 10f, selfRingPaint)
        canvas.drawCircle(cx, cy, 5f, selfPaint)

        // Beacon dots
        beacons.forEachIndexed { index: Int, beacon: IBeacon ->
            val dist = beacon.estimatedDistanceMeters().coerceIn(0.3, maxRangeMeters.toDouble())
            val r = (dist / maxRangeMeters) * radius
            val angle = (beacon.address.hashCode() + index * 37) % 360
            val a = Math.toRadians(angle.toDouble())
            val bx = cx + (r * Math.cos(a)).toFloat()
            val by = cy + (r * Math.sin(a)).toFloat()

            // Glow halo
            dotGlowPaint.alpha = 60
            canvas.drawCircle(bx, by, 18f, dotGlowPaint)
            dotGlowPaint.alpha = 35
            canvas.drawCircle(bx, by, 26f, dotGlowPaint)

            // Main dot
            canvas.drawCircle(bx, by, 8f, dotPaint)

            // Label: minor number + distance
            val dist1dp = if (dist < 0) "?" else "%.1f".format(dist)
            canvas.drawText(beacon.minor.toString(), bx + 14f, by - 4f, labelPaint)
            canvas.drawText("${dist1dp}m", bx + 14f, by + 16f, labelSmallPaint)
        }
    }

    private fun drawSweepTrail(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        // Draw 60° arc behind sweep as a series of lines with decreasing alpha
        val trailLength = 60f
        val steps = 20
        for (i in 0 until steps) {
            val fraction = i.toFloat() / steps
            val trailAngle = sweepAngle - trailLength * (1f - fraction)
            val rad = Math.toRadians(trailAngle.toDouble())
            val tx = cx + radius * Math.cos(rad).toFloat()
            val ty = cy + radius * Math.sin(rad).toFloat()
            val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
                color = Color.parseColor("#00E87A")
                alpha = (fraction * 60).toInt()
            }
            canvas.drawLine(cx, cy, tx, ty, trailPaint)
        }
    }
}
