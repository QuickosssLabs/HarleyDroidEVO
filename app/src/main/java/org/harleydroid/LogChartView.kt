//
// HarleyDroid EVO: evolution of HarleyDroid (J1850 analyser for Android).
//
// Copyright (C) 2026 Quickosss - HarleyDroid EVO (maintenance / modernization)
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// See COPYING for the full licence text.
//
package org.harleydroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.max

/**
 * Dual-series time chart (left + right axis) with horizontal scrub.
 */
class LogChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface ScrubListener {
        fun onScrub(timeMs: Long, leftValue: Float?, rightValue: Float?)
        fun onScrubEnded()
    }

    var scrubListener: ScrubListener? = null

    private var startMs = 0L
    private var endMs = 1L
    private var leftSeries: FloatSeries? = null
    private var rightSeries: FloatSeries? = null
    private var scrubMs: Long? = null

    private val plot = RectF()
    private val leftPath = Path()
    private val rightPath = Path()

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = ContextCompat.getColor(context, R.color.hd_divider)
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = ContextCompat.getColor(context, R.color.hd_outline)
    }
    private val leftLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val rightLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(dp(8f), dp(6f)), 0f)
    }
    private val scrubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = ContextCompat.getColor(context, R.color.hd_on_surface_60)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(11f)
        color = ContextCompat.getColor(context, R.color.hd_on_surface_60)
    }
    private val leftLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(11f)
    }
    private val rightLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(11f)
        textAlign = Paint.Align.RIGHT
    }

    init {
        leftLinePaint.color = AppTheme.primary(context)
        rightLinePaint.color = ContextCompat.getColor(context, R.color.hd_status_link)
        leftLabelPaint.color = leftLinePaint.color
        rightLabelPaint.color = rightLinePaint.color
        isClickable = true
    }

    fun setData(startMs: Long, endMs: Long, left: FloatSeries?, right: FloatSeries?) {
        this.startMs = startMs
        this.endMs = max(endMs, startMs + 1)
        leftSeries = left
        rightSeries = right
        scrubMs = null
        rebuildPaths()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padL = dp(44f)
        val padR = dp(44f)
        val padT = dp(12f)
        val padB = dp(28f)
        plot.set(padL, padT, w - padR, h - padB)
        rebuildPaths()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (plot.width() <= 0f || plot.height() <= 0f) return

        // Horizontal grid
        for (i in 0..4) {
            val y = plot.top + plot.height() * i / 4f
            canvas.drawLine(plot.left, y, plot.right, y, gridPaint)
        }
        canvas.drawRect(plot, axisPaint)

        leftSeries?.let { series ->
            drawAxisLabels(canvas, series, left = true)
            canvas.drawPath(leftPath, leftLinePaint)
        }
        rightSeries?.let { series ->
            drawAxisLabels(canvas, series, left = false)
            canvas.drawPath(rightPath, rightLinePaint)
        }

        val scrub = scrubMs
        if (scrub != null) {
            val x = timeToX(scrub)
            canvas.drawLine(x, plot.top, x, plot.bottom, scrubPaint)
        }

        // Time labels
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(formatElapsed(0L), plot.left, height - dp(8f), labelPaint)
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(formatElapsed(endMs - startMs), plot.right, height - dp(8f), labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(true)
                val t = xToTime(event.x.coerceIn(plot.left, plot.right))
                scrubMs = t
                scrubListener?.onScrub(
                    t,
                    valueAt(leftSeries, t),
                    valueAt(rightSeries, t)
                )
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                scrubListener?.onScrubEnded()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun rebuildPaths() {
        buildPath(leftPath, leftSeries, left = true)
        buildPath(rightPath, rightSeries, left = false)
    }

    private fun buildPath(path: Path, series: FloatSeries?, left: Boolean) {
        path.reset()
        if (series == null || series.points.isEmpty() || plot.width() <= 0f) return
        var first = true
        for (p in series.points) {
            val x = timeToX(p.timeMs)
            val y = valueToY(p.value, series, left)
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
        }
    }

    private fun drawAxisLabels(canvas: Canvas, series: FloatSeries, left: Boolean) {
        val paint = if (left) leftLabelPaint else rightLabelPaint
        val x = if (left) dp(4f) else width - dp(4f)
        val maxLabel = formatValue(series.max)
        val minLabel = formatValue(series.min)
        canvas.drawText(maxLabel, x, plot.top + sp(10f), paint)
        canvas.drawText(minLabel, x, plot.bottom, paint)
    }

    private fun timeToX(timeMs: Long): Float {
        val span = (endMs - startMs).toFloat().coerceAtLeast(1f)
        val t = ((timeMs - startMs).toFloat() / span).coerceIn(0f, 1f)
        return plot.left + t * plot.width()
    }

    private fun xToTime(x: Float): Long {
        val span = (endMs - startMs).toFloat().coerceAtLeast(1f)
        val t = ((x - plot.left) / plot.width()).coerceIn(0f, 1f)
        return startMs + (t * span).toLong()
    }

    private fun valueToY(value: Float, series: FloatSeries, @Suppress("UNUSED_PARAMETER") left: Boolean): Float {
        val range = (series.max - series.min).coerceAtLeast(0.001f)
        val t = ((value - series.min) / range).coerceIn(0f, 1f)
        return plot.bottom - t * plot.height()
    }

    private fun valueAt(series: FloatSeries?, timeMs: Long): Float? {
        val points = series?.points ?: return null
        if (points.isEmpty()) return null
        if (timeMs <= points.first().timeMs) return points.first().value
        if (timeMs >= points.last().timeMs) return points.last().value
        var lo = 0
        var hi = points.lastIndex
        while (lo + 1 < hi) {
            val mid = (lo + hi) / 2
            if (points[mid].timeMs <= timeMs) lo = mid else hi = mid
        }
        val a = points[lo]
        val b = points[hi]
        if (b.timeMs == a.timeMs) return a.value
        val frac = (timeMs - a.timeMs).toFloat() / (b.timeMs - a.timeMs).toFloat()
        return a.value + (b.value - a.value) * frac
    }

    private fun formatElapsed(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(Locale.US, m, s)
    }

    private fun formatValue(v: Float): String =
        if (v >= 100f || v <= -100f) "%.0f".format(Locale.US, v)
        else "%.1f".format(Locale.US, v)

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    private fun sp(v: Float): Float = v * resources.displayMetrics.scaledDensity
}
