package vn.startupbench

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

class SampleChart(context: Context, private val samples: List<Long>, color: Int) : View(context) {
    constructor(context: Context) : this(context, emptyList(), android.graphics.Color.CYAN)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    init { contentDescription = "Từng mẫu hợp lệ: ${samples.joinToString()} mili giây. Trục cao từ 0 đến ${samples.maxOrNull()} ms." }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (samples.isEmpty()) return
        val density = resources.displayMetrics.density
        val side = 4f * density
        val bottom = height - 26f * density
        val top = 6f * density
        if (bottom <= top || width <= side * 2) return
        val max = (samples.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val step = (width - side * 2) / samples.size
        val gap = minOf(5f * density, step / 4)
        samples.forEachIndexed { index, value ->
            val barHeight = (bottom - top) * value / max
            canvas.drawRoundRect(side + index * step + gap / 2, bottom - barHeight,
                side + (index + 1) * step - gap / 2, bottom, 3f * density, 3f * density, paint)
        }
        paint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
        paint.textAlign = Paint.Align.CENTER
        val baseline = height - 3f * density - paint.fontMetrics.descent
        samples.forEachIndexed { index, _ ->
            canvas.drawText("${index + 1}", side + (index + .5f) * step, baseline, paint)
        }
    }
}
