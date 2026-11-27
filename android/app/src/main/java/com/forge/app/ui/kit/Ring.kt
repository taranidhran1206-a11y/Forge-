package com.forge.app.ui.kit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/** A progress ring with a big number in the middle, in Forge's colours. */
class Ring(context: Context) : View(context) {

    var fraction = 0f
        set(v) { field = v; invalidate() }
    var big = ""
        set(v) { field = v; invalidate() }
    var small = ""
        set(v) { field = v; invalidate() }
    var color = Kit.RED
        set(v) { field = v; arc.color = v; invalidate() }

    private val stroke = Kit.dpf(context, 10f)
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; color = Kit.TRACK }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND; color = Kit.RED }
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Kit.FG; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = Kit.dpf(context, 26f)
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Kit.FG2; textAlign = Paint.Align.CENTER; textSize = Kit.dpf(context, 11f); letterSpacing = 0.12f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val box = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        val left = (width - size) / 2 + stroke / 2
        val top = (height - size) / 2 + stroke / 2
        box.set(left, top, left + size - stroke, top + size - stroke)
        canvas.drawArc(box, 0f, 360f, false, track)
        val sweep = 360f * fraction.coerceIn(0f, 1f)
        if (sweep > 0) canvas.drawArc(box, -90f, sweep, false, arc)
        val cy = height / 2f
        canvas.drawText(big, width / 2f, cy + bigPaint.textSize * 0.3f, bigPaint)
        canvas.drawText(small.uppercase(), width / 2f, cy + bigPaint.textSize * 0.3f + smallPaint.textSize * 1.8f, smallPaint)
    }
}
