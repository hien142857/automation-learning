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
        val max = (samples.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val step = width.toFloat() / samples.size.coerceAtLeast(1)
        samples.forEachIndexed { index, value ->
            val barHeight = (height - 20) * value / max
            canvas.drawRoundRect(index * step + 4, height - 16 - barHeight, (index + 1) * step - 4, height - 16f, 6f, 6f, paint)
        }
        paint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
        canvas.drawText("1", 4f, height.toFloat(), paint)
        canvas.drawText("${samples.size} lần · 0–${samples.maxOrNull()} ms", width / 2f, height.toFloat(), paint)
    }
}
