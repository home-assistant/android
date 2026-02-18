package io.homeassistant.companion.android.assist.wakeword

/**
 * Manages the state for wake word detection including probability averaging,
 * threshold checking, and cooldown logic.
 *
 * This class is extracted from MicroWakeWord to enable unit testing of the
 * detection logic without requiring TFLite or native code dependencies.
 *
 * @param slidingWindowSize Number of probability samples to average for detection
 * @param probabilityCutoff Threshold for average probability to trigger detection
 */
internal class WakeWordDetectionState(private val slidingWindowSize: Int, private val probabilityCutoff: Float) {
    private val probabilities = FloatArray(slidingWindowSize)
    private var probabilityIndex = 0
    private var probabilityCount = 0

    private var cooldownFrames = 0
    private val cooldownDuration = slidingWindowSize * 2

    /**
     * Returns true if currently in cooldown period after a detection.
     * Decrements the cooldown counter if active.
     */
    fun isInCooldown(): Boolean {
        if (cooldownFrames > 0) {
            cooldownFrames--
            return true
        }
        return false
    }

    /**
     * Add a probability value to the sliding window and check for detection.
     *
     * @param probability The probability from model inference (0.0 to 1.0)
     * @return true if wake word is detected (average probability >= cutoff)
     */
    fun addProbabilityAndCheckDetection(probability: Float): Boolean {
        probabilities[probabilityIndex] = probability
        probabilityIndex = (probabilityIndex + 1) % slidingWindowSize
        if (probabilityCount < slidingWindowSize) {
            probabilityCount++
        }

        if (probabilityCount >= slidingWindowSize) {
            val avgProbability = probabilities.sum() / slidingWindowSize

            if (avgProbability >= probabilityCutoff) {
                reset()
                cooldownFrames = cooldownDuration
                return true
            }
        }

        return false
    }

    /**
     * Get the current average probability across the sliding window.
     * Returns 0 if the window is not yet full.
     */
    fun getAverageProbability(): Float {
        if (probabilityCount == 0) return 0f
        return probabilities.sum() / probabilityCount
    }

    /**
     * Reset all detection state.
     */
    fun reset() {
        probabilities.fill(0f)
        probabilityIndex = 0
        probabilityCount = 0
    }

    /**
     * Returns true if the sliding window has enough samples for detection.
     */
    fun isWindowFull(): Boolean = probabilityCount >= slidingWindowSize
}
