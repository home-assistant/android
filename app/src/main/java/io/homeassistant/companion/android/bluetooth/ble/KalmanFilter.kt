package io.homeassistant.companion.android.bluetooth.ble

import org.altbeacon.beacon.service.RssiFilter

const val MAX_ITERATIONS = 10

class KalmanFilter: RssiFilter {
    private var predictedValue: Double = 0.0
    private var numIterations: Int = 0

    override fun addMeasurement(rssi: Int) {
        if (numIterations == 0) {
            predictedValue = rssi.toDouble()
        }
        if (numIterations < MAX_ITERATIONS) {
            numIterations++
        }
        val delta: Double = rssi.toDouble() - predictedValue
        val gain: Double = 1.0 / (kotlin.math.abs(rssi.toDouble()) + numIterations)
        predictedValue += gain * delta
    }

    override fun noMeasurementsAvailable(): Boolean {
        return numIterations < MAX_ITERATIONS
    }

    override fun calculateRssi(): Double {
        return predictedValue
    }

    override fun getMeasurementCount(): Int {
        return numIterations
    }
}