package com.mydrawingmap.app.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/** 차량용 안드로이드(AAOS)에서 도는지 확인한다. 차량용 클래스를 건드리지 않는 별도 객체로 둔다. */
object CarSupport {
    fun available(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 29 && context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
}

/**
 * 차량의 주행 제한 상태(CarUxRestrictions)를 읽는다.
 * 달리는 중이면 차가 "주의 분산 최적화가 필요하다"고 알려 주고, 앱은 그때 꾸미기를 잠근다.
 * Pleos 주행 제한 개발 가이드가 안내하는 방식이다. [CarSupport.available]이 참일 때만 만든다.
 */
class CarUxDrivingState(private val context: Context) : DrivingState {
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    override var isDriving: Boolean = false
        private set

    override fun start(onChanged: (Boolean) -> Unit) {
        try {
            val c = Car.createCar(context) ?: return
            car = c
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: return
            manager = m
            isDriving = m.currentCarUxRestrictions?.isRequiresDistractionOptimization == true
            m.registerListener { restrictions ->
                val now = restrictions.isRequiresDistractionOptimization
                if (now != isDriving) {
                    isDriving = now
                    onChanged(now)
                }
            }
            if (isDriving) onChanged(true)
        } catch (e: Throwable) {
            Log.w(TAG, "주행 제한 상태를 읽지 못했습니다", e)
        }
    }

    override fun stop() {
        try {
            manager?.unregisterListener()
        } catch (e: Throwable) {
            Log.w(TAG, "리스너 해제 실패", e)
        }
        try {
            car?.disconnect()
        } catch (e: Throwable) {
            Log.w(TAG, "차량 연결 해제 실패", e)
        }
        manager = null
        car = null
    }

    companion object {
        private const val TAG = "CarUxDrivingState"
    }
}
