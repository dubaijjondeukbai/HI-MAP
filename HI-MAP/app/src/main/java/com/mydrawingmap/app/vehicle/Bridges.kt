package com.mydrawingmap.app.vehicle

import com.mydrawingmap.app.core.LatLon
import com.mydrawingmap.app.data.Destination

/*
 * 차량 SDK와 맞닿는 부분을 인터페이스 네 개로 나눈다.
 * 화면 쪽 코드는 이 인터페이스만 알고, 실제 구현은 빌드 종류에 따라 바뀐다.
 *   - demo 빌드 : 시연용 구현(재생되는 위치, 안드로이드 기본 음성)
 *   - pleos 빌드: Pleos Connect SDK 구현(NaviHelper, FusedLocation, TextToSpeech)
 */

/** 순정 내비와의 연결. 목적지를 넘기고, 길이 정해졌는지·바뀌었는지·도착했는지를 받는다. */
interface NaviBridge {
    interface Listener {
        /** 순정 내비가 길 찾기를 시작했거나 마쳤다. */
        fun onRouteReady()

        /** 순정 내비가 길을 바꿨다. */
        fun onRerouted()

        /** 순정 내비가 도착을 알렸다. */
        fun onArrived()

        /** 연결이나 요청이 실패했다. */
        fun onNaviError(message: String)
    }

    fun start(listener: Listener)
    fun requestRoute(destination: Destination)
    fun stop()
}

/** 차 위치를 받아 오는 통로. */
interface LocationBridge {
    fun start(onLocation: (LatLon) -> Unit)
    fun stop()
}

/** 아이에게 문장을 읽어 주는 음성. */
interface Speaker {
    fun start()
    fun speak(text: String)
    fun stop()
}

/** 차가 달리는 중인지 알려 주는 통로. 달리는 중에는 꾸미기를 잠근다. */
interface DrivingState {
    val isDriving: Boolean
    fun start(onChanged: (Boolean) -> Unit)
    fun stop()
}
