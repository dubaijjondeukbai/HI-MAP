package com.mydrawingmap.app.vehicle

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import java.util.Locale

/**
 * 음성 예고 (Pleos TextToSpeech SDK 3.3.1).
 *
 * ON_DEVICE 모드로 차 안에서 합성한다. 네트워크와 앱 등록이 필요 없고, 문장이 차 밖으로 나가지 않는다.
 * 쓰는 순서는 SDK 문서를 따른다: 객체 생성 → initialize() → setLocale() → speak() → stop() → release().
 *
 * 필요한 권한(AndroidManifest): pleos.car.permission.TTS_SERVICE
 */
class PleosSpeaker(context: Context) : Speaker {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false

    override fun start() {
        try {
            val t = TextToSpeech(appContext, Mode.ON_DEVICE)
            tts = t
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    ready = true
                    t.setLocale(Locale.KOREA)
                }

                override fun onFailure(errorType: ErrorType) {
                    Log.w(TAG, "음성 합성 초기화 실패: $errorType")
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "음성 합성 시작 실패", e)
        }
    }

    override fun speak(text: String) {
        if (!ready) return
        try {
            tts?.speak(text)
        } catch (e: Exception) {
            Log.w(TAG, "speak 실패", e)
        }
    }

    override fun stop() {
        try {
            tts?.stop()
            tts?.release()
        } catch (e: Exception) {
            Log.w(TAG, "release 실패", e)
        }
        tts = null
        ready = false
    }

    companion object {
        private const val TAG = "PleosSpeaker"
    }
}
