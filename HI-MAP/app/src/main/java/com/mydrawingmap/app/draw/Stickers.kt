package com.mydrawingmap.app.draw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** 지도와 목록에 올릴 작은 그림들을 만든다. 모두 코드로 그리므로 이미지 파일이 필요 없다. */
object Stickers {

    /** 그림 뒤에 까는 종이 색. 낮·밤 모두 같은 색을 써서 그림 색이 달라 보이지 않게 한다. */
    val PAPER: Int = Color.rgb(255, 255, 255)
    val PAPER_EDGE: Int = Color.rgb(199, 201, 206)
    val INK: Int = Color.rgb(68, 70, 78)
    val LABEL: Int = Color.rgb(19, 20, 23)
    /** 경로 선과 강조 테두리. */
    val ROUTE: Int = Color.rgb(0, 100, 255)

    private fun newBitmap(width: Int, height: Int): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    /** 그림을 종이 카드에 올린 스티커. 목록과 여정 띠에 쓴다. [edge]는 테두리 색이다. */
    fun sticker(picture: Bitmap, size: Int, edge: Int = PAPER_EDGE, edgeWidth: Float = size * 0.03f): Bitmap {
        val out = newBitmap(size, size)
        val canvas = Canvas(out)
        val half = edgeWidth / 2f
        val rect = RectF(half, half, size - half, size - half)
        val radius = size * 0.18f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.style = Paint.Style.FILL
        paint.color = PAPER
        canvas.drawRoundRect(rect, radius, radius, paint)
        val pad = size * 0.1f
        canvas.drawBitmap(picture, null, RectF(pad, pad, size - pad, size - pad), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = edgeWidth
        paint.color = edge
        canvas.drawRoundRect(rect, radius, radius, paint)
        return out
    }

    /**
     * 지도에 꽂는 표지판. 그림 카드 아래에 이름을 적고, 맨 아래 꼬리 끝이 실제 위치를 가리킨다.
     * [size]는 그림 칸 한 변의 길이(px)다. 이름이 길면 카드를 옆으로 늘리고, 그래도 넘치면 말줄임표를 붙인다.
     * [label]이 비어 있으면 이름 칸 없이 그림만 넣는다.
     */
    fun pin(picture: Bitmap, label: String, size: Int, edge: Int = PAPER_EDGE, edgeWidth: Float = size * 0.04f): Bitmap {
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
        text.color = LABEL
        text.textAlign = Paint.Align.CENTER
        text.textSize = size * 0.24f
        text.isFakeBoldText = true

        val pad = size * 0.1f
        val labelHeight = if (label.isEmpty()) 0f else size * 0.36f
        val wanted = if (label.isEmpty()) 0f else text.measureText(label) + pad * 2f
        val width = ceil(min(size * 1.9f, max(size.toFloat(), wanted))).toInt()
        val tail = size * 0.18f
        val cardBottom = size + labelHeight
        val out = newBitmap(width, ceil(cardBottom + tail).toInt())
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val half = edgeWidth / 2f
        val cx = width / 2f

        // 꼬리: 카드 아래 가운데에서 실제 위치를 가리킨다
        val pointer = Path()
        pointer.moveTo(cx - tail * 0.75f, cardBottom - edgeWidth)
        pointer.lineTo(cx, cardBottom + tail)
        pointer.lineTo(cx + tail * 0.75f, cardBottom - edgeWidth)
        pointer.close()
        paint.style = Paint.Style.FILL
        paint.color = edge
        canvas.drawPath(pointer, paint)

        // 카드와 그림
        val card = RectF(half, half, width - half, cardBottom - half)
        val radius = size * 0.18f
        paint.color = PAPER
        canvas.drawRoundRect(card, radius, radius, paint)
        val left = (width - size) / 2f
        canvas.drawBitmap(picture, null, RectF(left + pad, pad, left + size - pad, size - pad), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = edgeWidth
        paint.color = edge
        canvas.drawRoundRect(card, radius, radius, paint)

        // 이름
        if (label.isNotEmpty()) {
            val shown = TextUtils.ellipsize(label, text, width - pad * 2f, TextUtils.TruncateAt.END).toString()
            val middle = size + labelHeight / 2f - pad / 2f
            canvas.drawText(shown, cx, middle - (text.descent() + text.ascent()) / 2f, text)
        }
        return out
    }

    /** 그림을 붙이지 않은 집·목적지에 쓰는 기본 그림. [flag]가 참이면 깃발을 달아 목적지임을 나타낸다. 바탕은 투명하다. */
    fun housePicture(size: Int, flag: Boolean): Bitmap {
        val out = newBitmap(size, size)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.07f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = INK
        val c = size / 2f
        val u = size * 0.26f
        val floor = c + u * 1.15f
        val eave = c + u * 0.05f
        val top = c - u
        val house = Path()
        house.moveTo(c - u * 1.35f, eave)
        house.lineTo(c, top)
        house.lineTo(c + u * 1.35f, eave)
        house.moveTo(c - u, eave)
        house.lineTo(c - u, floor)
        house.lineTo(c + u, floor)
        house.lineTo(c + u, eave)
        canvas.drawPath(house, paint)
        if (flag) {
            paint.color = ROUTE
            val pole = Path()
            pole.moveTo(c, top)
            pole.lineTo(c, top - u * 0.75f)
            pole.lineTo(c + u * 0.8f, top - u * 0.5f)
            pole.lineTo(c, top - u * 0.25f)
            canvas.drawPath(pole, paint)
        }
        return out
    }

    /** 아직 그림을 붙이지 않은 길가 자리에 쓰는 더하기 표시. 바탕은 투명하다. */
    fun plusPicture(size: Int): Bitmap {
        val out = newBitmap(size, size)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.09f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = PAPER_EDGE
        val c = size / 2f
        val r = size * 0.24f
        canvas.drawLine(c - r, c, c + r, c, paint)
        canvas.drawLine(c, c - r, c, c + r, paint)
        return out
    }

    /** 그림을 붙이지 않았을 때의 내 차 표시. */
    fun carPuck(size: Int): Bitmap {
        val out = newBitmap(size, size)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val c = size / 2f
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(c, c, size * 0.40f, paint)
        paint.color = ROUTE
        canvas.drawCircle(c, c, size * 0.31f, paint)
        paint.color = Color.WHITE
        val arrow = Path()
        arrow.moveTo(c, c - size * 0.17f)
        arrow.lineTo(c + size * 0.13f, c + size * 0.12f)
        arrow.lineTo(c, c + size * 0.05f)
        arrow.lineTo(c - size * 0.13f, c + size * 0.12f)
        arrow.close()
        canvas.drawPath(arrow, paint)
        return out
    }

    /** 건물·공원·물에 깔 무늬 한 칸. 바탕색 위에 아이 그림을 옅게 올린다. */
    fun patternTile(drawing: Bitmap, baseColor: Int, size: Int = 64): Bitmap {
        val out = newBitmap(size, size)
        val canvas = Canvas(out)
        canvas.drawColor(baseColor)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.alpha = 150
        val pad = size * 0.12f
        canvas.drawBitmap(drawing, null, RectF(pad, pad, size - pad, size - pad), paint)
        return out
    }

    /** 무늬를 붙이지 않은 바탕 자리를 목록에 보여 줄 때 쓰는 색 칸. */
    fun swatch(color: Int, size: Int): Bitmap {
        val out = newBitmap(size, size)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        val r = size * 0.18f
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), r, r, paint)
        return out
    }
}
