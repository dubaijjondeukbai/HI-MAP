package com.mydrawingmap.app.core

/** 그림을 붙일 수 있는 자리 하나. */
data class Spot(val id: String, val name: String, val position: LatLon)

/** 경로 위에 놓인 자리. s는 출발점에서 그 자리 앞까지의 거리(m). */
data class Stop(val spot: Spot, val s: Double)

/** 경로 주변의 자리 가운데 오늘 지나갈 것을 고른다. */
object StopPicker {

    /**
     * 경로에서 [maxOffRoute] m 안에 있는 자리를 가는 순서대로 고른다.
     * 너무 촘촘하면 아이에게 부담이 되므로 [minGap] m 이상 떨어진 것만, 많아야 [limit]개 남긴다.
     */
    fun pick(
        route: Route,
        candidates: List<Spot>,
        maxOffRoute: Double = 70.0,
        minGap: Double = 250.0,
        limit: Int = 6
    ): List<Stop> {
        val near = ArrayList<Stop>()
        for (spot in candidates) {
            val pr = route.project(spot.position)
            if (pr.distance <= maxOffRoute && pr.s > 40.0 && pr.s < route.total - 40.0) {
                near.add(Stop(spot, pr.s))
            }
        }
        near.sortBy { it.s }
        val out = ArrayList<Stop>()
        for (stop in near) {
            val farEnough = out.all { stop.s - it.s >= minGap }
            if (farEnough) out.add(stop)
            if (out.size == limit) break
        }
        return out
    }

    /**
     * 붙여 둔 자리가 없는 길을 위해, 경로를 따라 [every] m마다 길 오른쪽에 빈 자리를 만든다.
     * 같은 길이면 같은 자리가 나오도록 좌표로 id를 만든다.
     */
    fun autoSpots(route: Route, every: Double = 350.0, side: Double = 28.0, limit: Int = 6): List<Spot> {
        val out = ArrayList<Spot>()
        var s = every
        while (s < route.total - 150.0 && out.size < limit) {
            val i = route.segmentAt(s)
            val at = route.pointAt(s)
            val pos = Geo.offsetRight(route.points[i - 1], route.points[i], at, side)
            val id = "auto-%.5f-%.5f".format(java.util.Locale.US, pos.lat, pos.lon)
            out.add(Spot(id, "그림 자리 ${out.size + 1}", pos))
            s += every
        }
        return out
    }
}
