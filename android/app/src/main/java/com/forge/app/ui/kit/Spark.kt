package com.forge.app.ui.kit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** A small line chart (oldest → newest) with a dot on the latest value. */
class Spark(context: Context, private var values: List<Double> = emptyList(), private val color: Int = Kit.RED) : View(context) {

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Kit.dpf(context, 2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = this@Spark.color
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@Spark.color }
    private val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = Kit.LINE
        strokeWidth = Kit.dpf(context, 1f)
    }

    fun set(values: List<Double>) {
        this.values = values
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = Kit.dpf(context, 4f)
        val w = width - pad * 2
        val h = height - pad * 2
        canvas.drawLine(pad, height - pad, width - pad, height - pad, base)
        if (values.isEmpty()) return
        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 0.0
        val span = if (max - min < 1e-9) 1.0 else max - min
        fun x(i: Int) = pad + if (values.size == 1) w / 2 else w * i / (values.size - 1)
        fun y(v: Double) = pad + h - ((v - min) / span * h).toFloat() * if (max - min < 1e-9) 0.5f else 1f
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
        canvas.drawPath(path, line)
        canvas.drawCircle(x(values.size - 1), y(values.last()), Kit.dpf(context, 3.5f), dot)
    }
}
