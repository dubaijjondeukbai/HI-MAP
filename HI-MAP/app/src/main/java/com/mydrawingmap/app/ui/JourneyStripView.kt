package com.mydrawingmap.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View

/**
 * 여정 띠. 집에서 목적지까지 지나갈 그림을 한 줄로 늘어놓는다.
 * 움직이는 요소가 없고, 그림을 지날 때만 다음 칸으로 넘어간다.
 * 주행 중 지도 표시가 허용되지 않을 때는 이 띠가 지도를 대신한다.
 */
class JourneyStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class State { NORMAL, PASSED, NEXT }

    class Entry(val image: Bitmap, val label: String, val state: State)

    private var entries: List<Entry> = emptyList()
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 13f * d
    }
    private val box = RectF()

    var textColor: Int = Color.DKGRAY
    var lineColor: Int = Color.LTGRAY
    var accentColor: Int = Color.BLUE

    fun setEntries(list: List<Entry>) {
        entries = list
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = entries.size
        if (n == 0) return
        val icon = 48f * d
        val available = (width - paddingLeft - paddingRight).toFloat()
        // 칸이 많으면 간격을 줄여 한 줄에 다 들어가게 한다.
        val step = minOf(icon + 26f * d, available / n)
        val top = paddingTop + 4f * d
        val middle = top + icon / 2f

        for ((i, entry) in entries.withIndex()) {
            val cx = paddingLeft + step * i + step / 2f
            if (i < n - 1) {
                paint.style = Paint.Style.STROKE
                paint.color = lineColor
                paint.strokeWidth = 2f * d
                canvas.drawLine(cx + icon / 2f + 3f * d, middle, cx + step - icon / 2f - 3f * d, middle, paint)
            }
            box.set(cx - icon / 2f, top, cx + icon / 2f, top + icon)
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.alpha = if (entry.state == State.PASSED) 115 else 255
            canvas.drawBitmap(entry.image, null, box, paint)
            paint.alpha = 255
            if (entry.state == State.NEXT) {
                paint.style = Paint.Style.STROKE
                paint.color = accentColor
                paint.strokeWidth = 3f * d
                box.inset(-3f * d, -3f * d)
                canvas.drawRoundRect(box, 12f * d, 12f * d, paint)
            }
            textPaint.color = textColor
            textPaint.alpha = if (entry.state == State.PASSED) 115 else 255
            val label = TextUtils.ellipsize(entry.label, textPaint, step - 4f * d, TextUtils.TruncateAt.END).toString()
            canvas.drawText(label, cx, top + icon + 18f * d, textPaint)
        }
    }
}
