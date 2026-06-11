package io.homeassistant.companion.android.widgets.common

import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class WidgetUtilsTest {
    private val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()

    @Test
    fun `Given dynamic color background option when getting widget background type then returns dynamic color`() {
        assertWidgetBackgroundType(
            context.getString(R.string.widget_background_type_dynamiccolor),
            WidgetBackgroundType.DYNAMICCOLOR,
        )
    }

    @Test
    fun `Given transparent background option when getting widget background type then returns transparent`() {
        assertWidgetBackgroundType(
            context.getString(R.string.widget_background_type_transparent),
            WidgetBackgroundType.TRANSPARENT,
        )
    }

    @Test
    fun `Given day night background option when getting widget background type then returns day night`() {
        assertWidgetBackgroundType(
            context.getString(R.string.widget_background_type_daynight),
            WidgetBackgroundType.DAYNIGHT,
        )
    }

    @Test
    fun `Given unknown background option when getting widget background type then returns day night`() {
        assertWidgetBackgroundType("unknown", WidgetBackgroundType.DAYNIGHT)
    }

    private fun assertWidgetBackgroundType(
        selectedOption: String,
        expectedType: WidgetBackgroundType,
    ) {
        val result = WidgetUtils.getWidgetBackgroundType(context, selectedOption)

        assertEquals(expectedType, result)
    }
}
