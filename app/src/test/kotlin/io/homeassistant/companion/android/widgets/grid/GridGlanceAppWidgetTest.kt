package io.homeassistant.companion.android.widgets.grid

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.isIndeterminateCircularProgressIndicator
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasTextEqualTo
import io.homeassistant.companion.android.common.R as commonR
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GridGlanceAppWidgetTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `Given LoadingState when GridWidgetContent then it displays CircularProgressIndicator`() = runGlanceAppWidgetUnitTest {
        setContext(context)

        provideComposable {
            GridWidgetContent(LoadingGridState)
        }

        onNode(hasTestTag("LoadingScreen"))
            .assertExists()

        onNode(isIndeterminateCircularProgressIndicator())
            .assertExists()
    }

    @Test
    fun `Given GridStateWithData when GridWidgetContent then it displays items`() = runGlanceAppWidgetUnitTest {
        setContext(context)

        val items = listOf(
            GridButtonData("1", "Light", "mdi:lightbulb", "On", true),
            GridButtonData("2", "Switch", "mdi:toggle-switch", "Off", false),
        )
        val state = GridStateWithData(label = "My Grid", items = items)

        provideComposable {
            GridWidgetContent(state)
        }

        onNode(hasTextEqualTo("Light")).assertExists()
        onNode(hasTextEqualTo("On")).assertExists()

        onNode(hasTextEqualTo("Switch")).assertExists()
        onNode(hasTextEqualTo("Off")).assertExists()
    }

    @Test
    fun `Given GridStateWithData with label when GridWidgetContent then it displays title bar if size permits`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(400.dp, 400.dp))

        val state = GridStateWithData(label = "My Grid", items = emptyList())

        provideComposable {
            GridWidgetContent(state)
        }

        onNode(hasContentDescriptionEqualTo(context.getString(commonR.string.refresh)))
            .assertExists()
    }

    @Test
    fun `Given GridStateWithData in small width when GridWidgetContent then it does not display title bar`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(100.dp, 400.dp)) // Small width

        val state = GridStateWithData(label = "My Grid", items = emptyList())

        provideComposable {
            GridWidgetContent(state)
        }

        onNode(hasContentDescriptionEqualTo(context.getString(commonR.string.refresh)))
            .assertDoesNotExist()
    }

    @Test
    fun `Given GridStateWithData in normal width but small height when GridWidgetContent then it does not display title bar`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        // Normal width (>180dp), Small height (<180dp)
        setAppWidgetSize(DpSize(200.dp, 100.dp))

        val state = GridStateWithData(label = "My Grid", items = emptyList())

        provideComposable {
            GridWidgetContent(state)
        }

        onNode(hasContentDescriptionEqualTo(context.getString(commonR.string.refresh)))
            .assertDoesNotExist()
    }

    @Test
    fun `Given GridStateWithData in compact mode then items do not show labels`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(100.dp, 400.dp)) // Small width -> Compact mode

        val items = listOf(
            GridButtonData("1", "Light", "mdi:lightbulb", "On", true),
        )
        val state = GridStateWithData(label = "My Grid", items = items)

        provideComposable {
            GridWidgetContent(state)
        }

        // In compact mode, text is not shown
        onNode(hasTextEqualTo("Light")).assertDoesNotExist()
        onNode(hasTextEqualTo("On")).assertDoesNotExist()
    }
}
