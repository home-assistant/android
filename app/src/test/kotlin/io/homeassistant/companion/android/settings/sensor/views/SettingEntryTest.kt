package io.homeassistant.companion.android.settings.sensor.views

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class SettingEntryTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("labels")
    fun `Given label when creating entry then primary and secondary match expectations`(
        @Suppress("UNUSED_PARAMETER") description: String,
        label: String,
        expectedPrimary: String,
        expectedSecondary: String?,
    ) {
        val entry = SettingEntry(id = "id", label = label)

        assertEquals(expectedPrimary, entry.primary)
        assertEquals(expectedSecondary, entry.secondary)
    }

    companion object {
        @JvmStatic
        fun labels() = listOf(
            Arguments.of("single-line label has null secondary", "Chrome", "Chrome", null),
            Arguments.of(
                "two-line label with parens strips them from secondary",
                "Chrome\n(com.google.chrome)",
                "Chrome",
                "com.google.chrome",
            ),
            Arguments.of(
                "multiple newlines keep extra lines inside secondary",
                "Chrome\nline1\nline2",
                "Chrome",
                "line1\nline2",
            ),
            Arguments.of("second line without parens kept verbatim", "Foo\nbar", "Foo", "bar"),
            Arguments.of("mismatched leading paren is preserved", "Foo\n(bar", "Foo", "(bar"),
            Arguments.of("empty label yields empty primary and null secondary", "", "", null),
            Arguments.of("empty second line yields empty secondary", "Foo\n", "Foo", ""),
        )
    }
}
