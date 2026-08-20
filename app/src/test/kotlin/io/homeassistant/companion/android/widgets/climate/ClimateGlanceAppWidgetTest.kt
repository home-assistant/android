package io.homeassistant.companion.android.widgets.climate

import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.isIndeterminateCircularProgressIndicator
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasClickAction
import androidx.glance.testing.unit.assertHasNoClickAction
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasTextEqualTo
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.HvacMode
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ClimateGlanceAppWidgetTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `Given LoadingState when ScreenForState then it displays CircularProgressIndicator`() = runGlanceAppWidgetUnitTest {
        setContext(context)

        provideComposable {
            ScreenForState(LoadingClimateState)
        }

        // Verify nothing else exists
        onNode(hasTestTag("Screen"))
            .assertDoesNotExist()
        onNode(hasTestTag("EmptyScreen"))
            .assertDoesNotExist()

        onNode(isIndeterminateCircularProgressIndicator())
            .assertHasNoClickAction()
            .assertExists()
    }

    @Test
    fun `Given EmptyState when ScreenForState then it displays EmptyScreen`() = runGlanceAppWidgetUnitTest {
        setContext(context)

        provideComposable {
            ScreenForState(EmptyClimateState)
        }

        // Verify nothing else exists
        onNode(hasTestTag("LoadingScreen"))
            .assertDoesNotExist()
        onNode(hasTestTag("Screen"))
            .assertDoesNotExist()

        onNode(hasTestTag("EmptyScreen"))
            .assertExists()

        onNode(hasTextEqualTo(context.getString(R.string.widget_no_configuration)))
    }

    @Test
    fun `Given ClimateState with empty items when ScreenForState then it displays the empty data`() = runGlanceAppWidgetUnitTest {
        setContext(context)

        val expectedTitle = "Samsung Climate HVAC"

        provideComposable {
            ScreenForState(
                ClimateStateWithData(
                    backgroundType = WidgetBackgroundType.DYNAMICCOLOR,
                    textColor = null,
                    serverId = 1,
                    listEntityId = "",
                    climateName = expectedTitle,
                    currentTemp = null,
                    climateTemp = null,
                    outOfSync = true,
                ),
            )
        }

        // Verify nothing else exists
        onNode(hasTestTag("EmptyScreen"))
            .assertDoesNotExist()
        onNode(hasTestTag("LoadingScreen"))
            .assertDoesNotExist()

        onNode(hasTestTag("Screen"))
            .assertExists()

        assertTitleBar(expectedTitle)

        onNode(hasTextEqualTo(context.getString(R.string.widget_climate_empty_temp)))
            .assertExists()
    }

    @Test
    fun `Given ClimateState with heat mode when ScreenForState then it displays climate screen`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        val expectedTitle = "Hello world HA"

        provideComposable {
            ScreenForState(
                ClimateStateWithData(
                    backgroundType = WidgetBackgroundType.DYNAMICCOLOR,
                    textColor = null,
                    serverId = 1,
                    listEntityId = "",
                    climateName = expectedTitle,
                    currentTemp = 22f,
                    climateTemp = 24f,
                    hvacSelectedMode = HvacMode.HEAT,
                    hvacSupportedModes = listOf(
                        HvacMode.OFF,
                        HvacMode.HEAT,
                        HvacMode.COOL,
                    ),
                    outOfSync = false,
                ),
            )
        }

        onNode(hasTestTag("EmptyScreen"))
            .assertDoesNotExist()

        onNode(hasTestTag("LoadingScreen"))
            .assertDoesNotExist()

        onNode(hasTestTag("Screen"))
            .assertExists()

        assertTitleBar(expectedTitle)

        onNode(hasTextEqualTo("24 °C"))
            .assertExists()

        onNode(hasTextEqualTo(context.getString(HvacMode.HEAT.toStringName())))
            .assertExists()

        onNode(hasTextEqualTo(context.getString(HvacMode.COOL.toStringName())))
            .assertExists()
    }

    @Test
    fun `Given ClimateState with off mode when ScreenForState then temperature is not displayed`() = runGlanceAppWidgetUnitTest {
        setContext(context)

        provideComposable {
            ScreenForState(
                ClimateStateWithData(
                    backgroundType = WidgetBackgroundType.DYNAMICCOLOR,
                    textColor = null,
                    serverId = 1,
                    listEntityId = "",
                    climateName = "Air conditioner",
                    currentTemp = 22f,
                    climateTemp = 24f,
                    hvacSelectedMode = HvacMode.OFF,
                    hvacSupportedModes = listOf(
                        HvacMode.OFF,
                        HvacMode.HEAT,
                        HvacMode.COOL,
                    ),
                    outOfSync = false,
                ),
            )
        }

        onNode(hasTestTag("Screen"))
            .assertExists()

        onNode(hasTextEqualTo("24 °C"))
            .assertDoesNotExist()

        onNode(hasTextEqualTo(context.getString(HvacMode.OFF.toStringName())))
            .assertExists()
    }

    @Test
    fun `Given ClimateState with fan mode when ScreenForState then temperature is not displayed`() = runGlanceAppWidgetUnitTest {
        setContext(context)

        provideComposable {
            ScreenForState(
                ClimateStateWithData(
                    backgroundType = WidgetBackgroundType.DYNAMICCOLOR,
                    textColor = null,
                    serverId = 1,
                    listEntityId = "",
                    climateName = "Air conditioner",
                    currentTemp = 22f,
                    climateTemp = 24f,
                    hvacSelectedMode = HvacMode.FAN,
                    hvacSupportedModes = listOf(
                        HvacMode.OFF,
                        HvacMode.COOL,
                        HvacMode.FAN,
                    ),
                    outOfSync = false,
                ),
            )
        }

        onNode(hasTestTag("Screen"))
            .assertExists()

        onNode(hasTextEqualTo("24 °C"))
            .assertDoesNotExist()
    }

    private fun GlanceAppWidgetUnitTest.assertTitleBar(title: String) {
        onNode(hasTextEqualTo(title))
            .assertExists()

        onNode(hasContentDescriptionEqualTo(context.getString(R.string.widget_climate_refresh)))
            .assertExists()
        onNode(hasTestTag("Refresh"))
            .assertExists()
            .assertHasClickAction()
            .assertExists()
        onNode(hasTestTag("Add"))
            .assertExists()
            .assertHasClickAction()

        onNode(hasTestTag("Substract"))
            .assertExists()
            .assertHasClickAction()

        // TODO I don't know how to assert the imageProvider or event if it possible
    }
}
