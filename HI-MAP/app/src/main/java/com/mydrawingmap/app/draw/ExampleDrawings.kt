package com.mydrawingmap.app.draw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.mydrawingmap.app.data.Drawing
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * 처음 켰을 때 바로 붙여 볼 수 있는 예시 그림 네 장.
 * 손으로 그린 느낌이 나도록 선을 조금씩 흔들어 그린다. 흔들림은 고정된 값이라 매번 같은 그림이 나온다.
 */
object ExampleDrawings {
    const val HOUSE = "ex-house"
    const val BRIDGE = "ex-bridge"
    const val TREE = "ex-tree"
    const val BUS = "ex-bus"

    private const val SIZE = 256
    private val RED = Color.rgb(210, 73, 63)
    private val YELLOW = Color.rgb(226, 185, 59)
    private val GREEN = Color.rgb(79, 154, 91)
    private val BLUE = Color.rgb(63, 124, 196)
    private val BROWN = Color.rgb(138, 101, 72)
    private val BLACK = Color.rgb(42, 42, 42)

    fun all(): List<Drawing> = listOf(
        Drawing(HOUSE, "빨간 집", house(), true),
        Drawing(BRIDGE, "파란 다리", bridge(), true),
        Drawing(TREE, "초록 나무", tree(), true),
        Drawing(BUS, "노란 버스", bus(), true)
    )

    /** 0~1 좌표의 점들을 조금씩 흔들어 한 획으로 그린다. */
    private class Pen(private val canvas: Canvas, seed: Long) {
        private val random = Random(seed)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        fun stroke(color: Int, width: Float, points: List<FloatArray>) {
            if (points.isEmpty()) return
            val path = Path()
            for ((i, p) in points.withIndex()) {
                val x = (p[0] + (random.nextFloat() - 0.5f) * 0.012f) * SIZE
                val y = (p[1] + (random.nextFloat() - 0.5f) * 0.012f) * SIZE
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            paint.color = color
            paint.strokeWidth = width * SIZE
            canvas.drawPath(path, paint)
        }
    }

    /** 꼭짓점 사이를 잘게 나눠, 흔들림이 선 전체에 고르게 들어가게 한다. */
    private fun line(vararg corners: FloatArray): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        for (i in 1 until corners.size) {
            val a = corners[i - 1]
            val b = corners[i]
            for (k in 0..6) {
                val t = k / 6f
                out.add(floatArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
            }
        }
        return out
    }

    private fun pt(x: Float, y: Float) = floatArrayOf(x, y)

    private fun ring(cx: Float, cy: Float, rx: Float, ry: Float, turns: Float, steps: Int): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        for (i in 0..steps) {
            val angle = -Math.PI / 2 + i.toDouble() / steps * Math.PI * 2 * turns
            out.add(floatArrayOf(cx + (cos(angle) * rx).toFloat(), cy + (sin(angle) * ry).toFloat()))
        }
        return out
    }

    private fun blank(): Bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)

    private fun house(): Bitmap {
        val bitmap = blank()
        val pen = Pen(Canvas(bitmap), 11L)
        pen.stroke(RED, 0.05f, line(pt(0.22f, 0.48f), pt(0.22f, 0.88f), pt(0.78f, 0.88f), pt(0.78f, 0.48f), pt(0.22f, 0.48f)))
        pen.stroke(BROWN, 0.05f, line(pt(0.12f, 0.5f), pt(0.5f, 0.12f), pt(0.88f, 0.5f)))
        pen.stroke(BLUE, 0.035f, line(pt(0.44f, 0.88f), pt(0.44f, 0.64f), pt(0.58f, 0.64f), pt(0.58f, 0.88f)))
        pen.stroke(YELLOW, 0.03f, ring(0.33f, 0.62f, 0.05f, 0.05f, 1.3f, 18))
        return bitmap
    }

    private fun bridge(): Bitmap {
        val bitmap = blank()
        val pen = Pen(Canvas(bitmap), 23L)
        val arch = ArrayList<FloatArray>()
        for (i in 0..20) {
            val t = i / 20f
            arch.add(floatArrayOf(0.1f + 0.8f * t, 0.68f - (sin(t * Math.PI) * 0.46).toFloat()))
        }
        pen.stroke(BLUE, 0.055f, arch)
        pen.stroke(BLUE, 0.045f, line(pt(0.05f, 0.68f), pt(0.95f, 0.68f)))
        pen.stroke(BLUE, 0.03f, line(pt(0.3f, 0.68f), pt(0.3f, 0.4f)))
        pen.stroke(BLUE, 0.03f, line(pt(0.5f, 0.68f), pt(0.5f, 0.24f)))
        pen.stroke(BLUE, 0.03f, line(pt(0.7f, 0.68f), pt(0.7f, 0.4f)))
        val wave = ArrayList<FloatArray>()
        for (i in 0..24) {
            val t = i / 24f
            wave.add(floatArrayOf(0.08f + 0.84f * t, 0.88f + (sin(t * Math.PI * 5) * 0.025).toFloat()))
        }
        pen.stroke(GREEN, 0.028f, wave)
        return bitmap
    }

    private fun tree(): Bitmap {
        val bitmap = blank()
        val pen = Pen(Canvas(bitmap), 37L)
        pen.stroke(BROWN, 0.07f, line(pt(0.5f, 0.94f), pt(0.5f, 0.56f)))
        pen.stroke(GREEN, 0.055f, ring(0.5f, 0.37f, 0.3f, 0.27f, 2.2f, 60))
        pen.stroke(GREEN, 0.05f, ring(0.5f, 0.39f, 0.15f, 0.13f, 1.4f, 26))
        return bitmap
    }

    private fun bus(): Bitmap {
        val bitmap = blank()
        val pen = Pen(Canvas(bitmap), 41L)
        pen.stroke(YELLOW, 0.055f, line(pt(0.1f, 0.34f), pt(0.9f, 0.34f), pt(0.9f, 0.7f), pt(0.1f, 0.7f), pt(0.1f, 0.34f)))
        pen.stroke(YELLOW, 0.05f, line(pt(0.18f, 0.62f), pt(0.3f, 0.44f), pt(0.42f, 0.62f), pt(0.54f, 0.44f), pt(0.66f, 0.62f), pt(0.78f, 0.44f)))
        pen.stroke(BLUE, 0.03f, line(pt(0.2f, 0.42f), pt(0.33f, 0.42f), pt(0.33f, 0.52f), pt(0.2f, 0.52f), pt(0.2f, 0.42f)))
        pen.stroke(BLUE, 0.03f, line(pt(0.43f, 0.42f), pt(0.56f, 0.42f), pt(0.56f, 0.52f), pt(0.43f, 0.52f), pt(0.43f, 0.42f)))
        pen.stroke(BLUE, 0.03f, line(pt(0.66f, 0.42f), pt(0.79f, 0.42f), pt(0.79f, 0.52f), pt(0.66f, 0.52f), pt(0.66f, 0.42f)))
        pen.stroke(BLACK, 0.04f, ring(0.29f, 0.76f, 0.07f, 0.07f, 2f, 24))
        pen.stroke(BLACK, 0.04f, ring(0.71f, 0.76f, 0.07f, 0.07f, 2f, 24))
        return bitmap
    }
}
