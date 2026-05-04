package io.homeassistant.companion.android.frontend.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.frontend.externalbus.incoming.HapticType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [Build.VERSION_CODES.R])
class HapticFeedbackPerformerTest {

    private val vibrator: Vibrator = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true) {
        every { getSystemService(Vibrator::class.java) } returns vibrator
    }
    private val view: View = mockk(relaxed = true) {
        every { this@mockk.context } returns this@HapticFeedbackPerformerTest.context
    }

    @Test
    fun `Given success type on API 30 when perform then calls CONFIRM`() {
        every { view.performHapticFeedback(any()) } returns true

        HapticFeedbackPerformer.perform(view, HapticType.Success)

        verify { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM) }
    }

    @Test
    fun `Given failure type on API 30 when perform then calls REJECT`() {
        every { view.performHapticFeedback(any()) } returns true

        HapticFeedbackPerformer.perform(view, HapticType.Failure)

        verify { view.performHapticFeedback(HapticFeedbackConstants.REJECT) }
    }

    @Test
    fun `Given light type when perform then calls KEYBOARD_TAP`() {
        every { view.performHapticFeedback(any()) } returns true

        HapticFeedbackPerformer.perform(view, HapticType.Light)

        verify { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    }

    @Test
    fun `Given medium type when perform then calls VIRTUAL_KEY`() {
        every { view.performHapticFeedback(any()) } returns true

        HapticFeedbackPerformer.perform(view, HapticType.Medium)

        verify { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    }

    @Test
    fun `Given heavy type when perform then calls LONG_PRESS`() {
        every { view.performHapticFeedback(any()) } returns true

        HapticFeedbackPerformer.perform(view, HapticType.Heavy)

        verify { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
    }

    @Test
    fun `Given selection type on API 30 when perform then calls GESTURE_START`() {
        every { view.performHapticFeedback(any()) } returns true

        HapticFeedbackPerformer.perform(view, HapticType.Selection)

        verify { view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START) }
    }

    @Test
    fun `Given warning type on API 30 when perform then vibrates with EFFECT_HEAVY_CLICK`() {
        val effectSlot = slot<VibrationEffect>()

        HapticFeedbackPerformer.perform(view, HapticType.Warning)

        verify(exactly = 0) { view.performHapticFeedback(any()) }
        verify { vibrator.vibrate(capture(effectSlot)) }
        assertEquals(
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK),
            effectSlot.captured,
        )
    }
}
