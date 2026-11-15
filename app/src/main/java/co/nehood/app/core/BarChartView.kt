package co.nehood.app.core

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.ColorUtils
import co.nehood.app.R

/**
 * Vista personalizada para las gráficas de ventas.
 * Sus atributos (barColor, labelColor, labelTextSize, barCornerRadius) están
 * declarados en res/values/attrs.xml y se configuran desde el XML del layout.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: List<Pair<String, Int>> = emptyList()
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val softBarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val rect = RectF()
    private var corner = dp(6f)

    init {
        context.withStyledAttributes(attrs, R.styleable.BarChartView) {
            barPaint.color = getColor(R.styleable.BarChartView_barColor, Color.BLACK)
            labelPaint.color = getColor(R.styleable.BarChartView_labelColor, Color.GRAY)
            labelPaint.textSize = getDimension(R.styleable.BarChartView_labelTextSize, sp(12f))
            corner = getDimension(R.styleable.BarChartView_barCornerRadius, dp(6f))
        }
        // Las barras anteriores van más claras; la última (periodo actual) con el color completo.
        softBarPaint.color = ColorUtils.setAlphaComponent(barPaint.color, 90)
        valuePaint.color = labelPaint.color
        valuePaint.textSize = labelPaint.textSize * 0.9f
    }

    fun setData(values: List<Pair<String, Int>>) {
        data = values
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val labelSpace = labelPaint.textSize * 1.8f
        val valueSpace = valuePaint.textSize * 1.8f
        val chartTop = paddingTop + valueSpace
        val chartBottom = height - paddingBottom - labelSpace
        val chartHeight = chartBottom - chartTop
        val slot = (width - paddingStart - paddingEnd).toFloat() / data.size
        val barWidth = slot * 0.55f
        val max = data.maxOf { it.second }.coerceAtLeast(1)

        data.forEachIndexed { i, (label, value) ->
            val cx = paddingStart + slot * i + slot / 2
            val barTop = chartBottom - chartHeight * value / max
            rect.set(cx - barWidth / 2, barTop, cx + barWidth / 2, chartBottom)
            val paint = if (i == data.lastIndex) barPaint else softBarPaint
            canvas.drawRoundRect(rect, corner, corner, paint)
            canvas.drawText(value.toShortCop(), cx, barTop - dp(6f), valuePaint)
            canvas.drawText(label, cx, height - paddingBottom - labelPaint.textSize * 0.4f, labelPaint)
        }
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
}
