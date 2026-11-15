package com.mydrawingmap.app.map

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.mydrawingmap.app.core.LatLon
import com.mydrawingmap.app.data.DrawingStore
import com.mydrawingmap.app.data.Slots
import com.mydrawingmap.app.draw.Stickers
import org.json.JSONObject
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * 그림 지도를 그린다 (MapLibre Native Android + OpenFreeMap 벡터 지도).
 *
 * 지도는 물·공원·건물·도로·장소가 층으로 쌓인 데이터이고, 각 층을 어떤 무늬·아이콘으로 그릴지는 스타일이 정한다.
 * 꾸미기는 그 스타일의 재료를 아이 그림으로 바꿔 끼우는 일이다.
 *  - 자리(집·목적지·길가 그림)와 내 차: 그림을 스타일 이미지로 등록하고 기호 층(SymbolLayer)에 올린다.
 *  - 건물·공원·물: 그림으로 만든 무늬 칸을 면 층(FillLayer)의 fill-pattern으로 넣는다.
 * 도로와 경로선은 여기서만 그리며 아이가 바꿀 수 없다.
 *
 * 경로선과 표지판 위치는 GeoJSON 문자열로 만들어 넘긴다. MapLibre 버전에 따라 패키지 이름이 다른
 * GeoJSON 클래스(Feature, Point 등)를 직접 쓰지 않으려는 것이다.
 */
class MapController(private val mapView: MapView, private val store: DrawingStore) {

    /**
     * 지도에 꽂을 표지판 하나.
     * [slot]에 붙은 그림이 있으면 그 그림을, 없으면 [fallback]을 카드에 넣는다. [label]은 카드 아래에 적는 이름이다.
     */
    class Item(
        val slot: String,
        val position: LatLon,
        val label: String,
        val fallback: Bitmap,
        val faded: Boolean,
        val highlighted: Boolean
    )

    /** 한 번에 그릴 장면 전체. */
    class Scene(
        val ahead: List<LatLon>,
        val passed: List<LatLon>,
        val items: List<Item>,
        val car: LatLon?
    )

    /** 지도에서 표지판을 눌렀을 때. */
    var onItemTapped: ((String) -> Unit)? = null

    /** 지도를 길게 눌렀을 때. */
    var onLongPress: ((LatLon) -> Unit)? = null

    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var lastScene: Scene? = null
    private val density = mapView.resources.displayMetrics.density

    /** 스타일에 등록한 이미지와, 그 이미지가 무엇으로 만들어졌는지. 같은 내용이면 다시 만들지 않는다. */
    private val registered = HashMap<String, String>()

    private fun px(dp: Int): Int = (dp * density).toInt()

    /**
     * 지도를 준비한다. 스타일까지 읽히면 [onReady]가, 지도를 불러오지 못하면 [onFailed]가 불린다.
     * (인터넷이 안 되면 지도 스타일과 타일을 받을 수 없다.)
     */
    fun start(onReady: () -> Unit, onFailed: (String) -> Unit) {
        mapView.addOnDidFailLoadingMapListener { message -> onFailed(message ?: "") }
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.addOnMapClickListener { point ->
                val hits = m.queryRenderedFeatures(m.projection.toScreenLocation(point), LAYER_ITEMS)
                val slot = hits.firstOrNull()?.getStringProperty("slot")
                if (slot != null) onItemTapped?.invoke(slot)
                slot != null
            }
            m.addOnMapLongClickListener { point ->
                onLongPress?.invoke(LatLon(point.latitude, point.longitude))
                true
            }
            loadStyle(onReady)
        }
    }

    private fun loadStyle(onReady: (() -> Unit)?) {
        val m = map ?: return
        m.setStyle(STYLE_URL) { st ->
            style = st
            registered.clear()
            addOurLayers(st)
            applyPatterns(st)
            lastScene?.let { render(it) }
            onReady?.invoke()
        }
    }

    /** 무늬를 붙이거나 뗐을 때 스타일을 다시 읽어 바탕 층을 새로 칠한다. */
    fun reloadStyle() {
        style = null
        loadStyle(null)
    }

    private fun addOurLayers(st: Style) {
        st.addSource(GeoJsonSource(SRC_PASSED))
        st.addSource(GeoJsonSource(SRC_ROUTE))
        st.addSource(GeoJsonSource(SRC_ITEMS))
        st.addSource(GeoJsonSource(SRC_CAR))

        st.addLayer(
            LineLayer(LAYER_PASSED, SRC_PASSED).withProperties(
                PropertyFactory.lineColor(Color.rgb(153, 193, 255)),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
        st.addLayer(
            LineLayer(LAYER_ROUTE_CASING, SRC_ROUTE).withProperties(
                PropertyFactory.lineColor(Color.WHITE),
                PropertyFactory.lineWidth(10f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
        st.addLayer(
            LineLayer(LAYER_ROUTE, SRC_ROUTE).withProperties(
                PropertyFactory.lineColor(Stickers.ROUTE),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
        // 표지판: 꼬리 끝(이미지 아래 가운데)이 실제 위치에 오게 한다. 이름은 이미지 안에 적혀 있다.
        st.addLayer(
            SymbolLayer(LAYER_ITEMS, SRC_ITEMS).withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconOpacity(Expression.get("opacity")),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true)
            )
        )
        st.addLayer(
            SymbolLayer(LAYER_CAR, SRC_CAR).withProperties(
                PropertyFactory.iconImage(IMG_CAR),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true)
            )
        )
    }

    /** 건물·공원·물 층에 아이 그림 무늬를 넣는다. 층 이름은 OpenFreeMap Positron 스타일의 것이다. */
    private fun applyPatterns(st: Style) {
        val targets = listOf(
            Triple(Slots.BG_BUILDING, "building", BASE_BUILDING),
            Triple(Slots.BG_PARK, "park", BASE_PARK),
            Triple(Slots.BG_WATER, "water", BASE_WATER)
        )
        for ((slot, layerId, base) in targets) {
            val drawing = store.drawingFor(slot) ?: continue
            val layer = st.getLayer(layerId) as? FillLayer
            if (layer == null) {
                Log.w(TAG, "스타일에 $layerId 층이 없습니다")
                continue
            }
            val image = "pattern-$layerId"
            st.addImage(image, Stickers.patternTile(drawing.bitmap, base, px(48)))
            layer.setProperties(PropertyFactory.fillPattern(image))
        }
    }

    /** [signature]가 바뀌었을 때만 이미지를 새로 만들어 등록한다. */
    private fun setImage(st: Style, name: String, signature: String, make: () -> Bitmap) {
        if (registered[name] == signature) return
        if (registered.containsKey(name)) st.removeImage(name)
        st.addImage(name, make())
        registered[name] = signature
    }

    /** 장면을 지도에 반영한다. 스타일이 아직 안 읽혔으면 기억해 두었다가 읽힌 뒤에 그린다. */
    fun render(scene: Scene) {
        lastScene = scene
        val st = style ?: return

        st.getSourceAs<GeoJsonSource>(SRC_ROUTE)?.setGeoJson(lineJson(scene.ahead))
        st.getSourceAs<GeoJsonSource>(SRC_PASSED)?.setGeoJson(lineJson(scene.passed))

        val features = ArrayList<String>()
        for (item in scene.items) {
            val drawing = store.drawingFor(item.slot)
            val picture = drawing?.bitmap ?: item.fallback
            val source = drawing?.id ?: ("fallback-" + System.identityHashCode(item.fallback))
            val image = "item-" + item.slot
            setImage(st, image, source + "|" + item.label + "|" + item.highlighted) {
                Stickers.pin(
                    picture, item.label, px(PIN_DP),
                    if (item.highlighted) Stickers.ROUTE else Stickers.PAPER_EDGE,
                    px(if (item.highlighted) 4 else 2).toFloat()
                )
            }
            val properties = "\"slot\":" + JSONObject.quote(item.slot) +
                ",\"icon\":" + JSONObject.quote(image) +
                ",\"opacity\":" + (if (item.faded) "0.45" else "1")
            features.add(pointJson(item.position, properties))
        }
        st.getSourceAs<GeoJsonSource>(SRC_ITEMS)?.setGeoJson(collectionJson(features))

        val carDrawing = store.drawingFor(Slots.CAR)
        if (carDrawing != null) {
            setImage(st, IMG_CAR, "drawing-" + carDrawing.id) {
                Stickers.sticker(carDrawing.bitmap, px(46), Stickers.ROUTE, px(3).toFloat())
            }
        } else {
            setImage(st, IMG_CAR, "puck") { Stickers.carPuck(px(40)) }
        }
        val car = scene.car
        val carFeatures = ArrayList<String>()
        if (car != null) carFeatures.add(pointJson(car, ""))
        st.getSourceAs<GeoJsonSource>(SRC_CAR)?.setGeoJson(collectionJson(carFeatures))
    }

    // ---- GeoJSON 문자열 만들기 (좌표는 경도, 위도 순서) ----

    private fun collectionJson(features: List<String>): String =
        "{\"type\":\"FeatureCollection\",\"features\":[" + features.joinToString(",") + "]}"

    /** 점 하나. [properties]는 따옴표까지 갖춘 "이름":값 목록이다. */
    private fun pointJson(position: LatLon, properties: String): String =
        "{\"type\":\"Feature\",\"properties\":{" + properties + "}," +
            "\"geometry\":{\"type\":\"Point\",\"coordinates\":[" + position.lon + "," + position.lat + "]}}"

    /** 선 하나. 점이 두 개보다 적으면 빈 모음을 돌려준다. */
    private fun lineJson(points: List<LatLon>): String {
        if (points.size < 2) return collectionJson(emptyList())
        val sb = StringBuilder(points.size * 24 + 128)
        sb.append("{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
        for ((i, p) in points.withIndex()) {
            if (i > 0) sb.append(',')
            sb.append('[').append(p.lon).append(',').append(p.lat).append(']')
        }
        sb.append("]}}")
        return collectionJson(listOf(sb.toString()))
    }

    /** 멈춰 있을 때: 길 전체가 한눈에 들어오게 맞춘다. 표지판이 잘리지 않게 위쪽 여백을 넉넉히 둔다. */
    fun showWhole(points: List<LatLon>, animate: Boolean) {
        val m = map ?: return
        if (points.size < 2) return
        val builder = LatLngBounds.Builder()
        for (p in points) builder.include(LatLng(p.lat, p.lon))
        try {
            val update = CameraUpdateFactory.newLatLngBounds(builder.build(), px(64), px(120), px(64), px(56))
            if (animate) m.easeCamera(update, 600) else m.moveCamera(update)
        } catch (e: Exception) {
            Log.w(TAG, "지도 범위를 맞추지 못했습니다", e)
        }
    }

    /** 길을 아직 모를 때: 한 곳을 가운데 두고 보여 준다. */
    fun jumpTo(position: LatLon, zoom: Double) {
        val m = map ?: return
        m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(position.lat, position.lon), zoom))
    }

    /** 달리는 중: 내 차를 가운데 두고 천천히 따라간다. */
    fun follow(position: LatLon) {
        val m = map ?: return
        m.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(position.lat, position.lon), FOLLOW_ZOOM), 900)
    }

    /** 달리는 중에는 지도를 손으로 움직일 수 없게 한다. */
    fun setGesturesEnabled(enabled: Boolean) {
        val m = map ?: return
        m.uiSettings.setAllGesturesEnabled(enabled)
        if (enabled) {
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
        }
    }

    companion object {
        private const val TAG = "MapController"

        /** 채도가 낮은 OpenFreeMap 기본 스타일. 지도 데이터는 OpenStreetMap이다. */
        const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

        private const val SRC_ROUTE = "mdm-route"
        private const val SRC_PASSED = "mdm-passed"
        private const val SRC_ITEMS = "mdm-items"
        private const val SRC_CAR = "mdm-car"
        private const val LAYER_ROUTE = "mdm-route-line"
        private const val LAYER_ROUTE_CASING = "mdm-route-casing"
        private const val LAYER_PASSED = "mdm-passed-line"
        private const val LAYER_ITEMS = "mdm-items-symbols"
        private const val LAYER_CAR = "mdm-car-symbol"
        private const val IMG_CAR = "mdm-car-image"
        private const val FOLLOW_ZOOM = 16.0

        /** 표지판 그림 칸 한 변의 길이(dp). */
        private const val PIN_DP = 56

        val BASE_BUILDING: Int = Color.rgb(224, 225, 230)
        val BASE_PARK: Int = Color.rgb(207, 230, 200)
        val BASE_WATER: Int = Color.rgb(181, 213, 238)
    }
}
