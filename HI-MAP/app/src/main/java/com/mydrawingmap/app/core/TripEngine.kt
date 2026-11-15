package com.mydrawingmap.app.core

/**
 * 한 번의 이동을 따라가는 엔진.
 * 차 위치가 들어올 때마다 얼마나 왔는지 계산하고, 그림 앞에서 예고하고, 지나간 그림을 표시하고,
 * 길을 벗어났는지와 도착했는지를 알린다. 화면과 SDK를 모르는 순수 로직이다.
 */
class TripEngine(private val listener: Listener) {

    interface Listener {
        /** 아이에게 들려줄 문장이 생겼다. */
        fun onAnnounce(text: String)

        /** 위치나 지나간 그림이 바뀌어 화면을 다시 그려야 한다. */
        fun onProgress()

        /** 목적지에 도착했다. */
        fun onArrived()

        /** 계획한 길에서 벗어났다. 순정 내비가 길을 바꿨을 수 있으니 새 길을 받아야 한다. */
        fun onOffRoute(position: LatLon)
    }

    var route: Route? = null
        private set
    var stops: List<Stop> = emptyList()
        private set
    var destinationName: String = ""
        private set

    /** 출발점에서 지금까지 온 거리(m). 뒤로 가지 않는다. */
    var progress: Double = 0.0
        private set
    var arrived: Boolean = false
        private set

    /** 자리 이름을 정하는 방법. 그림에 이름이 있으면 그 이름을 쓰도록 화면 쪽에서 넣는다. */
    var labelOf: (Spot) -> String = { it.name }

    private val announced = HashSet<String>()
    private val passed = HashSet<String>()
    private var offRouteHits = 0

    /** 새 길로 이동을 시작한다. [alreadyAnnounced]에는 길이 바뀌기 전에 이미 예고한 자리를 넣는다. */
    fun start(route: Route, stops: List<Stop>, destinationName: String, alreadyAnnounced: Set<String> = emptySet()) {
        this.route = route
        this.stops = stops
        this.destinationName = destinationName
        progress = 0.0
        arrived = false
        offRouteHits = 0
        announced.clear()
        passed.clear()
        for (stop in stops) if (stop.spot.id in alreadyAnnounced) announced.add(stop.spot.id)
    }

    /**
     * 멈춰 있는 동안 그림을 붙이거나 떼어 자리 목록이 바뀌었을 때 부른다.
     * 온 거리는 그대로 두고, 이미 지나온 자리는 지나간 것으로 표시해 다시 예고하지 않는다.
     */
    fun replaceStops(stops: List<Stop>) {
        this.stops = stops
        val ids = stops.map { it.spot.id }.toSet()
        announced.retainAll(ids)
        passed.retainAll(ids)
        for (stop in stops) {
            if (progress >= stop.s + PASS_MARGIN_M) {
                announced.add(stop.spot.id)
                passed.add(stop.spot.id)
            }
        }
    }

    fun isPassed(stop: Stop): Boolean = stop.spot.id in passed

    /** 아직 지나지 않은 첫 그림. 없으면 null. */
    fun nextStop(): Stop? = stops.firstOrNull { it.spot.id !in passed }

    /** 남은 그림 수. */
    fun remaining(): Int = stops.count { it.spot.id !in passed }

    /** 예고는 했지만 아직 지나지 않은 자리. 길이 바뀔 때 다시 예고하지 않으려고 넘겨 준다. */
    fun announcedNotPassed(): Set<String> = announced.filter { it !in passed }.toSet()

    fun summary(): String =
        if (arrived) Sentences.arrived(destinationName) else Sentences.summary(remaining(), destinationName)

    /** 차 위치가 새로 들어왔을 때 부른다. */
    fun onLocation(position: LatLon) {
        val r = route ?: return
        if (arrived) return

        val pr = r.project(position)
        if (pr.distance > OFF_ROUTE_M) {
            offRouteHits++
            if (offRouteHits >= OFF_ROUTE_HITS) {
                offRouteHits = 0
                listener.onOffRoute(position)
            }
            return
        }
        offRouteHits = 0
        if (pr.s > progress) progress = pr.s

        for (stop in stops) {
            val id = stop.spot.id
            if (id !in announced && progress >= stop.s - ANNOUNCE_AHEAD_M) {
                announced.add(id)
                listener.onAnnounce(Sentences.approaching(labelOf(stop.spot)))
            }
            if (id !in passed && progress >= stop.s + PASS_MARGIN_M) passed.add(id)
        }

        if (progress >= r.total - ARRIVE_M) {
            arrived = true
            for (stop in stops) passed.add(stop.spot.id)
            listener.onAnnounce(Sentences.arrived(destinationName))
            listener.onProgress()
            listener.onArrived()
            return
        }
        listener.onProgress()
    }

    companion object {
        /** 그림 몇 m 앞에서 예고할지. */
        const val ANNOUNCE_AHEAD_M = 120.0

        /** 그림을 몇 m 지나면 지나간 것으로 볼지. */
        const val PASS_MARGIN_M = 10.0

        /** 목적지 몇 m 안에 들어오면 도착으로 볼지. */
        const val ARRIVE_M = 25.0

        /** 경로에서 이만큼(m) 떨어진 위치가 연속으로 들어오면 길을 벗어난 것으로 본다. */
        const val OFF_ROUTE_M = 80.0
        const val OFF_ROUTE_HITS = 3
    }
}
