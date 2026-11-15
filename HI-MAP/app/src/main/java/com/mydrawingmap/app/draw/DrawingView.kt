package com.mydrawingmap.app.draw

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

/**
 * 손가락으로 그리는 그림판.
 * 획마다 색과 굵기를 기억하고, 저장할 때는 그린 부분만 잘라 정사각형 그림으로 내보낸다.
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private class Stroke(val color: Int, val width: Float) {
        val path = Path()
        val bounds = RectF()
        private var started = false

        fun add(x: Float, y: Float) {
            if (!started) {
                path.moveTo(x, y)
                bounds.set(x, y, x, y)
                started = true
            } else {
                path.lineTo(x, y)
                bounds.union(x, y)
            }
        }
    }

    private val strokes = ArrayList<Stroke>()
    private var current: Stroke? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** 지금 쓰는 색. */
    var penColor: Int = Color.rgb(210, 73, 63)

    /** 펜 굵기. 그림판 너비에 대한 비율이다. */
    var penWidthRatio: Float = 0.05f

    /** 주행 중에는 그릴 수 없게 잠근다. */
    var locked: Boolean = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fallback = (240 * resources.displayMetrics.density).toInt()
        val w = resolveSize(fallback, widthMeasureSpec)
        setMeasuredDimension(w, w)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (s in strokes) drawStroke(canvas, s)
        current?.let { drawStroke(canvas, it) }
    }

    private fun drawStroke(canvas: Canvas, s: Stroke) {
        paint.color = s.color
        paint.strokeWidth = s.width
        canvas.drawPath(s.path, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (locked) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 스크롤 영역 안에 있어도 그리는 동안에는 화면이 밀리지 않게 한다.
                parent?.requestDisallowInterceptTouchEvent(true)
                val s = Stroke(penColor, max(2f, width * penWidthRatio))
                s.add(event.x, event.y)
                s.add(event.x + 0.1f, event.y + 0.1f) // 한 번 찍기만 해도 점이 남게 한다
                current = s
            }
            MotionEvent.ACTION_MOVE -> {
                val s = current
                if (s != null) {
                    for (i in 0 until event.historySize) s.add(event.getHistoricalX(i), event.getHistoricalY(i))
                    s.add(event.x, event.y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                current?.let { strokes.add(it) }
                current = null
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        invalidate()
        return true
    }

    fun isEmpty(): Boolean = strokes.isEmpty()

    fun undo() {
        if (strokes.isNotEmpty()) strokes.removeAt(strokes.size - 1)
        invalidate()
    }

    fun clear() {
        strokes.clear()
        current = null
        invalidate()
    }

    /** 그린 부분만 잘라 [size] px 정사각형 그림으로 만든다. 바탕은 투명하다. 그린 것이 없으면 null. */
    fun exportBitmap(size: Int = 256): Bitmap? {
        if (strokes.isEmpty()) return null
        val box = RectF(strokes[0].bounds)
        var widest = 0f
        for (s in strokes) {
            box.union(s.bounds)
            widest = max(widest, s.width)
        }
        box.inset(-(widest / 2f + 4f), -(widest / 2f + 4f))
        val side = max(max(box.width(), box.height()), 8f)
        val scale = size / side
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        canvas.translate(-(box.centerX() - side / 2f), -(box.centerY() - side / 2f))
        for (s in strokes) drawStroke(canvas, s)
        return bitmap
    }
}
