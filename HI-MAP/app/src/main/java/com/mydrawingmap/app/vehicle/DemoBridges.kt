package com.mydrawingmap.app.vehicle

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.mydrawingmap.app.core.LatLon
import com.mydrawingmap.app.core.Route
import com.mydrawingmap.app.data.Destination
import java.util.Locale
import kotlin.math.min

/*
 * 시연용 구현. 차량 SDK 없이도 앱의 전체 흐름을 돌려 볼 수 있게 한다.
 * 에뮬레이터는 실제로 달리지 않으므로, 2차 PT 시연에서도 이 재생 기능을 쓴다.
 */

/** 시연용 내비. 목적지를 받으면 잠시 뒤 길이 정해졌다고 알린다. */
class DemoNaviBridge : NaviBridge {
    private val handler = Handler(Looper.getMainLooper())
    private var listener: NaviBridge.Listener? = null

    override fun start(listener: NaviBridge.Listener) {
        this.listener = listener
    }

    override fun requestRoute(destination: Destination) {
        handler.postDelayed({ listener?.onRouteReady() }, 300L)
    }

    override fun stop() {
        handler.removeCallbacksAndMessages(null)
        listener = null
    }
}

/** 시연용 위치. 정해 준 길을 따라 일정한 속도로 움직이는 위치를 0.5초마다 내보낸다. */
class ReplayLocationBridge : LocationBridge {
    private val handler = Handler(Looper.getMainLooper())
    private var onLocation: ((LatLon) -> Unit)? = null
    private var route: Route? = null
    private var s = 0.0
    private var running = false

    /** 재생 속도(m/s). 시연이 지루하지 않게 실제 주행보다 빠르게 잡았다. */
    var speedMps = 28.0

    private val tick = object : Runnable {
        override fun run() {
            step()
            if (running) handler.postDelayed(this, TICK_MS)
        }
    }

    /** 따라갈 길을 정한다. 처음부터 다시 달린다. */
    fun setPath(points: List<LatLon>) {
        route = if (points.size >= 2) Route(points) else null
        s = 0.0
    }

    override fun start(onLocation: (LatLon) -> Unit) {
        this.onLocation = onLocation
        if (!running) {
            running = true
            handler.postDelayed(tick, TICK_MS)
        }
    }

    override fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    private fun step() {
        val r = route ?: return
        s = min(r.total, s + speedMps * TICK_MS / 1000.0)
        onLocation?.invoke(r.pointAt(s))
    }

    companion object {
        private const val TICK_MS = 500L
    }
}

/** 시연용 주행 상태. 화면의 출발·정차 단추가 바꾼다. */
class ManualDrivingState : DrivingState {
    private var callback: ((Boolean) -> Unit)? = null

    override var isDriving: Boolean = false
        private set

    override fun start(onChanged: (Boolean) -> Unit) {
        callback = onChanged
    }

    override fun stop() {
        callback = null
    }

    fun set(driving: Boolean) {
        if (isDriving == driving) return
        isDriving = driving
        callback?.invoke(driving)
    }
}

/** 안드로이드 기본 음성 합성. Pleos SDK가 없는 demo 빌드에서 쓴다. */
class AndroidSpeaker(context: Context) : Speaker {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false

    override fun start() {
        tts = TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setLanguage(Locale.KOREAN)
                tts?.setSpeechRate(0.95f)
            }
        }
    }

    override fun speak(text: String) {
        if (ready) tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "say-" + System.nanoTime())
    }

    override fun stop() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
