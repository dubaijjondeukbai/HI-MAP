package com.mydrawingmap.app.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 위도·경도 한 점 (WGS84, 도 단위). */
data class LatLon(val lat: Double, val lon: Double)

/** 거리와 좌표 계산. 안드로이드에 의존하지 않아 단위 테스트로 검증할 수 있다. */
object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** 두 점 사이 거리(m). */
    fun distance(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    /** 기준점 주변을 평면으로 편 좌표(m). x는 동쪽, y는 북쪽. 몇 km 범위에서는 충분히 정확하다. */
    fun toLocal(origin: LatLon, p: LatLon): DoubleArray {
        val x = Math.toRadians(p.lon - origin.lon) * EARTH_RADIUS_M * cos(Math.toRadians(origin.lat))
        val y = Math.toRadians(p.lat - origin.lat) * EARTH_RADIUS_M
        return doubleArrayOf(x, y)
    }

    /** [toLocal]의 반대. 평면 좌표(m)를 위도·경도로 되돌린다. */
    fun fromLocal(origin: LatLon, x: Double, y: Double): LatLon {
        val lat = origin.lat + Math.toDegrees(y / EARTH_RADIUS_M)
        val lon = origin.lon + Math.toDegrees(x / (EARTH_RADIUS_M * cos(Math.toRadians(origin.lat))))
        return LatLon(lat, lon)
    }

    /** a에서 b로 가는 길의 오른쪽으로 [meters]만큼 떨어진 점을 [at] 위치 기준으로 구한다. */
    fun offsetRight(a: LatLon, b: LatLon, at: LatLon, meters: Double): LatLon {
        val d = toLocal(a, b)
        val len = hypot(d[0], d[1])
        if (len < 1e-6) return at
        // 진행 방향 (dx, dy)의 오른쪽은 (dy, -dx)
        return fromLocal(at, d[1] / len * meters, -d[0] / len * meters)
    }
}
