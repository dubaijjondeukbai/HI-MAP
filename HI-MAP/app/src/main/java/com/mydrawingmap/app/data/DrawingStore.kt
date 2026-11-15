package com.mydrawingmap.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.mydrawingmap.app.core.LatLon
import com.mydrawingmap.app.core.Spot
import com.mydrawingmap.app.draw.ExampleDrawings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** 그림 한 장. [example]이 참이면 앱에 들어 있는 예시 그림이다. */
class Drawing(val id: String, val name: String, val bitmap: Bitmap, val example: Boolean)

/** 그림을 붙일 수 있는 자리의 이름표. 저장 파일과 지도에서 같은 문자열을 쓴다. */
object Slots {
    const val CAR = "car"
    const val HOME = "home"
    const val BG_BUILDING = "bg:building"
    const val BG_PARK = "bg:park"
    const val BG_WATER = "bg:water"
    fun destination(id: String) = "dest:$id"
    fun spot(id: String) = "spot:$id"
    fun isBackground(slot: String) = slot.startsWith("bg:")
}

/**
 * 아이의 그림, 그림을 붙인 자리, 직접 찍은 내 장소를 차 안 저장소에만 보관한다.
 * 서버로 보내지 않는다. 파일: files/drawings/<id>.png 와 index.json
 */
class DrawingStore(private val context: Context) {

    val drawings = ArrayList<Drawing>()
    val customSpots = ArrayList<Spot>()
    private val assigned = HashMap<String, String>()
    private var counter = 0

    /** 저장된 것이 하나도 없는 첫 실행인지. 길가에 예시 그림을 붙일지 정한 뒤 화면 쪽에서 false로 바꾼다. */
    var firstRun = false

    private fun dir(): File = File(context.filesDir, "drawings").apply { mkdirs() }
    private fun indexFile(): File = File(dir(), "index.json")

    fun load() {
        drawings.clear()
        customSpots.clear()
        assigned.clear()
        drawings.addAll(ExampleDrawings.all())
        var hadIndex = false
        try {
            val file = indexFile()
            if (file.exists()) {
                hadIndex = true
                val root = JSONObject(file.readText())
                counter = root.optInt("counter", 0)
                val list = root.optJSONArray("drawings") ?: JSONArray()
                for (i in 0 until list.length()) {
                    val o = list.getJSONObject(i)
                    val id = o.getString("id")
                    val bitmap = BitmapFactory.decodeFile(File(dir(), "$id.png").absolutePath) ?: continue
                    drawings.add(Drawing(id, o.optString("name", "내 그림"), bitmap, false))
                }
                val spots = root.optJSONArray("spots") ?: JSONArray()
                for (i in 0 until spots.length()) {
                    val o = spots.getJSONObject(i)
                    customSpots.add(Spot(o.getString("id"), o.optString("name", "내 장소"), LatLon(o.getDouble("lat"), o.getDouble("lon"))))
                }
                val a = root.optJSONObject("assigned")
                if (a != null) {
                    val keys = a.keys()
                    while (keys.hasNext()) {
                        val slot = keys.next()
                        val id = a.optString(slot)
                        if (byId(id) != null) assigned[slot] = id
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "저장된 그림을 읽지 못했습니다", e)
        }
        firstRun = !hadIndex
        if (!hadIndex) {
            // 처음 켰을 때는 예시 그림 두 장을 붙여 둔다.
            assigned[Slots.CAR] = ExampleDrawings.BUS
            assigned[Slots.HOME] = ExampleDrawings.HOUSE
        }
    }

    fun byId(id: String?): Drawing? = if (id == null) null else drawings.firstOrNull { it.id == id }

    /** 이 자리에 붙어 있는 그림. 없으면 null. */
    fun drawingFor(slot: String): Drawing? = byId(assigned[slot])

    fun add(name: String, bitmap: Bitmap): Drawing {
        counter++
        val id = "d$counter-${System.currentTimeMillis().toString(36)}"
        val finalName = name.trim().ifEmpty { "내 그림 $counter" }
        FileOutputStream(File(dir(), "$id.png")).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        val drawing = Drawing(id, finalName, bitmap, false)
        drawings.add(drawing)
        save()
        return drawing
    }

    fun remove(id: String) {
        val drawing = byId(id) ?: return
        if (drawing.example) return
        drawings.remove(drawing)
        val slots = assigned.filterValues { it == id }.keys.toList()
        for (slot in slots) assigned.remove(slot)
        File(dir(), "$id.png").delete()
        save()
    }

    /** 자리에 그림을 붙인다. 같은 그림이 이미 붙어 있으면 뗀다. 붙였으면 true. */
    fun toggle(slot: String, drawingId: String): Boolean {
        val on = assigned[slot] != drawingId
        if (on) assigned[slot] = drawingId else assigned.remove(slot)
        save()
        return on
    }

    fun addSpot(name: String, position: LatLon): Spot {
        counter++
        val spot = Spot("my$counter", name, position)
        customSpots.add(spot)
        save()
        return spot
    }

    fun removeSpot(id: String) {
        customSpots.removeAll { it.id == id }
        assigned.remove(Slots.spot(id))
        save()
    }

    private fun save() {
        try {
            val root = JSONObject()
            root.put("counter", counter)
            val list = JSONArray()
            for (d in drawings) if (!d.example) list.put(JSONObject().put("id", d.id).put("name", d.name))
            root.put("drawings", list)
            val spots = JSONArray()
            for (s in customSpots) {
                spots.put(JSONObject().put("id", s.id).put("name", s.name).put("lat", s.position.lat).put("lon", s.position.lon))
            }
            root.put("spots", spots)
            val a = JSONObject()
            for ((slot, id) in assigned) a.put(slot, id)
            root.put("assigned", a)
            indexFile().writeText(root.toString())
        } catch (e: Exception) {
            Log.w(TAG, "그림을 저장하지 못했습니다", e)
        }
    }

    companion object {
        private const val TAG = "DrawingStore"
    }
}
