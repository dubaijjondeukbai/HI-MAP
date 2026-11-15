package com.mydrawingmap.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면 없이 돌릴 수 있는 로직 검사.
 * 실행: ./gradlew testDemoDebugUnitTest
 */
class CoreTest {

    // 서울 강남 부근에서 동쪽으로 약 1 km, 다시 북쪽으로 약 1 km 가는 ㄱ자 길
    private val a = LatLon(37.4900, 127.0300)
    private val b = Geo.fromLocal(a, 1000.0, 0.0)
    private val c = Geo.fromLocal(b, 0.0, 1000.0)
    private val route = Route(listOf(a, b, c))

    @Test
    fun distanceAndLocalRoundTrip() {
        assertEquals(1000.0, Geo.distance(a, b), 2.0)
        val p = Geo.fromLocal(a, 250.0, -40.0)
        val l = Geo.toLocal(a, p)
        assertEquals(250.0, l[0], 0.5)
        assertEquals(-40.0, l[1], 0.5)
    }

    @Test
    fun routeLengthAndPoints() {
        assertEquals(2000.0, route.total, 5.0)
        assertEquals(500.0, Geo.distance(a, route.pointAt(500.0)), 2.0)
        assertEquals(1, route.segmentAt(999.0))
        assertEquals(2, route.segmentAt(1500.0))
    }

    @Test
    fun projectionFindsNearestPointOnRoute() {
        // 첫 구간 한가운데에서 북쪽으로 30 m 떨어진 점
        val p = Geo.fromLocal(a, 500.0, 30.0)
        val pr = route.project(p)
        assertEquals(500.0, pr.s, 3.0)
        assertEquals(30.0, pr.distance, 1.0)
    }

    @Test
    fun offsetRightIsOnTheRightHandSide() {
        // 동쪽으로 가는 길의 오른쪽은 남쪽이다
        val right = Geo.offsetRight(a, b, route.pointAt(500.0), 28.0)
        val l = Geo.toLocal(a, right)
        assertEquals(500.0, l[0], 2.0)
        assertEquals(-28.0, l[1], 1.0)
    }

    @Test
    fun stopsArePickedInOrderAndSpacedApart() {
        val spots = listOf(
            Spot("far", "멀리 있는 곳", Geo.fromLocal(a, 500.0, 400.0)),      // 경로에서 400 m: 제외
            Spot("s2", "둘째", Geo.fromLocal(a, 900.0, -20.0)),
            Spot("s1", "첫째", Geo.fromLocal(a, 300.0, 20.0)),
            Spot("close", "너무 가까운 곳", Geo.fromLocal(a, 380.0, 20.0)),   // 첫째와 80 m: 제외
            Spot("s3", "셋째", Geo.fromLocal(b, 20.0, 600.0))
        )
        val stops = StopPicker.pick(route, spots)
        assertEquals(listOf("s1", "s2", "s3"), stops.map { it.spot.id })
        assertTrue(stops[0].s < stops[1].s && stops[1].s < stops[2].s)
    }

    @Test
    fun autoSpotsFollowTheRoute() {
        val spots = StopPicker.autoSpots(route, every = 350.0, side = 28.0, limit = 6)
        assertEquals(5, spots.size)
        for (spot in spots) assertEquals(28.0, route.project(spot.position).distance, 3.0)
        // 같은 길이면 같은 id가 나온다
        assertEquals(spots.map { it.id }, StopPicker.autoSpots(route, 350.0, 28.0, 6).map { it.id })
    }

    @Test
    fun josaFollowsTheFinalConsonant() {
        assertEquals("곧 편의점이 나와요.", Sentences.approaching("편의점"))
        assertEquals("곧 다리가 나와요.", Sentences.approaching("다리"))
        assertEquals("그림 3개를 지나면 학교예요.", Sentences.summary(3, "학교"))
        assertEquals("그림 1개를 지나면 할머니 집이에요.", Sentences.summary(1, "할머니 집"))
        assertEquals("조금만 가면 학교예요.", Sentences.summary(0, "학교"))
    }

    private class Recorder : TripEngine.Listener {
        val said = ArrayList<String>()
        var arrived = 0
        var offRoute = 0
        override fun onAnnounce(text: String) { said.add(text) }
        override fun onProgress() {}
        override fun onArrived() { arrived++ }
        override fun onOffRoute(position: LatLon) { offRoute++ }
    }

    @Test
    fun engineAnnouncesPassesAndArrives() {
        val rec = Recorder()
        val engine = TripEngine(rec)
        val stops = StopPicker.pick(route, listOf(Spot("shop", "빨간 편의점", Geo.fromLocal(a, 600.0, -20.0))))
        engine.start(route, stops, "학교")
        assertEquals(1, engine.remaining())

        engine.onLocation(route.pointAt(100.0))
        assertTrue(rec.said.isEmpty())

        engine.onLocation(route.pointAt(490.0))            // 그림 110 m 앞
        assertEquals(listOf("곧 빨간 편의점이 나와요."), rec.said)
        assertEquals(1, engine.remaining())

        engine.onLocation(route.pointAt(490.0))            // 같은 자리에서 다시 불러도 한 번만 말한다
        assertEquals(1, rec.said.size)

        engine.onLocation(route.pointAt(700.0))            // 지나감
        assertEquals(0, engine.remaining())
        assertFalse(engine.arrived)

        engine.onLocation(route.pointAt(1990.0))           // 도착
        assertTrue(engine.arrived)
        assertEquals(1, rec.arrived)
        assertEquals("학교에 도착했어요.", rec.said.last())
    }

    @Test
    fun engineReportsOffRouteAfterThreeFarFixes() {
        val rec = Recorder()
        val engine = TripEngine(rec)
        engine.start(route, emptyList(), "학교")
        val far = Geo.fromLocal(a, 500.0, 300.0)
        engine.onLocation(far)
        engine.onLocation(far)
        assertEquals(0, rec.offRoute)
        engine.onLocation(far)
        assertEquals(1, rec.offRoute)
    }

    @Test
    fun engineDoesNotRepeatAnnouncementsAfterReroute() {
        val rec = Recorder()
        val engine = TripEngine(rec)
        val stops = StopPicker.pick(route, listOf(Spot("shop", "편의점", Geo.fromLocal(a, 600.0, -20.0))))
        engine.start(route, stops, "학교")
        engine.onLocation(route.pointAt(500.0))
        assertEquals(1, rec.said.size)

        // 길이 바뀌어 같은 자리가 새 길에도 있을 때, 이미 예고한 자리는 다시 말하지 않는다
        val kept = engine.announcedNotPassed()
        val rest = Route(route.tail(500.0))
        engine.start(rest, StopPicker.pick(rest, listOf(Spot("shop", "편의점", Geo.fromLocal(a, 600.0, -20.0)))), "학교", kept)
        engine.onLocation(rest.pointAt(10.0))
        assertEquals(1, rec.said.size)
    }

    @Test
    fun replacingStopsKeepsProgressAndSkipsWhatIsBehind() {
        val rec = Recorder()
        val engine = TripEngine(rec)
        engine.start(route, emptyList(), "학교")
        engine.onLocation(route.pointAt(800.0))            // 800 m 온 곳에서 멈춤

        // 멈춘 동안 그림 두 장을 붙였다: 하나는 지나온 곳(300 m), 하나는 앞쪽(1500 m)
        val spots = listOf(
            Spot("behind", "지나온 나무", Geo.fromLocal(a, 300.0, 20.0)),
            Spot("ahead", "앞쪽 다리", Geo.fromLocal(b, 20.0, 500.0))
        )
        engine.replaceStops(StopPicker.pick(route, spots))
        assertEquals(800.0, engine.progress, 3.0)          // 온 거리는 그대로
        assertEquals(1, engine.remaining())                // 지나온 그림은 세지 않는다
        assertEquals("ahead", engine.nextStop()?.spot?.id)

        engine.onLocation(route.pointAt(820.0))
        assertTrue(rec.said.isEmpty())                     // 지나온 그림은 예고하지 않는다

        engine.onLocation(route.pointAt(1400.0))           // 앞쪽 그림 100 m 앞
        assertEquals(listOf("곧 앞쪽 다리가 나와요."), rec.said)
    }
}
