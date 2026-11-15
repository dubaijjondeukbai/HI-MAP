package com.mydrawingmap.app.vehicle

import android.content.Context

/**
 * demo 빌드의 연결부. Pleos SDK를 쓰지 않는다.
 * 같은 이름의 파일이 src/pleos 에도 있고, 빌드 종류에 따라 둘 중 하나만 들어간다.
 */
object VehicleFactory {
    /** 이 빌드가 Pleos SDK를 실제로 부르는지. */
    const val USES_PLEOS_SDK = false

    fun navi(context: Context): NaviBridge = DemoNaviBridge()

    fun location(context: Context): LocationBridge = ReplayLocationBridge()

    fun speaker(context: Context): Speaker = AndroidSpeaker(context)
}
