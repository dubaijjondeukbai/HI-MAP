package com.mydrawingmap.app.vehicle

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mydrawingmap.app.core.LatLon

/**
 * 차량 위치 (Pleos FusedLocation SDK 1.1.3).
 *
 * 쓰는 순서는 SDK 문서를 따른다: 객체 생성 → isSupported() → initialize() →
 * registerFusedLocationCallback() → unregisterFusedLocationCallback() → release().
 *
 * LocationData의 속성 이름은 Pleos 공식 FusedLocation 샘플 앱에서 latitude, longitude, altitude, heading,
 * confidence, timestampMillis로 쓰인다. 값의 자료형까지는 확인하지 못해 이름으로 값을 읽는다([Reflect.number]).
 * Android Studio에서 자료형을 확인한 뒤 location.latitude 처럼 직접 읽도록 바꾸면 된다.
 *
 * 그림 앞 예고를 영역 진입 알림으로 바꿀 때는 같은 샘플의 호출을 따른다:
 * addProximityAlert(ProximityAlertRequest(latitude, longitude, radiusMeters, transitionTypes), ProximityAlertListener)
 * 와 removeProximityAlert(alertId). 알림 번호는 ProximityAlertListener.onRegistered(alertId)로 온다.
 *
 * 필요한 권한(AndroidManifest): pleos.car.permission.FUSED_LOCATION
 */
class PleosLocationBridge(context: Context) : LocationBridge {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var fused: FusedLocation? = null
    private var callback: ((LatLon) -> Unit)? = null

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            location ?: return
            val lat = Reflect.number(location, "latitude", "lat")
            val lon = Reflect.number(location, "longitude", "lon", "lng")
            if (lat == null || lon == null) {
                Log.w(TAG, "위치 값의 필드 이름을 찾지 못했습니다: $location")
                return
            }
            main.post { callback?.invoke(LatLon(lat, lon)) }
        }

        override fun onError(exception: Exception) {
            Log.w(TAG, "위치를 받지 못했습니다", exception)
        }
    }

    override fun start(onLocation: (LatLon) -> Unit) {
        callback = onLocation
        try {
            val f = FusedLocation(appContext)
            if (!f.isSupported()) {
                Log.w(TAG, "이 환경에서는 FusedLocation을 쓸 수 없습니다")
                return
            }
            f.initialize()
            f.registerFusedLocationCallback(locationListener)
            fused = f
        } catch (e: Exception) {
            Log.w(TAG, "FusedLocation 시작 실패", e)
        }
    }

    override fun stop() {
        val f = fused
        if (f != null) {
            try {
                f.unregisterFusedLocationCallback(locationListener)
                f.release()
            } catch (e: Exception) {
                Log.w(TAG, "FusedLocation 해제 실패", e)
            }
        }
        fused = null
        callback = null
    }

    companion object {
        private const val TAG = "PleosLocationBridge"
    }
}

/** 필드 이름을 정확히 모르는 SDK 값에서 숫자를 꺼낸다. getter와 필드를 차례로 찾아본다. */
internal object Reflect {
    fun number(target: Any, vararg names: String): Double? {
        for (name in names) {
            val getter = "get" + name.substring(0, 1).uppercase() + name.substring(1)
            try {
                val method = target.javaClass.methods.firstOrNull { it.name == getter && it.parameterTypes.isEmpty() }
                val value = method?.invoke(target)
                if (value is Number) return value.toDouble()
            } catch (e: Exception) {
                // 다음 후보로 넘어간다
            }
            try {
                val field = target.javaClass.getDeclaredField(name)
                field.isAccessible = true
                val value = field.get(target)
                if (value is Number) return value.toDouble()
            } catch (e: Exception) {
                // 다음 후보로 넘어간다
            }
        }
        return null
    }
}
