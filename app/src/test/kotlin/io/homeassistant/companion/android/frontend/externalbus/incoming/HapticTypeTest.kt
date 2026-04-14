package io.homeassistant.companion.android.frontend.externalbus.incoming

import io.homeassistant.companion.android.frontend.externalbus.frontendExternalBusJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class HapticTypeTest {

    @ParameterizedTest
    @EnumSource(HapticTypeTestCase::class)
    fun `Given known haptic string when deserialize then returns correct HapticType`(case: HapticTypeTestCase) {
        val json = """{"hapticType":"${case.input}"}"""
        val result = frontendExternalBusJson.decodeFromString<HapticType>(json)
        assertEquals(case.expected, result)
    }

    @Test
    fun `Given unknown haptic string when deserialize then returns Unknown`() {
        val json = """{"hapticType":"future_type"}"""
        val result = frontendExternalBusJson.decodeFromString<HapticType>(json)
        assertEquals(HapticType.Unknown, result)
    }

    @Test
    fun `Given empty haptic string when deserialize then returns Unknown`() {
        val json = """{"hapticType":""}"""
        val result = frontendExternalBusJson.decodeFromString<HapticType>(json)
        assertEquals(HapticType.Unknown, result)
    }
}

@Suppress("unused")
enum class HapticTypeTestCase(val input: String, val expected: HapticType) {
    SUCCESS("success", HapticType.Success),
    WARNING("warning", HapticType.Warning),
    FAILURE("failure", HapticType.Failure),
    LIGHT("light", HapticType.Light),
    MEDIUM("medium", HapticType.Medium),
    HEAVY("heavy", HapticType.Heavy),
    SELECTION("selection", HapticType.Selection),
}
