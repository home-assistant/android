package io.homeassistant.companion.android.common.bluetooth.ble

import kotlin.math.pow
import org.altbeacon.beacon.service.RssiFilter

class KalmanFilter : RssiFilter {
    companion object {
        var maxIterations = 10
        var rssiMultiplier: Double = 1.05
    }

    private var predictedValue: Double = 0.0
    private var numIterations: Int = 0

    override fun addMeasurement(rssi: Int) {
        if (numIterations == 0) {
            predictedValue = rssi.toDouble()
        }
        if (numIterations < maxIterations) {
            numIterations++
        } else {
            numIterations = maxIterations
        }

        val delta: Double = rssi.toDouble() - predictedValue
        val gain: Double = 1.0 / (rssiMultiplier.pow(kotlin.math.abs(rssi.toDouble())) + numIterations)
        predictedValue += gain * delta
    }

    override fun noMeasurementsAvailable(): Boolean {
        return numIterations == 0
    }

    override fun calculateRssi(): Double {
        return predictedValue
    }

    override fun getMeasurementCount(): Int {
        return numIterations
    }
}
