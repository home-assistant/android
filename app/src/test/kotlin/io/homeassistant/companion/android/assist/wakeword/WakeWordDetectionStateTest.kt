package io.homeassistant.companion.android.assist.wakeword

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class WakeWordDetectionStateTest {

    @Nested
    inner class SlidingWindowTests {

        @Test
        fun `Given empty window when adding probabilities then window fills up`() {
            val state = WakeWordDetectionState(
                slidingWindowSize = 3,
                probabilityCutoff = 0.5f,
            )

            assertFalse(state.isWindowFull())

            state.addProbabilityAndCheckDetection(0.1f)
            assertFalse(state.isWindowFull())

            state.addProbabilityAndCheckDetection(0.2f)
            assertFalse(state.isWindowFull())

            state.addProbabilityAndCheckDetection(0.3f)
            assertTrue(state.isWindowFull())
        }

        @Test
        fun `Given window with values when getting average then returns correct average`() {
            val state = WakeWordDetectionState(
                slidingWindowSize = 3,
                probabilityCutoff = 0.9f, // High threshold to avoid triggering detection
            )

            state.addProbabilityAndCheckDetection(0.3f)
            state.addProbabilityAndCheckDetection(0.6f)
            state.addProbabilityAndCheckDetection(0.9f)

            assertEquals(0.6f, state.getAverageProbability(), 0.001f)
        }

        @Test
        fun `Given full window when adding more values then oldest values are replaced`() {
            val state = WakeWordDetectionState(
                slidingWindowSize = 3,
                probabilityCutoff = 0.9f,
            )

            // Fill window with low values
            state.addProbabilityAndCheckDetection(0.1f)
            state.addProbabilityAndCheckDetection(0.1f)
            state.addProbabilityAndCheckDetection(0.1f)
            assertEquals(0.1f, state.getAverageProbability(), 0.001f)

            // Replace with high values one by one
            state.addProbabilityAndCheckDetection(0.5f)
            // Window: [0.5, 0.1, 0.1] -> avg = 0.233
            assertEquals(0.233f, state.getAverageProbability(), 0.01f)

            state.addProbabilityAndCheckDetection(0.5f)
            // Window: [0.5, 0.5, 0.1] -> avg = 0.366
            assertEquals(0.366f, state.getAverageProbability(), 0.01f)

            state.addProbabilityAndCheckDetection(0.5f)
            // Window: [0.5, 0.5, 0.5] -> avg = 0.5
            assertEquals(0.5f, state.getAverageProbability(), 0.001f)
        }
    }

    @Nested
    inner class DetectionTests {

        @Test
        fun `Given probabilities below threshold when checking detection then returns false`() {
            val state = WakeWordDetectionState(
                slidingWindowSize = 3,
                probabilityCutoff = 0.5f,
            )

            assertFalse(state.addProbabilityAndCheckDetection(0.1f))
            assertFalse(state.addProbabilityAndCheckDetection(0.2f))
            assertFalse(state.addProbabilityAndCheckDetection(0.3f))
        }

        @Test
        fun `Given probabilities at threshold when checking detection then returns true`() {
            val state = WakeWordDetectionState(
                slidingWindowSize = 3,
                probabilityCutoff = 0.5f,
            )

            assertFalse(state.addProbabilityAndCheckDetection(0.5f))
            assertFalse(state.addProbabilityAndCheckDetection(0.5f))
            assertTrue(state.addProbabilityAndCheckDetection(0.5f))
        }

        @Test
        fun `Given probabilities above threshold when checking detection then returns true`() {
            val state = WakeWordDetectionState(
                slidingWindowSize = 3,
                probabilityCutoff = 0.5f,
            )

            assertFalse(state.addProbabilityAndCheckDetection(0.8f))
            assertFalse(state.addProbabilityAndCheckDetection(0.8f))
            assertTrue(state.addProbabilityAndCheckDetection(0.8f))
        }

        @Test
        fun `Given window not full when probabilities high then does not detect`() {
            val state = WakeWordDetectionState(
                slidingWindowSize = 5,
                probabilityCutoff = 0.5f,
            )

            // Even with high probabilities, need full window
            assertFalse(state.addProbabilityAndCheckDetection(1.0f))
            assertFalse(state.addProbabilityAndCheckDetection(1.0f))
            assertFalse(state.addProbabilityAndCheckDetection(1.0f))
            assertFalse(state.addProbabilityAndCheckDetection(1.0f))
            // Fifth value fills window and triggers detection
            assertTrue(state.addProbabilityAndCheckDetection(1.0f))
        }
    }

    @Nested
    inner class CooldownTests {

        @Test
        fun `Given not in cooldown when checking then returns false`() {
            val state = WakeWordDetectionState(
                slidingWindowSize = 3,
                probabilityCutoff = 0.5f,
            )

            assertFalse(state.isInCooldown())
        }

        @Test
        fun `Given detection triggered when checking cooldown then is in cooldown`() {
            val state = WakeWordDetectionState(
                slidingWindowSize = 3,
                probabilityCutoff = 0.5f,
            )

            // Trigger detection
            state.addProbabilityAndCheckDetection(1.0f)
            state.addProbabilityAndCheckDetection(1.0f)
            assertTrue(state.addProbabilityAndCheckDetection(1.0f))

            // Should be in cooldown now
            assertTrue(state.isInCooldown())
        }

        @Test
        fun `Given in cooldown when cooldown expires then no longer in cooldown`() {
            val state = WakeWordDetectionState(
                slidingWindowSize = 2,
                probabilityCutoff = 0.5f,
            )

            // Trigger detection (cooldown = slidingWindowSize * 2 = 4)
            state.addProbabilityAndCheckDetection(1.0f)
            state.addProbabilityAndCheckDetection(1.0f)

            // Cooldown should last 4 frames
            assertTrue(state.isInCooldown()) // 3 remaining
            assertTrue(state.isInCooldown()) // 2 remaining
            assertTrue(state.isInCooldown()) // 1 remaining
            assertTrue(state.isInCooldown()) // 0 remaining
            assertFalse(state.isInCooldown()) // expired
        }
    }

    @Nested
    inner class ResetTests {

        @Test
        fun `Given state with values when reset then state is cleared`() {
            val state = WakeWordDetectionState(
                slidingWindowSize = 3,
                probabilityCutoff = 0.5f,
            )

            state.addProbabilityAndCheckDetection(0.5f)
            state.addProbabilityAndCheckDetection(0.5f)
            assertTrue(state.addProbabilityAndCheckDetection(0.5f))

            state.reset()

            assertFalse(state.isWindowFull())
            assertEquals(0f, state.getAverageProbability())
        }

        @Test
        fun `Given detection triggered when state auto-resets then window is empty`() {
            val state = WakeWordDetectionState(
                slidingWindowSize = 3,
                probabilityCutoff = 0.5f,
            )

            // Trigger detection (which auto-resets)
            state.addProbabilityAndCheckDetection(1.0f)
            state.addProbabilityAndCheckDetection(1.0f)
            assertTrue(state.addProbabilityAndCheckDetection(1.0f))

            // Window should be reset
            assertFalse(state.isWindowFull())
        }
    }

    @Nested
    inner class EdgeCaseTests {

        @Test
        fun `Given window size of 1 when probability above threshold then detects immediately`() {
            val state = WakeWordDetectionState(
                slidingWindowSize = 1,
                probabilityCutoff = 0.5f,
            )

            assertTrue(state.addProbabilityAndCheckDetection(0.6f))
        }

        @Test
        fun `Given empty window when getting average then returns zero`() {
            val state = WakeWordDetectionState(
                slidingWindowSize = 3,
                probabilityCutoff = 0.5f,
            )

            assertEquals(0f, state.getAverageProbability())
        }

        @Test
        fun `Given partial window when getting average then returns partial average`() {
            val state = WakeWordDetectionState(
                slidingWindowSize = 5,
                probabilityCutoff = 0.5f,
            )

            state.addProbabilityAndCheckDetection(0.4f)
            state.addProbabilityAndCheckDetection(0.6f)

            // Average of 2 values: (0.4 + 0.6) / 2 = 0.5
            assertEquals(0.5f, state.getAverageProbability(), 0.001f)
        }
    }
}
