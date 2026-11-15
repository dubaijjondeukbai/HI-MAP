package com.mydrawingmap.app.vehicle

import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mydrawingmap.app.data.Destination
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

/**
 * 순정 내비 연동 (Pleos NaviHelper SDK 3.0.5).
 *
 * 하는 일
 *  1. requestRoute(RouteInfo)로 순정 내비에 같은 목적지를 설정한다. 길 안내는 순정 내비가 한다.
 *  2. 길이 정해짐·길이 바뀜·도착 이벤트를 받아 그림 지도 쪽에 알린다.
 *
 * 이벤트를 받는 방식
 *  NaviHelperEventListener에는 콜백이 스물여섯 개 있다(Pleos 공식 NaviHelper 샘플 앱의
 *  NaviHelperViewModel.kt 기준). 이 앱이 쓰는 것은 onRouteStarted, onRoutingCompleted, onReRouteEnded,
 *  onDestinationArrived, onError 다섯 개뿐이라, 나머지를 일일이 비워 두지 않도록 "콜백 이름으로 받는"
 *  프록시를 쓴다. 전부 구현하려면 object : NaviHelperEventListener { override fun ... } 형태로 바꾼다.
 *
 * 필요한 권한(AndroidManifest): pleos.car.permission.NAVI_ROUTE, pleos.car.permission.NAVI_ROUTE_SEARCH
 */
class PleosNaviBridge(context: Context) : NaviBridge {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var helper: NaviHelper? = null
    private var listener: NaviBridge.Listener? = null
    private var connected = false
    private var pending: Destination? = null

    private val eventListener: NaviHelperEventListener = Proxy.newProxyInstance(
        NaviHelperEventListener::class.java.classLoader,
        arrayOf<Class<*>>(NaviHelperEventListener::class.java),
        InvocationHandler { proxy, method, args ->
            when (method.name) {
                // Object의 기본 메서드
                "toString" -> "MyDrawingMapNaviListener"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.getOrNull(0)
                else -> {
                    onNaviEvent(method.name, args)
                    // 콜백은 값을 돌려주지 않는다. 혹시 기본형을 돌려주는 메서드가 있으면 기본값을 준다.
                    when (method.returnType) {
                        java.lang.Boolean.TYPE -> false
                        java.lang.Integer.TYPE -> 0
                        java.lang.Long.TYPE -> 0L
                        java.lang.Float.TYPE -> 0f
                        java.lang.Double.TYPE -> 0.0
                        else -> null
                    }
                }
            }
        }
    ) as NaviHelperEventListener

    private fun onNaviEvent(name: String, args: Array<out Any?>?) {
        Log.d(TAG, "event $name ${args?.joinToString() ?: ""}")
        // 콜백이 어느 스레드에서 오는지 문서에 없으므로 화면 스레드로 옮겨 처리한다.
        main.post {
            when (name) {
                "onRouteStarted", "onRoutingCompleted" -> listener?.onRouteReady()
                "onReRouteEnded" -> listener?.onRerouted()
                "onDestinationArrived" -> listener?.onArrived()
                "onError" -> listener?.onNaviError("순정 내비 오류: " + (args?.getOrNull(0)?.toString() ?: "알 수 없음"))
                // onTripInfo(남은 거리·시간), onTBTInfo 등은 로그만 남긴다. 2단계에서 화면에 쓴다.
            }
        }
    }

    override fun start(listener: NaviBridge.Listener) {
        this.listener = listener
        try {
            val h = NaviHelper(appContext)
            helper = h
            h.initialize(
                onConnected = {
                    main.post {
                        connected = true
                        h.addListener(eventListener)
                        val waiting = pending
                        pending = null
                        if (waiting != null) requestRoute(waiting)
                    }
                },
                onFailed = { throwable ->
                    main.post { this.listener?.onNaviError("순정 내비에 연결하지 못했습니다: " + throwable.message) }
                }
            )
        } catch (e: Exception) {
            // Pleos Connect가 아닌 기기에 설치했을 때처럼 내비 서비스가 없는 경우
            Log.w(TAG, "NaviHelper 시작 실패", e)
            helper = null
            listener.onNaviError("순정 내비를 쓸 수 없는 환경입니다: " + e.message)
        }
    }

    override fun requestRoute(destination: Destination) {
        val h = helper
        if (h == null || !connected) {
            pending = destination // 연결이 끝나면 보낸다
            return
        }
        try {
            // 매개변수 이름은 Pleos 공식 NaviHelper 샘플 앱(NaviTestData.kt)이 쓰는 여섯 개와 같다.
            val info = RouteInfo(
                latitude = destination.position.lat,
                longitude = destination.position.lon,
                poiId = "",
                poiName = destination.name,
                poiSubId = "0",
                address = destination.address
            )
            // 아이가 탄 차이므로 편안한 경로를 요청한다. 스쿨존을 고려하려면 SCHOOL_ZONE.
            applyRouteOption(info, RouteOption.COMFORTABLE)
            h.requestRoute(info)
        } catch (e: Exception) {
            Log.w(TAG, "requestRoute 실패", e)
            listener?.onNaviError("목적지를 순정 내비에 보내지 못했습니다: " + e.message)
        }
    }

    /**
     * 경로 옵션을 넣는다. 문서에는 RouteInfo.routeOption으로 나오지만 생성자의 매개변수 이름을 공식 예제에서
     * 확인하지 못했다. 이름이 달라도 빌드가 깨지지 않도록 필드 이름으로 넣고, 필드가 없으면 기본 경로 옵션으로
     * 요청한다. Android Studio에서 매개변수를 확인한 뒤에는 RouteInfo(..., routeOption = ...)로 바꾼다.
     */
    private fun applyRouteOption(info: RouteInfo, option: RouteOption) {
        try {
            val field = info.javaClass.getDeclaredField("routeOption")
            field.isAccessible = true
            field.set(info, option)
        } catch (e: Exception) {
            Log.d(TAG, "RouteInfo에 routeOption 필드가 없어 기본 경로 옵션으로 요청합니다")
        }
    }

    override fun stop() {
        main.removeCallbacksAndMessages(null)
        val h = helper
        if (h != null) {
            try {
                h.removeListener(eventListener)
                h.release()
            } catch (e: Exception) {
                Log.w(TAG, "release 실패", e)
            }
        }
        helper = null
        listener = null
        connected = false
    }

    companion object {
        private const val TAG = "PleosNaviBridge"
    }
}
