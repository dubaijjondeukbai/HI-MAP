package com.mydrawingmap.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mydrawingmap.app.core.Geo
import com.mydrawingmap.app.core.LatLon
import com.mydrawingmap.app.core.Route
import com.mydrawingmap.app.core.Sentences
import com.mydrawingmap.app.core.Spot
import com.mydrawingmap.app.core.Stop
import com.mydrawingmap.app.core.StopPicker
import com.mydrawingmap.app.core.TripEngine
import com.mydrawingmap.app.data.DemoTrip
import com.mydrawingmap.app.data.Destination
import com.mydrawingmap.app.data.Drawing
import com.mydrawingmap.app.data.DrawingStore
import com.mydrawingmap.app.data.Slots
import com.mydrawingmap.app.draw.DrawingView
import com.mydrawingmap.app.draw.ExampleDrawings
import com.mydrawingmap.app.draw.Stickers
import com.mydrawingmap.app.map.MapController
import com.mydrawingmap.app.route.RouteService
import com.mydrawingmap.app.ui.JourneyStripView
import com.mydrawingmap.app.vehicle.CarSupport
import com.mydrawingmap.app.vehicle.CarUxDrivingState
import com.mydrawingmap.app.vehicle.DrivingState
import com.mydrawingmap.app.vehicle.LocationBridge
import com.mydrawingmap.app.vehicle.ManualDrivingState
import com.mydrawingmap.app.vehicle.NaviBridge
import com.mydrawingmap.app.vehicle.ReplayLocationBridge
import com.mydrawingmap.app.vehicle.Speaker
import com.mydrawingmap.app.vehicle.VehicleFactory
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * HI-MAP의 화면 하나.
 *
 * 화면 구성
 *  - 가운데: 그림 지도. 가는 길과 내 차, 아이 그림으로 만든 표지판이 보인다.
 *  - 오른쪽: 멈춰 있을 때는 그리기·붙이기·미리 보기, 달리는 중에는 "다음 그림" 한 장.
 *  - 아래: 여정 띠. 집에서 목적지까지 지나갈 그림을 한 줄로 보여 준다.
 *
 * 두 가지 상태
 *  - 멈춰 있을 때: 그림을 그리고 붙일 수 있다.
 *  - 달리는 중: 보기 전용. 그리기·붙이기·지도 손짓이 모두 잠기고, 그림 앞에서 소리로 예고한다.
 *
 * 신호를 받는 곳
 *  - 시연 재생(처음 상태): 화면의 출발 단추와, 길을 따라 재생되는 위치를 쓴다. demo 빌드는 항상 이 방식이다.
 *  - 차량 신호: pleos 빌드에서 "시연 재생"을 끄면 FusedLocation 위치와 차량의 주행 제한 상태를 쓴다.
 *  어느 쪽이든 목적지를 누르면 순정 내비에 같은 목적지를 보내고(pleos 빌드), 예고는 음성으로 읽는다.
 */
class MainActivity : AppCompatActivity() {

    // ---- 화면 ----
    private lateinit var mapView: MapView
    private lateinit var mapController: MapController
    private lateinit var destBar: LinearLayout
    private lateinit var modeChip: TextView
    private lateinit var mapNote: TextView
    private lateinit var caption: TextView
    private lateinit var btnDemo: Button
    private lateinit var btnBlock: Button
    private lateinit var btnGo: Button
    private lateinit var editPanel: View
    private lateinit var drivePanel: View
    private lateinit var tabDraw: Button
    private lateinit var tabAssign: Button
    private lateinit var tabPreview: Button
    private lateinit var paneDraw: View
    private lateinit var paneAssign: View
    private lateinit var panePreview: View
    private lateinit var drawingView: DrawingView
    private lateinit var crayonRow: LinearLayout
    private lateinit var penThin: Button
    private lateinit var penThick: Button
    private lateinit var drawName: EditText
    private lateinit var drawHint: TextView
    private lateinit var drawer: LinearLayout
    private lateinit var drawerDelete: Button
    private lateinit var slotList: LinearLayout
    private lateinit var previewSum: TextView
    private lateinit var previewList: LinearLayout
    private lateinit var nextImage: ImageView
    private lateinit var nextName: TextView
    private lateinit var nextDistance: TextView
    private lateinit var driveSum: TextView
    private lateinit var strip: JourneyStripView

    // ---- 데이터 ----
    private lateinit var store: DrawingStore
    private lateinit var trip: DemoTrip
    private lateinit var destination: Destination
    private lateinit var routeService: RouteService

    private val engine = TripEngine(object : TripEngine.Listener {
        override fun onAnnounce(text: String) {
            say(text)
        }

        override fun onProgress() {
            onTripProgress()
        }

        override fun onArrived() {
            onTripArrived()
        }

        override fun onOffRoute(position: LatLon) {
            onTripOffRoute(position)
        }
    })

    // ---- 차량 연결부 ----
    private lateinit var navi: NaviBridge
    private lateinit var speaker: Speaker
    private val replay = ReplayLocationBridge()
    private val manualState = ManualDrivingState()

    /** 차량 위치. pleos 빌드에서만 있다. */
    private var vehicleLocation: LocationBridge? = null

    /** 차량의 주행 제한 상태. 차량용 안드로이드에서만 있다. */
    private var vehicleState: DrivingState? = null

    /**
     * 참이면 시연 재생(화면 단추 + 재생되는 위치), 거짓이면 차량 신호를 쓴다.
     * 에뮬레이터는 스스로 달리지 않으므로 처음에는 시연 재생으로 켠다. pleos 빌드에서는 위쪽 단추로 바꿀 수 있다.
     */
    private var demoMode = true

    private val naviListener = object : NaviBridge.Listener {
        override fun onRouteReady() {
            if (VehicleFactory.USES_PLEOS_SDK) showHint(getString(R.string.navi_sent))
        }

        override fun onRerouted() {
            // 순정 내비가 길을 바꿨다. 경로 모양은 받을 수 없으므로 지금 위치에서 남은 길을 다시 받는다.
            val position = carPosition
            if (driving && position != null) reroute(position, null)
        }

        override fun onArrived() {
            // 도착은 차 위치로 판단한다(TripEngine). 순정 내비의 알림은 기록만 남긴다.
            Log.d(TAG, "순정 내비가 도착을 알렸습니다")
        }

        override fun onNaviError(message: String) {
            showHint(message)
        }
    }

    // ---- 상태 ----
    private var driving = false
    private var tab = TAB_DRAW
    private var pickedId: String? = null
    private var carPosition: LatLon? = null

    /** 처음 받은 길. 시연을 다시 할 때 이 길로 되돌린다. */
    private var plannedPoints: List<LatLon>? = null

    /** 아직 그림을 붙이지 않은 길가 자리. 멈춰 있을 때만 지도에 ＋ 표지판으로 보인다. */
    private var placeholders: List<Stop> = emptyList()

    /** 길이 바뀌기 전에 지나온 길과 그림. */
    private val trail = ArrayList<LatLon>()
    private val passedEarlier = ArrayList<Spot>()

    private var startedAtHome = true
    private var routeSerial = 0
    private var routePending = false
    private var lastPlanFrom: LatLon? = null
    private var night = false
    private var recreatePending = false
    private var crayon = 0
    private var lastNextKey = ""

    private val handler = Handler(Looper.getMainLooper())
    private val thumbs = HashMap<String, Bitmap>()
    private val pictures = HashMap<String, Bitmap>()

    private val restoreNote = Runnable {
        mapNote.setText(if (driving) R.string.map_note_driving else R.string.map_note_parked)
    }
    private val hideCaption = Runnable { caption.visibility = View.GONE }
    private val autoPark = Runnable { manualState.set(false) }

    // ------------------------------------------------------------------ 생명주기

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this) // 지도 화면을 만들기 전에 한 번 불러야 한다
        setContentView(R.layout.activity_main)
        night = isNight(resources.configuration)

        store = DrawingStore(this)
        store.load()
        trip = DemoTrip.load(this)
        val savedDestination = savedInstanceState?.getString(KEY_DESTINATION)
        destination = trip.destinations.firstOrNull { it.id == savedDestination } ?: trip.destinations[0]
        tab = savedInstanceState?.getInt(KEY_TAB, TAB_DRAW) ?: TAB_DRAW
        routeService = RouteService(BuildConfig.TMAP_APP_KEY)
        engine.labelOf = { spot -> labelOf(spot) }

        bindViews()
        setUpButtons()
        refreshCrayons()
        setPen(false)

        mapView.onCreate(savedInstanceState)
        mapController = MapController(mapView, store)
        mapController.onItemTapped = { slot -> onSlotChosen(slot) }
        mapController.onLongPress = { position -> onMapLongPress(position) }
        mapController.start(
            onReady = {
                mapController.setGesturesEnabled(!driving)
                val position = carPosition
                if (driving && position != null) {
                    mapController.follow(position)
                } else if (engine.route != null) {
                    showRouteOnMap(false)
                } else {
                    mapController.jumpTo(trip.home, 13.5)
                }
            },
            onFailed = { message ->
                Log.w(TAG, "지도를 불러오지 못했습니다: $message")
                showHint(getString(R.string.map_failed))
            }
        )

        connectVehicle()
        applyDrivingUi()
        selectTab(tab)
        refreshAll()
        planTrip(trip.home, false)
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
        outState.putString(KEY_DESTINATION, destination.id)
        outState.putInt(KEY_TAB, tab)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        replay.stop()
        manualState.stop()
        vehicleLocation?.stop()
        vehicleState?.stop()
        navi.stop()
        speaker.stop()
        routeService.shutdown()
        mapView.onDestroy()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val now = isNight(newConfig)
        if (now == night) return
        night = now
        // 낮·밤 화면이 바뀌면 화면을 새로 만든다. 달리는 중에는 화면이 처음으로 돌아가지 않게, 멈출 때까지 미룬다.
        if (driving) recreatePending = true else recreate()
    }

    private fun isNight(config: Configuration): Boolean =
        (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    // ------------------------------------------------------------------ 준비

    private fun bindViews() {
        mapView = findViewById(R.id.mapView)
        destBar = findViewById(R.id.destBar)
        modeChip = findViewById(R.id.modeChip)
        mapNote = findViewById(R.id.mapNote)
        caption = findViewById(R.id.caption)
        btnDemo = findViewById(R.id.btnDemo)
        btnBlock = findViewById(R.id.btnBlock)
        btnGo = findViewById(R.id.btnGo)
        editPanel = findViewById(R.id.editPanel)
        drivePanel = findViewById(R.id.drivePanel)
        tabDraw = findViewById(R.id.tabDraw)
        tabAssign = findViewById(R.id.tabAssign)
        tabPreview = findViewById(R.id.tabPreview)
        paneDraw = findViewById(R.id.paneDraw)
        paneAssign = findViewById(R.id.paneAssign)
        panePreview = findViewById(R.id.panePreview)
        drawingView = findViewById(R.id.drawingView)
        crayonRow = findViewById(R.id.crayonRow)
        penThin = findViewById(R.id.penThin)
        penThick = findViewById(R.id.penThick)
        drawName = findViewById(R.id.drawName)
        drawHint = findViewById(R.id.drawHint)
        drawer = findViewById(R.id.drawer)
        drawerDelete = findViewById(R.id.drawerDelete)
        slotList = findViewById(R.id.slotList)
        previewSum = findViewById(R.id.previewSum)
        previewList = findViewById(R.id.previewList)
        nextImage = findViewById(R.id.nextImage)
        nextName = findViewById(R.id.nextName)
        nextDistance = findViewById(R.id.nextDistance)
        driveSum = findViewById(R.id.driveSum)
        strip = findViewById(R.id.strip)

        strip.textColor = color(R.color.ink2)
        strip.lineColor = color(R.color.line)
        strip.accentColor = color(R.color.accent)
    }

    private fun setUpButtons() {
        tabDraw.setOnClickListener { selectTab(TAB_DRAW) }
        tabAssign.setOnClickListener { selectTab(TAB_ASSIGN) }
        tabPreview.setOnClickListener { selectTab(TAB_PREVIEW) }

        penThin.setOnClickListener { setPen(false) }
        penThick.setOnClickListener { setPen(true) }
        findViewById<Button>(R.id.padUndo).setOnClickListener { drawingView.undo() }
        findViewById<Button>(R.id.padClear).setOnClickListener {
            drawingView.clear()
            drawHint.setText(R.string.draw_hint)
        }
        findViewById<Button>(R.id.padSave).setOnClickListener { saveDrawing() }
        drawerDelete.setOnClickListener { deletePicked() }

        findViewById<Button>(R.id.btnAsk).setOnClickListener {
            if (engine.route != null) say(engine.summary())
        }
        btnGo.setOnClickListener { onGoPressed() }
        btnBlock.setOnClickListener { onBlockPressed() }
        btnDemo.setOnClickListener { onDemoToggled() }
    }

    /** 내비·음성·위치·주행 상태를 연결한다. */
    private fun connectVehicle() {
        speaker = VehicleFactory.speaker(this)
        speaker.start()
        navi = VehicleFactory.navi(this)
        navi.start(naviListener)
        manualState.start { now -> onDrivingChanged(now) }
        if (VehicleFactory.USES_PLEOS_SDK) vehicleLocation = VehicleFactory.location(this)
        if (CarSupport.available(this)) vehicleState = CarUxDrivingState(this)
        useSignals()
    }

    /** 위치와 주행 상태를 어디서 받을지 정한다. 시연 재생이면 화면 단추와 재생되는 위치를 쓴다. */
    private fun useSignals() {
        replay.stop()
        vehicleLocation?.stop()
        vehicleState?.stop()
        if (!demoMode) {
            vehicleLocation?.start { position -> onLocation(position) }
            vehicleState?.start { now -> onDrivingChanged(now) }
        }
    }

    // ------------------------------------------------------------------ 길

    private fun startPoint(): LatLon = if (demoMode) trip.home else (carPosition ?: trip.home)

    /** [from]에서 목적지까지 길을 새로 받는다. [sendToNavi]가 참이면 순정 내비에도 같은 목적지를 보낸다. */
    private fun planTrip(from: LatLon, sendToNavi: Boolean) {
        routeSerial++
        val serial = routeSerial
        routePending = true
        lastPlanFrom = from
        showHint(getString(R.string.finding_route))
        routeService.request(from, destination.position, null) { points, source ->
            if (serial != routeSerial || isFinishing || isDestroyed) return@request
            routePending = false
            plannedPoints = points
            trail.clear()
            passedEarlier.clear()
            startedAtHome = Geo.distance(from, trip.home) < 300.0
            val straight = source == RouteService.SOURCE_STRAIGHT
            if (store.firstRun && !straight) {
                // 첫 실행에서 도로 경로를 처음 받았을 때 한 번만, 길가가 비어 있으면 예시 그림을 붙여 둔다.
                store.firstRun = false
                if (store.customSpots.isEmpty()) seedExamples(points)
            }
            applyRoute(points, emptySet())
            if (demoMode) carPosition = points[0]
            if (sendToNavi) navi.requestRoute(destination)
            showHint(if (straight) getString(R.string.straight_route_notice) else "")
            if (!driving) showRouteOnMap(true)
            refreshAll()
        }
    }

    /** 달리는 중에 길이 바뀌었을 때. 지나온 길과 이미 예고한 그림은 그대로 두고, 남은 길만 새로 받는다. */
    private fun reroute(from: LatLon, via: LatLon?) {
        if (routePending) return
        routeSerial++
        val serial = routeSerial
        routePending = true
        lastPlanFrom = from
        if (demoMode) replay.stop() // 새 길을 받을 때까지 잠깐 멈춘다
        routeService.request(from, destination.position, via) { points, source ->
            if (serial != routeSerial || isFinishing || isDestroyed) return@request
            routePending = false
            val old = engine.route
            if (old != null) trail.addAll(old.head(engine.progress))
            for (stop in engine.stops) {
                if (engine.isPassed(stop)) passedEarlier.add(stop.spot)
            }
            applyRoute(points, engine.announcedNotPassed())
            say(Sentences.rerouted(engine.remaining(), destination.name))
            if (source == RouteService.SOURCE_STRAIGHT) showHint(getString(R.string.straight_route_notice))
            if (demoMode && driving) replay.start { position -> onLocation(position) }
            refreshAll()
        }
    }

    /** 새 길로 이동을 시작한다. 길가에 붙여 둔 그림 가운데 이 길에서 지나갈 것을 고른다. */
    private fun applyRoute(points: List<LatLon>, announced: Set<String>) {
        if (points.size < 2) return
        val route = Route(points)
        engine.start(route, computeStops(route), destination.name, announced)
        placeholders = computePlaceholders(route, engine.stops)
        if (demoMode) replay.setPath(points)
        lastNextKey = ""
    }

    /** 처음 받은 길로 되돌린다. 도착한 뒤 시연을 다시 할 때 쓴다. */
    private fun resetTrip() {
        val points = plannedPoints ?: return
        trail.clear()
        passedEarlier.clear()
        applyRoute(points, emptySet())
        if (demoMode) carPosition = points[0]
    }

    /** 길가에 붙여 둔 그림(내 장소) 가운데 이 길에서 지나갈 것. */
    private fun computeStops(route: Route): List<Stop> {
        val candidates = store.customSpots.filter { spot -> passedEarlier.none { it.id == spot.id } }
        return StopPicker.pick(route, candidates, NEAR_ROUTE_M, MIN_GAP_M, MAX_STOPS)
    }

    /** 그림을 붙일 수 있는 빈 자리. 길을 따라 고르게 놓고, 이미 그림이 있는 곳 가까이는 뺀다. */
    private fun computePlaceholders(route: Route, stops: List<Stop>): List<Stop> {
        val every = max(350.0, route.total / 7.0)
        val out = ArrayList<Stop>()
        for (candidate in StopPicker.pick(route, StopPicker.autoSpots(route, every), NEAR_ROUTE_M, MIN_GAP_M, MAX_STOPS)) {
            if (stops.size + out.size >= MAX_STOPS) break
            if (stops.all { abs(it.s - candidate.s) >= MIN_GAP_M }) out.add(candidate)
        }
        return out
    }

    /** 그림을 붙이거나 뗀 뒤, 지나갈 그림과 빈 자리를 다시 고른다. 온 거리는 그대로 둔다. */
    private fun refreshStops() {
        val route = engine.route ?: return
        engine.replaceStops(computeStops(route))
        placeholders = computePlaceholders(route, engine.stops)
    }

    /** 처음 켰을 때 길가 두 곳에 예시 그림을 붙여 둔다. 무엇을 하는 앱인지 바로 보이게 하려는 것이다. */
    private fun seedExamples(points: List<LatLon>) {
        if (points.size < 2) return
        val places = computePlaceholders(Route(points), emptyList())
        val picks = listOf(Pair(1, ExampleDrawings.BRIDGE), Pair(3, ExampleDrawings.TREE))
        for ((index, drawingId) in picks) {
            val place = places.getOrNull(index) ?: continue
            val drawing = store.byId(drawingId) ?: continue
            val spot = store.addSpot(drawing.name, place.spot.position)
            store.toggle(Slots.spot(spot.id), drawing.id)
        }
    }

    // ------------------------------------------------------------------ 위치와 주행 상태

    private fun onLocation(position: LatLon) {
        carPosition = position
        engine.onLocation(position)
        if (driving) mapController.follow(position)
        renderMap()
    }

    private fun onTripProgress() {
        refreshStrip()
        if (driving) refreshDrive()
    }

    private fun onTripArrived() {
        // 시연 재생에서는 도착하고 잠시 뒤 차를 멈춘 것으로 한다.
        if (demoMode) handler.postDelayed(autoPark, 2500L)
    }

    /** 계획한 길에서 벗어났다. 달리는 중이면 남은 길을 다시 받고, 멈춰 있으면 지금 위치에서 처음부터 다시 잡는다. */
    private fun onTripOffRoute(position: LatLon) {
        if (routePending) return
        val last = lastPlanFrom
        // 같은 자리에서 길을 되풀이해 받지 않는다(예: 도로에서 떨어진 주차장).
        if (last != null && Geo.distance(position, last) < REPLAN_MIN_MOVE_M) return
        if (Geo.distance(position, destination.position) > MAX_TRIP_M) {
            lastPlanFrom = position
            showHint(getString(R.string.too_far_notice))
            return
        }
        if (driving) reroute(position, null) else planTrip(position, false)
    }

    private fun onDrivingChanged(now: Boolean) {
        if (driving == now) return
        driving = now
        handler.removeCallbacks(autoPark)
        if (now) {
            hideKeyboard()
            if (engine.route != null && !engine.arrived && engine.progress < 50.0) {
                say(Sentences.depart(engine.remaining(), destination.name))
            }
            val position = carPosition
            if (position != null) mapController.follow(position)
            if (demoMode) replay.start { p -> onLocation(p) }
        } else {
            if (demoMode) replay.stop()
            if (recreatePending) {
                recreatePending = false
                recreate()
                return
            }
            showRouteOnMap(true)
        }
        applyDrivingUi()
        refreshAll()
    }

    /** 달리는 중인지에 따라 화면을 잠그거나 연다. */
    private fun applyDrivingUi() {
        drawingView.locked = driving
        mapController.setGesturesEnabled(!driving)
        editPanel.visibility = if (driving) View.GONE else View.VISIBLE
        drivePanel.visibility = if (driving) View.VISIBLE else View.GONE
        modeChip.setText(if (driving) R.string.mode_driving else R.string.mode_parked)
        modeChip.setBackgroundResource(if (driving) R.drawable.chip_lock_bg else R.drawable.chip_bg)
        modeChip.setTextColor(color(if (driving) R.color.lock else R.color.accent))
        handler.removeCallbacks(restoreNote)
        restoreNote.run()
        refreshDestBar()
        refreshDemoButtons()
    }

    private fun showRouteOnMap(animate: Boolean) {
        val all = ArrayList<LatLon>(trail)
        val route = engine.route
        if (route != null) all.addAll(route.points)
        mapController.showWhole(all, animate)
    }

    // ------------------------------------------------------------------ 시연 단추

    private fun refreshDemoButtons() {
        btnDemo.visibility = if (VehicleFactory.USES_PLEOS_SDK) View.VISIBLE else View.GONE
        btnDemo.setText(if (demoMode) R.string.demo_on else R.string.demo_off)
        btnDemo.isEnabled = !driving
        btnDemo.alpha = if (driving) 0.4f else 1f
        // 차량이 주행 상태를 알려 주지 않을 때는 화면 단추로 출발·정지를 바꾼다.
        val manual = demoMode || vehicleState == null
        btnGo.visibility = if (manual) View.VISIBLE else View.GONE
        btnGo.setText(if (driving) R.string.demo_stop else R.string.demo_go)
        btnBlock.visibility = if (demoMode && driving) View.VISIBLE else View.GONE
    }

    private fun onGoPressed() {
        if (manualState.isDriving) {
            manualState.set(false)
            return
        }
        if (engine.route == null) {
            showHint(getString(R.string.finding_route))
            return
        }
        if (engine.arrived) resetTrip()
        manualState.set(true)
    }

    /** 시연용: 앞길이 막혀 순정 내비가 길을 바꾼 상황을 만든다. 조금 앞에서 옆으로 돌아가는 길을 받는다. */
    private fun onBlockPressed() {
        val route = engine.route ?: return
        val from = carPosition ?: return
        if (!driving || engine.arrived || routePending) return
        val ahead = engine.progress + 350.0
        if (ahead > route.total - 300.0) return // 목적지가 가까우면 돌아갈 길이 없다
        val i = route.segmentAt(ahead)
        val via = Geo.offsetRight(route.points[i - 1], route.points[i], route.pointAt(ahead), -DETOUR_M)
        reroute(from, via)
    }

    /** pleos 빌드에서만 보인다. 시연 재생과 차량 신호를 바꾼다. */
    private fun onDemoToggled() {
        if (driving) return
        demoMode = !demoMode
        carPosition = null
        useSignals()
        refreshDemoButtons()
        planTrip(trip.home, false)
    }

    private fun onDestinationChosen(chosen: Destination) {
        if (driving) return
        destination = chosen
        refreshDestBar()
        refreshAll()
        planTrip(startPoint(), true)
    }

    // ------------------------------------------------------------------ 그리기

    private fun refreshCrayons() {
        crayonRow.removeAllViews()
        for ((i, c) in CRAYONS.withIndex()) {
            val dot = View(this)
            val circle = GradientDrawable()
            circle.shape = GradientDrawable.OVAL
            circle.setColor(c)
            circle.setStroke(px(if (i == crayon) 4 else 1), color(if (i == crayon) R.color.ink else R.color.line))
            dot.background = circle
            dot.setOnClickListener {
                crayon = i
                refreshCrayons()
            }
            val lp = LinearLayout.LayoutParams(px(40), px(40))
            lp.marginStart = px(4)
            lp.marginEnd = px(4)
            crayonRow.addView(dot, lp)
        }
        drawingView.penColor = CRAYONS[crayon]
    }

    private fun setPen(thick: Boolean) {
        drawingView.penWidthRatio = if (thick) 0.08f else 0.04f
        penThin.isSelected = !thick
        penThick.isSelected = thick
    }

    private fun saveDrawing() {
        if (driving) return
        val bitmap = drawingView.exportBitmap(256)
        if (bitmap == null) {
            drawHint.setText(R.string.draw_empty)
            return
        }
        val drawing = store.add(drawName.text.toString(), bitmap)
        drawingView.clear()
        drawName.setText("")
        drawHint.setText(R.string.draw_hint)
        hideKeyboard()
        pickedId = drawing.id
        selectTab(TAB_ASSIGN)
        showHint(getString(R.string.saved_now_assign))
    }

    private fun deletePicked() {
        if (driving) return
        val id = pickedId ?: return
        val usedAsPattern = listOf(Slots.BG_BUILDING, Slots.BG_PARK, Slots.BG_WATER).any { store.drawingFor(it)?.id == id }
        store.remove(id)
        // 그림이 사라진 내 장소는 함께 지운다.
        val emptySpots = store.customSpots.filter { store.drawingFor(Slots.spot(it.id)) == null }
        for (spot in emptySpots) store.removeSpot(spot.id)
        pickedId = null
        afterAssignmentChanged(usedAsPattern)
    }

    // ------------------------------------------------------------------ 붙이기

    private fun pick(id: String) {
        pickedId = if (pickedId == id) null else id
        refreshDrawer()
        refreshSlots()
    }

    /** 목록이나 지도에서 붙일 곳을 눌렀을 때. 고른 그림을 붙이고, 같은 그림이 이미 붙어 있으면 뗀다. */
    private fun onSlotChosen(slot: String) {
        if (driving) return
        val drawing = store.byId(pickedId)
        if (drawing == null) {
            showHint(getString(R.string.pick_first))
            return
        }
        if (slot.startsWith(AUTO_SLOT_PREFIX)) {
            // 빈 자리에 처음 붙일 때: 그 자리를 '내 장소'로 저장한다. 다음에 길이 조금 달라져도 같은 곳에 그림이 남는다.
            val place = placeholders.firstOrNull { Slots.spot(it.spot.id) == slot } ?: return
            val spot = store.addSpot(drawing.name, place.spot.position)
            store.toggle(Slots.spot(spot.id), drawing.id)
            showHint(getString(R.string.spot_added))
        } else {
            val attached = store.toggle(slot, drawing.id)
            // 그림을 뗀 내 장소는 지운다.
            if (!attached && slot.startsWith(MY_SLOT_PREFIX)) store.removeSpot(slot.removePrefix("spot:"))
        }
        afterAssignmentChanged(Slots.isBackground(slot))
    }

    /** 지도를 길게 눌렀을 때. 가는 길 가까이면 그 자리에 고른 그림을 붙인다. */
    private fun onMapLongPress(position: LatLon) {
        if (driving) return
        val drawing = store.byId(pickedId)
        if (drawing == null) {
            showHint(getString(R.string.pick_first))
            return
        }
        val route = engine.route
        if (route == null || route.project(position).distance > NEAR_ROUTE_M) {
            showHint(getString(R.string.off_route_tap))
            return
        }
        val before = computeStops(route).map { it.spot.id }
        val spot = store.addSpot(drawing.name, position)
        store.toggle(Slots.spot(spot.id), drawing.id)
        val after = computeStops(route).map { it.spot.id }
        // 새 자리가 뽑히지 않았거나 원래 있던 그림을 밀어냈으면 되돌린다.
        if (spot.id !in after || !after.containsAll(before)) {
            store.removeSpot(spot.id)
            showHint(getString(R.string.spot_rejected))
            return
        }
        showHint(getString(R.string.spot_added))
        afterAssignmentChanged(false)
    }

    private fun afterAssignmentChanged(patternChanged: Boolean) {
        // 지운 내 장소가 "지나온 그림"에 남아 있지 않게 한다.
        passedEarlier.removeAll { earlier -> store.customSpots.none { it.id == earlier.id } }
        refreshStops()
        if (patternChanged) mapController.reloadStyle()
        refreshAll()
    }

    // ------------------------------------------------------------------ 화면 다시 그리기

    private fun refreshAll() {
        renderMap()
        refreshStrip()
        refreshPanel()
    }

    private fun refreshPanel() {
        if (driving) {
            refreshDrive()
        } else {
            refreshDrawer()
            refreshSlots()
            refreshPreview()
        }
    }

    private fun selectTab(which: Int) {
        tab = which
        tabDraw.isSelected = which == TAB_DRAW
        tabAssign.isSelected = which == TAB_ASSIGN
        tabPreview.isSelected = which == TAB_PREVIEW
        paneDraw.visibility = if (which == TAB_DRAW) View.VISIBLE else View.GONE
        paneAssign.visibility = if (which == TAB_ASSIGN) View.VISIBLE else View.GONE
        panePreview.visibility = if (which == TAB_PREVIEW) View.VISIBLE else View.GONE
        if (which != TAB_DRAW) hideKeyboard()
        refreshPanel()
    }

    private fun labelOf(spot: Spot): String = store.drawingFor(Slots.spot(spot.id))?.name ?: spot.name

    private fun renderMap() {
        val items = ArrayList<MapController.Item>()
        val next = if (driving) engine.nextStop() else null

        items.add(MapController.Item(Slots.HOME, trip.home, trip.homeName, picture(PIC_HOME), false, false))
        items.add(
            MapController.Item(
                Slots.destination(destination.id), destination.position, destination.name, picture(PIC_DEST),
                false, driving && next == null
            )
        )
        for (spot in passedEarlier) {
            items.add(MapController.Item(Slots.spot(spot.id), spot.position, labelOf(spot), picture(PIC_PLUS), true, false))
        }
        for (stop in engine.stops) {
            items.add(
                MapController.Item(
                    Slots.spot(stop.spot.id), stop.spot.position, labelOf(stop.spot), picture(PIC_PLUS),
                    engine.isPassed(stop), stop == next
                )
            )
        }
        // 빈 자리는 꾸밀 수 있을 때만 보여 준다.
        if (!driving) {
            for (place in placeholders) {
                if (place.s <= engine.progress) continue
                items.add(MapController.Item(Slots.spot(place.spot.id), place.spot.position, "", picture(PIC_PLUS), false, false))
            }
        }

        val route = engine.route
        val ahead: List<LatLon> = if (route != null) route.tail(engine.progress) else emptyList()
        val passed = ArrayList<LatLon>(trail)
        if (route != null) passed.addAll(route.head(engine.progress))
        mapController.render(MapController.Scene(ahead, passed, items, carPosition))
    }

    private fun refreshStrip() {
        val entries = ArrayList<JourneyStripView.Entry>()
        if (startedAtHome) {
            val departed = engine.progress > 30.0 || trail.isNotEmpty()
            entries.add(
                JourneyStripView.Entry(
                    slotThumb(Slots.HOME, PIC_HOME, 48), trip.homeName,
                    if (departed) JourneyStripView.State.PASSED else JourneyStripView.State.NORMAL
                )
            )
        }
        for (spot in passedEarlier) {
            entries.add(
                JourneyStripView.Entry(slotThumb(Slots.spot(spot.id), PIC_PLUS, 48), labelOf(spot), JourneyStripView.State.PASSED)
            )
        }
        val next = engine.nextStop()
        for (stop in engine.stops) {
            val state = when {
                engine.isPassed(stop) -> JourneyStripView.State.PASSED
                driving && stop == next -> JourneyStripView.State.NEXT
                else -> JourneyStripView.State.NORMAL
            }
            entries.add(JourneyStripView.Entry(slotThumb(Slots.spot(stop.spot.id), PIC_PLUS, 48), labelOf(stop.spot), state))
        }
        val atEnd = engine.arrived || (driving && next == null)
        entries.add(
            JourneyStripView.Entry(
                slotThumb(Slots.destination(destination.id), PIC_DEST, 48), destination.name,
                if (atEnd) JourneyStripView.State.NEXT else JourneyStripView.State.NORMAL
            )
        )
        strip.setEntries(entries)
    }

    private fun refreshDestBar() {
        destBar.removeAllViews()
        for (d in trip.destinations) {
            val chip = TextView(this)
            chip.text = d.name
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            chip.setTextColor(color(R.color.ink))
            chip.gravity = Gravity.CENTER
            chip.setBackgroundResource(R.drawable.btn_bg)
            chip.setPadding(px(14), 0, px(14), 0)
            chip.isSelected = d.id == destination.id
            // 달리는 중에는 목적지를 바꿀 수 없다.
            chip.alpha = if (driving && d.id != destination.id) 0.4f else 1f
            chip.setOnClickListener { onDestinationChosen(d) }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, px(44))
            lp.marginEnd = px(6)
            destBar.addView(chip, lp)
        }
    }

    private fun refreshDrawer() {
        drawer.removeAllViews()
        for (d in store.drawings) {
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            cell.setBackgroundResource(R.drawable.row_bg)
            cell.setPadding(px(6), px(6), px(6), px(6))
            cell.isSelected = d.id == pickedId

            val image = ImageView(this)
            image.setImageBitmap(drawingThumb(d, 56))
            cell.addView(image, LinearLayout.LayoutParams(px(56), px(56)))

            val name = textLine(if (d.example) d.name + getString(R.string.example_suffix) else d.name, 12f, R.color.ink2)
            name.gravity = Gravity.CENTER
            cell.addView(name, LinearLayout.LayoutParams(px(76), LinearLayout.LayoutParams.WRAP_CONTENT))

            cell.setOnClickListener { pick(d.id) }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = px(6)
            drawer.addView(cell, lp)
        }
        val picked = store.byId(pickedId)
        drawerDelete.visibility = if (picked != null && !picked.example) View.VISIBLE else View.GONE
    }

    private fun refreshSlots() {
        slotList.removeAllViews()
        addSlot(Slots.CAR, getString(R.string.slot_car), slotThumb(Slots.CAR, PIC_CAR, 44))
        addSlot(Slots.HOME, trip.homeName, slotThumb(Slots.HOME, PIC_HOME, 44))
        val destSlot = Slots.destination(destination.id)
        addSlot(destSlot, destination.name, slotThumb(destSlot, PIC_DEST, 44))
        for ((i, stop) in engine.stops.withIndex()) {
            val slot = Slots.spot(stop.spot.id)
            addSlot(slot, getString(R.string.slot_spot, i + 1), slotThumb(slot, PIC_PLUS, 44))
        }
        addSlot(Slots.BG_BUILDING, getString(R.string.slot_building), patternThumb(Slots.BG_BUILDING, MapController.BASE_BUILDING, 44))
        addSlot(Slots.BG_PARK, getString(R.string.slot_park), patternThumb(Slots.BG_PARK, MapController.BASE_PARK, 44))
        addSlot(Slots.BG_WATER, getString(R.string.slot_water), patternThumb(Slots.BG_WATER, MapController.BASE_WATER, 44))
    }

    private fun addSlot(slot: String, title: String, image: Bitmap) {
        val drawing = store.drawingFor(slot)
        val subtitle = drawing?.name ?: getString(R.string.default_drawing)
        val selected = drawing != null && drawing.id == pickedId
        slotList.addView(iconRow(image, title, subtitle, selected) { onSlotChosen(slot) }, rowParams())
    }

    private fun refreshPreview() {
        previewSum.text = if (engine.route == null) getString(R.string.finding_route) else engine.summary()
        previewList.removeAllViews()
        if (startedAtHome) {
            previewList.addView(iconRow(slotThumb(Slots.HOME, PIC_HOME, 44), trip.homeName, "", false, null), rowParams())
        }
        for (stop in engine.stops) {
            val slot = Slots.spot(stop.spot.id)
            previewList.addView(iconRow(slotThumb(slot, PIC_PLUS, 44), labelOf(stop.spot), "", false, null), rowParams())
        }
        val destSlot = Slots.destination(destination.id)
        previewList.addView(iconRow(slotThumb(destSlot, PIC_DEST, 44), destination.name, "", false, null), rowParams())
    }

    /** 달리는 중 오른쪽에 보여 주는 "다음 그림". 그림을 모두 지나면 목적지를 보여 준다. */
    private fun refreshDrive() {
        val route = engine.route
        val next = engine.nextStop()
        val slot: String
        val fallback: String
        val name: String
        val distance: String
        if (next != null) {
            slot = Slots.spot(next.spot.id)
            fallback = PIC_PLUS
            name = labelOf(next.spot)
            distance = distanceText(next.s - engine.progress)
        } else {
            slot = Slots.destination(destination.id)
            fallback = PIC_DEST
            name = destination.name
            distance = if (route == null || engine.arrived) "" else distanceText(route.total - engine.progress)
        }
        val key = slot + "|" + (store.drawingFor(slot)?.id ?: fallback)
        if (key != lastNextKey) {
            lastNextKey = key
            nextImage.setImageBitmap(slotThumb(slot, fallback, 112))
        }
        nextName.text = name
        nextDistance.text = distance
        driveSum.text = if (route == null) getString(R.string.finding_route) else engine.summary()
    }

    /** 남은 거리를 100 m 단위로 말한다. 숫자가 자주 바뀌지 않게 하려는 것이다. */
    private fun distanceText(meters: Double): String {
        if (meters <= TripEngine.ANNOUNCE_AHEAD_M) return getString(R.string.next_soon)
        val rounded = (ceil(meters / 100.0) * 100.0).toInt()
        return getString(R.string.next_far, rounded)
    }

    // ------------------------------------------------------------------ 말하기와 안내

    /** 아이에게 들려주는 문장. 소리로 읽고, 지도 아래에 글로도 잠시 보여 준다. */
    private fun say(text: String) {
        speaker.speak(text)
        caption.text = text
        caption.visibility = View.VISIBLE
        handler.removeCallbacks(hideCaption)
        handler.postDelayed(hideCaption, 6000L)
    }

    /** 지도 왼쪽 위에 잠깐 보여 주는 안내. 빈 글이면 바로 원래 글로 되돌린다. */
    private fun showHint(text: String) {
        handler.removeCallbacks(restoreNote)
        if (text.isEmpty()) {
            restoreNote.run()
            return
        }
        mapNote.text = text
        handler.postDelayed(restoreNote, 4500L)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(drawName.windowToken, 0)
        drawName.clearFocus()
    }

    // ------------------------------------------------------------------ 작은 그림과 줄 만들기

    private fun px(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun color(id: Int): Int = ContextCompat.getColor(this, id)

    /** 그림을 붙이지 않은 자리에 쓰는 기본 그림. 한 번만 만든다. */
    private fun picture(key: String): Bitmap = pictures.getOrPut(key) {
        when (key) {
            PIC_HOME -> Stickers.housePicture(PICTURE_PX, false)
            PIC_DEST -> Stickers.housePicture(PICTURE_PX, true)
            PIC_CAR -> Stickers.carPuck(PICTURE_PX)
            else -> Stickers.plusPicture(PICTURE_PX)
        }
    }

    /** 목록과 여정 띠에 쓰는 작은 스티커. 같은 그림은 한 번만 만든다. */
    private fun slotThumb(slot: String, fallback: String, dp: Int): Bitmap {
        val drawing = store.drawingFor(slot)
        val key = "t|" + (drawing?.id ?: fallback) + "|" + dp
        return thumbs.getOrPut(key) { Stickers.sticker(drawing?.bitmap ?: picture(fallback), px(dp)) }
    }

    private fun drawingThumb(drawing: Drawing, dp: Int): Bitmap =
        thumbs.getOrPut("t|" + drawing.id + "|" + dp) { Stickers.sticker(drawing.bitmap, px(dp)) }

    private fun patternThumb(slot: String, base: Int, dp: Int): Bitmap {
        val drawing = store.drawingFor(slot)
        val key = "p|" + (drawing?.id ?: "none") + "|" + base + "|" + dp
        return thumbs.getOrPut(key) {
            if (drawing != null) Stickers.patternTile(drawing.bitmap, base, px(dp)) else Stickers.swatch(base, px(dp))
        }
    }

    private fun textLine(text: String, sp: Float, colorId: Int): TextView {
        val view = TextView(this)
        view.text = text
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        view.setTextColor(color(colorId))
        view.maxLines = 1
        view.ellipsize = TextUtils.TruncateAt.END
        return view
    }

    /** 작은 그림 + 제목 + 작은 글 한 줄. [onClick]이 있으면 누를 수 있다. */
    private fun iconRow(image: Bitmap, title: String, subtitle: String, selected: Boolean, onClick: (() -> Unit)?): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setBackgroundResource(R.drawable.row_bg)
        row.setPadding(px(8), px(6), px(10), px(6))
        row.isSelected = selected

        val icon = ImageView(this)
        icon.setImageBitmap(image)
        row.addView(icon, LinearLayout.LayoutParams(px(44), px(44)))

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.addView(textLine(title, 15f, R.color.ink))
        if (subtitle.isNotEmpty()) texts.addView(textLine(subtitle, 12f, R.color.ink2))
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lp.marginStart = px(10)
        row.addView(texts, lp)

        if (onClick != null) row.setOnClickListener { onClick() }
        return row
    }

    private fun rowParams(): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = px(6)
        return lp
    }

    companion object {
        private const val TAG = "MainActivity"

        private const val TAB_DRAW = 0
        private const val TAB_ASSIGN = 1
        private const val TAB_PREVIEW = 2
        private const val KEY_DESTINATION = "destination"
        private const val KEY_TAB = "tab"

        private const val PIC_HOME = "home"
        private const val PIC_DEST = "dest"
        private const val PIC_CAR = "car"
        private const val PIC_PLUS = "plus"
        private const val PICTURE_PX = 192

        /** 자리 이름표의 머리글. 빈 자리는 "spot:auto-…", 내 장소는 "spot:my…" 이다(Slots.spot, DrawingStore.addSpot). */
        private const val AUTO_SLOT_PREFIX = "spot:auto-"
        private const val MY_SLOT_PREFIX = "spot:my"

        /** 한 번의 이동에서 보여 주는 길가 그림은 많아야 여섯 장. 너무 많으면 아이에게 부담이 된다. */
        private const val MAX_STOPS = 6

        /** 길가 그림 사이의 최소 간격(m)과, 길에서 떨어져도 되는 거리(m). */
        private const val MIN_GAP_M = 250.0
        private const val NEAR_ROUTE_M = 70.0

        /** 시연에서 길을 막았을 때 옆으로 돌아가는 거리(m). */
        private const val DETOUR_M = 300.0

        /** 길에서 벗어났을 때, 지난번에 길을 받은 곳에서 이만큼(m) 움직인 뒤에만 다시 받는다. */
        private const val REPLAN_MIN_MOVE_M = 150.0

        /** 차 위치가 목적지에서 이보다 멀면(m) 위치 설정이 잘못된 것으로 본다. */
        private const val MAX_TRIP_M = 150_000.0

        private val CRAYONS = intArrayOf(
            Color.rgb(210, 73, 63),
            Color.rgb(226, 185, 59),
            Color.rgb(79, 154, 91),
            Color.rgb(63, 124, 196),
            Color.rgb(138, 101, 72),
            Color.rgb(42, 42, 42)
        )
    }
}
