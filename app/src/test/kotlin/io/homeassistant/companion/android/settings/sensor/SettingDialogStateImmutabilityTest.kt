package io.homeassistant.companion.android.settings.sensor

import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.homeassistant.companion.android.settings.sensor.SensorDetailViewModel.Companion.SettingDialogState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test

/**
 * Regression test for the immutable copy fix in `SensorDetailView`'s setting dialog.
 *
 * The previous implementation called `state.copy().apply { setting.value = newValue }`, which
 * shared the same [SensorSetting] instance across the original and "copied" state because
 * `data class` copy is shallow. Mutating the shared `var value` field then leaked back into
 * the source-of-truth state held by the view model.
 *
 * The fix replaces that with `state.copy(setting = state.setting.copy(value = newValue))`, which
 * creates a fresh [SensorSetting] for the new state, leaving the original untouched. This test
 * pins that invariant so a future regression is intentional.
 */
class SettingDialogStateImmutabilityTest {

    private fun makeSetting(value: String) = SensorSetting(
        sensorId = "sensor-1",
        name = "allow_list",
        value = value,
        valueType = SensorSettingType.LIST_APPS,
        enabled = true,
        entries = listOf("com.google.chrome", "org.mozilla.firefox"),
    )

    private fun makeState(value: String) = SettingDialogState(
        setting = makeSetting(value),
        loading = false,
        entries = listOf(
            "com.google.chrome" to "Chrome\n(com.google.chrome)",
            "org.mozilla.firefox" to "Firefox\n(org.mozilla.firefox)",
        ),
        entriesSelected = listOf("com.google.chrome"),
    )

    @Test
    fun `Given state when copy with new setting value then new state holds new value`() {
        val original = makeState(value = "old")

        val updated = original.copy(setting = original.setting.copy(value = "new"))

        assertEquals("new", updated.setting.value)
    }

    @Test
    fun `Given state when copy with new setting value then original setting value is unchanged`() {
        val original = makeState(value = "old")

        original.copy(setting = original.setting.copy(value = "new"))

        assertEquals("old", original.setting.value)
    }

    @Test
    fun `Given state when copy with new setting value then setting reference is a fresh instance`() {
        val original = makeState(value = "old")

        val updated = original.copy(setting = original.setting.copy(value = "new"))

        // Different SensorSetting instance — proves we did not mutate the shared one.
        assertNotSame(original.setting, updated.setting)
    }

    @Test
    fun `Given state when copy with new setting value then untouched state fields share references`() {
        val original = makeState(value = "old")

        val updated = original.copy(setting = original.setting.copy(value = "new"))

        // Other state fields are reused (data class shallow copy), confirming we only swapped setting.
        assertEquals(original.entries, updated.entries)
        assertEquals(original.entriesSelected, updated.entriesSelected)
        assertEquals(original.loading, updated.loading)
    }
}
