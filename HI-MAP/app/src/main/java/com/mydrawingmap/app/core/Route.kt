package com.mydrawingmap.app.core

import kotlin.math.hypot

/**
 * 가는 길 한 줄. 점 목록과, 출발점에서 각 점까지의 누적 거리(m)를 갖는다.
 * 경로 위의 위치는 출발점부터 잰 거리 s(m)로 나타낸다.
 */
class Route(val points: List<LatLon>) {

    /** points[i]까지의 누적 거리(m). */
    val cumulative: DoubleArray

    /** 전체 길이(m). */
    val total: Double

    init {
        require(points.size >= 2) { "경로에는 점이 두 개 이상 있어야 합니다" }
        cumulative = DoubleArray(points.size)
        for (i in 1 until points.size) {
            cumulative[i] = cumulative[i - 1] + Geo.distance(points[i - 1], points[i])
        }
        total = cumulative[points.size - 1]
    }

    /** 거리 s가 놓인 구간의 끝점 번호(1 이상). */
    fun segmentAt(s: Double): Int {
        val c = s.coerceIn(0.0, total)
        var i = 1
        while (i < points.size - 1 && cumulative[i] < c) i++
        return i
    }

    /** 출발점에서 s(m) 간 곳의 좌표. */
    fun pointAt(s: Double): LatLon {
        val c = s.coerceIn(0.0, total)
        val i = segmentAt(c)
        val a = points[i - 1]
        val b = points[i]
        val seg = cumulative[i] - cumulative[i - 1]
        val t = if (seg <= 0.0) 0.0 else (c - cumulative[i - 1]) / seg
        return LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
    }

    /** 경로에 가장 가까운 점까지의 거리와, 그 점의 경로상 위치 s. */
    data class Projection(val s: Double, val distance: Double)

    fun project(p: LatLon): Projection {
        var bestS = 0.0
        var bestD = Double.MAX_VALUE
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val bl = Geo.toLocal(a, b)
            val pl = Geo.toLocal(a, p)
            val len2 = bl[0] * bl[0] + bl[1] * bl[1]
            val t = if (len2 <= 0.0) 0.0 else ((pl[0] * bl[0] + pl[1] * bl[1]) / len2).coerceIn(0.0, 1.0)
            val d = hypot(pl[0] - bl[0] * t, pl[1] - bl[1] * t)
            if (d < bestD) {
                bestD = d
                bestS = cumulative[i - 1] + (cumulative[i] - cumulative[i - 1]) * t
            }
        }
        return Projection(bestS, bestD)
    }

    /** 출발점부터 s까지 지나온 부분. */
    fun head(s: Double): List<LatLon> {
        val i = segmentAt(s)
        val out = ArrayList<LatLon>(points.subList(0, i))
        out.add(pointAt(s))
        return out
    }

    /** s부터 도착점까지 남은 부분. */
    fun tail(s: Double): List<LatLon> {
        val i = segmentAt(s)
        val out = ArrayList<LatLon>()
        out.add(pointAt(s))
        out.addAll(points.subList(i, points.size))
        return out
    }
}
