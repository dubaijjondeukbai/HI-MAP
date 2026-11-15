package com.mydrawingmap.app.route

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mydrawingmap.app.core.Geo
import com.mydrawingmap.app.core.LatLon
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 출발점에서 도착점까지 도로를 따라가는 좌표 목록을 구한다.
 * [fetch]는 네트워크를 쓰므로 화면 스레드에서 부르면 안 된다. 실패하면 null.
 */
interface RouteProvider {
    val name: String
    fun fetch(from: LatLon, to: LatLon, via: LatLon?): List<LatLon>?
}

private fun readJson(connection: HttpURLConnection): JSONObject? {
    return try {
        val code = connection.responseCode
        if (code !in 200..299) {
            Log.w("RouteProvider", "HTTP $code from ${connection.url.host}")
            null
        } else {
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        }
    } finally {
        connection.disconnect()
    }
}

private fun coord(p: LatLon) = String.format(Locale.US, "%.6f,%.6f", p.lon, p.lat)

/**
 * TMAP 자동차 경로안내 API. local.properties 에 TMAP_APP_KEY 를 넣었을 때만 쓴다.
 * 응답은 GeoJSON이고 LineString 좌표가 경로다. 받은 좌표는 24시간 넘게 저장하지 않는다(무료 이용 조건).
 * 요청 형식은 TMAP 문서에서 한 번 더 확인할 것.
 */
class TmapRouteProvider(private val appKey: String) : RouteProvider {
    override val name = "TMAP"

    override fun fetch(from: LatLon, to: LatLon, via: LatLon?): List<LatLon>? {
        val body = JSONObject()
            .put("startX", from.lon.toString()).put("startY", from.lat.toString())
            .put("endX", to.lon.toString()).put("endY", to.lat.toString())
            .put("reqCoordType", "WGS84GEO").put("resCoordType", "WGS84GEO")
            .put("searchOption", "0")
        if (via != null) body.put("passList", coord(via))

        val connection = URL("https://apis.openapi.sk.com/tmap/routes?version=1&format=json").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 6000
        connection.readTimeout = 8000
        connection.doOutput = true
        connection.setRequestProperty("appKey", appKey)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val root = readJson(connection) ?: return null
        val features = root.optJSONArray("features") ?: return null
        val out = ArrayList<LatLon>()
        for (i in 0 until features.length()) {
            val geometry = features.getJSONObject(i).optJSONObject("geometry") ?: continue
            if (geometry.optString("type") != "LineString") continue
            val coords = geometry.getJSONArray("coordinates")
            for (k in 0 until coords.length()) {
                val c = coords.getJSONArray(k)
                val p = LatLon(c.getDouble(1), c.getDouble(0))
                if (out.isEmpty() || Geo.distance(out[out.size - 1], p) > 0.5) out.add(p)
            }
        }
        return if (out.size >= 2) out else null
    }
}

/**
 * OSRM 공개 시연 서버(OpenStreetMap 도로 기반). 키 없이 쓸 수 있어 시연용으로 둔다.
 * 시연 서버라 응답이 느리거나 막힐 수 있으므로 실제 서비스에서는 쓰지 않는다.
 */
class OsrmRouteProvider : RouteProvider {
    override val name = "OSRM"

    override fun fetch(from: LatLon, to: LatLon, via: LatLon?): List<LatLon>? {
        val stops = if (via != null) listOf(from, via, to) else listOf(from, to)
        val path = stops.joinToString(";") { coord(it) }
        val url = "https://router.project-osrm.org/route/v1/driving/$path?overview=full&geometries=geojson"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 6000
        connection.readTimeout = 8000
        connection.setRequestProperty("User-Agent", "MyDrawingMap-demo")

        val root = readJson(connection) ?: return null
        val routes = root.optJSONArray("routes") ?: return null
        if (routes.length() == 0) return null
        val coords = routes.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
        val out = ArrayList<LatLon>()
        for (k in 0 until coords.length()) {
            val c = coords.getJSONArray(k)
            out.add(LatLon(c.getDouble(1), c.getDouble(0)))
        }
        return if (out.size >= 2) out else null
    }
}

/** 네트워크가 안 될 때의 마지막 수단. 도로를 따르지 않고 곧게 잇는다. */
class StraightRouteProvider : RouteProvider {
    override val name = RouteService.SOURCE_STRAIGHT

    override fun fetch(from: LatLon, to: LatLon, via: LatLon?): List<LatLon> {
        val corners = if (via != null) listOf(from, via, to) else listOf(from, to)
        val out = ArrayList<LatLon>()
        for (i in 1 until corners.size) {
            val a = corners[i - 1]
            val b = corners[i]
            // 50 m마다 점을 찍되, 아주 먼 거리에서도 점이 너무 많아지지 않게 한다.
            val steps = (Geo.distance(a, b) / 50.0).toInt().coerceIn(1, 400)
            for (k in 0 until steps) {
                val t = k.toDouble() / steps
                out.add(LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t))
            }
        }
        out.add(to)
        return out
    }
}

/**
 * 경로를 백그라운드에서 받아 화면 스레드로 돌려준다.
 * TMAP(키가 있을 때) → OSRM 시연 서버 → 직선 순서로 시도한다.
 */
class RouteService(tmapKey: String) {
    private val providers = ArrayList<RouteProvider>()
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    init {
        if (tmapKey.isNotBlank()) providers.add(TmapRouteProvider(tmapKey))
        providers.add(OsrmRouteProvider())
        providers.add(StraightRouteProvider())
    }

    /** [done]은 화면 스레드에서 불린다. 두 번째 값은 어느 경로원을 썼는지다. */
    fun request(from: LatLon, to: LatLon, via: LatLon?, done: (List<LatLon>, String) -> Unit) {
        executor.execute {
            var result: List<LatLon>? = null
            var source = ""
            for (provider in providers) {
                try {
                    result = provider.fetch(from, to, via)
                } catch (e: Exception) {
                    Log.w(TAG, "${provider.name} 경로를 받지 못했습니다: ${e.message}")
                }
                if (result != null) {
                    source = provider.name
                    break
                }
            }
            val points = result ?: listOf(from, to)
            main.post { done(points, source) }
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    companion object {
        private const val TAG = "RouteService"

        /** 도로 경로를 받지 못해 곧은 선으로 이었을 때 돌려주는 경로원 이름. */
        const val SOURCE_STRAIGHT = "직선"
    }
}
