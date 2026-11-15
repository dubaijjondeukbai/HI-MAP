package com.mydrawingmap.app.vehicle

import android.content.Context

/**
 * pleos 빌드의 연결부. Pleos Connect SDK 구현을 돌려준다.
 * 같은 이름의 파일이 src/demo 에도 있고, 빌드 종류에 따라 둘 중 하나만 들어간다.
 */
object VehicleFactory {
    /** 이 빌드가 Pleos SDK를 실제로 부르는지. */
    const val USES_PLEOS_SDK = true

    fun navi(context: Context): NaviBridge = PleosNaviBridge(context)

    fun location(context: Context): LocationBridge = PleosLocationBridge(context)

    fun speaker(context: Context): Speaker = PleosSpeaker(context)
}
