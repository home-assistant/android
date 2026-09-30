package io.homeassistant.companion.android.widgets

import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class WidgetTextColorTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `Given the white hex when converting from hex then WHITE is returned`() {
        assertEquals(WidgetTextColor.WHITE, WidgetTextColor.fromHex(context, WidgetTextColor.WHITE.resolve(context)))
    }

    @Test
    fun `Given the black hex when converting from hex then BLACK is returned`() {
        assertEquals(WidgetTextColor.BLACK, WidgetTextColor.fromHex(context, WidgetTextColor.BLACK.resolve(context)))
    }

    @Test
    fun `Given no hex in light mode when converting from hex then it falls back to BLACK`() {
        assertEquals(WidgetTextColor.BLACK, WidgetTextColor.fromHex(context, null))
    }

    @Test
    @Config(qualifiers = "+night")
    fun `Given no hex in dark mode when converting from hex then it falls back to WHITE`() {
        assertEquals(WidgetTextColor.WHITE, WidgetTextColor.fromHex(RuntimeEnvironment.getApplication(), null))
    }

    @Test
    fun `Given an unrecognized hex in light mode when converting from hex then it falls back to BLACK`() {
        assertEquals(WidgetTextColor.BLACK, WidgetTextColor.fromHex(context, "#123456"))
    }

    @Test
    @Config(qualifiers = "+night")
    fun `Given an unrecognized hex in dark mode when converting from hex then it falls back to WHITE`() {
        assertEquals(WidgetTextColor.WHITE, WidgetTextColor.fromHex(context, "#123456"))
    }
}
