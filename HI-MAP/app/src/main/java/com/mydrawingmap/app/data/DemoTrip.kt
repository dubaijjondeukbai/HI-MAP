package com.mydrawingmap.app.data

import android.content.Context
import android.util.Log
import com.mydrawingmap.app.core.LatLon
import org.json.JSONObject

/** 즐겨찾는 목적지 하나. */
data class Destination(val id: String, val name: String, val position: LatLon, val address: String)

/**
 * 출발지(집)와 즐겨찾는 목적지 목록.
 * assets/demo_trip.json 에서 읽는다. 좌표는 시연용이므로 실제 좌표로 바꿔 쓴다.
 */
class DemoTrip(val homeName: String, val home: LatLon, val destinations: List<Destination>) {

    companion object {
        private const val TAG = "DemoTrip"

        fun load(context: Context): DemoTrip {
            try {
                val text = context.assets.open("demo_trip.json").bufferedReader().use { it.readText() }
                val root = JSONObject(text)
                val h = root.getJSONObject("home")
                val list = ArrayList<Destination>()
                val arr = root.getJSONArray("destinations")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        Destination(
                            o.getString("id"),
                            o.getString("name"),
                            LatLon(o.getDouble("lat"), o.getDouble("lon")),
                            o.optString("address", "")
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    return DemoTrip(h.optString("name", "우리 집"), LatLon(h.getDouble("lat"), h.getDouble("lon")), list)
                }
            } catch (e: Exception) {
                Log.w(TAG, "demo_trip.json 을 읽지 못해 기본값을 씁니다", e)
            }
            return fallback()
        }

        private fun fallback() = DemoTrip(
            "우리 집",
            LatLon(37.4979, 127.0276),
            listOf(Destination("school", "학교", LatLon(37.48544722, 127.03636666), "서울시 강남구 남부순환로 2621"))
        )
    }
}
