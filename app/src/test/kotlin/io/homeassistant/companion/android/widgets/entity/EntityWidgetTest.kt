package io.homeassistant.companion.android.widgets.entity

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.StaticWidgetDao
import io.homeassistant.companion.android.database.widget.StaticWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class EntityWidgetTest {

    @get:Rule
    val consoleLogRule = ConsoleLogRule()

    private val dao = mockk<StaticWidgetDao>(relaxUnitFun = true)
    private val serverManager = mockk<ServerManager>()
    private val integrationRepository = mockk<IntegrationRepository>()
    private val context = RuntimeEnvironment.getApplication()

    private val appWidgetId = 1
    private val serverId = 1
    private val entityId = "sensor.power"

    private lateinit var widget: EntityWidget

    @Before
    fun setUp() {
        widget = EntityWidget().also {
            it.dao = dao
            it.serverManager = serverManager
        }
    }

    private fun createWidgetEntity(
        attributeIds: String? = null,
        lastUpdate: String = "",
        stateSeparator: String = " ",
        attributeSeparator: String = "",
    ) = StaticWidgetEntity(
        id = appWidgetId,
        serverId = serverId,
        entityId = entityId,
        attributeIds = attributeIds,
        label = null,
        textSize = 30F,
        stateSeparator = stateSeparator,
        attributeSeparator = attributeSeparator,
        tapAction = WidgetTapAction.REFRESH,
        lastUpdate = lastUpdate,
        backgroundType = WidgetBackgroundType.DAYNIGHT,
        textColor = null,
    )

    private fun createEntity(
        state: String,
        attributes: Map<String, Any?> = emptyMap(),
    ) = Entity(
        entityId = entityId,
        state = state,
        attributes = attributes,
        lastChanged = LocalDateTime.now(),
        lastUpdated = LocalDateTime.now(),
    )

    private fun resolveWidgetViews(views: android.widget.RemoteViews): Pair<TextView, View> {
        val parent = FrameLayout(context)
        val inflated = views.apply(context, parent)
        val text = inflated.findViewById<TextView>(R.id.widgetText)
        val errorIcon = inflated.findViewById<View>(R.id.widgetStaticError)
        return text to errorIcon
    }

    @Test
    fun `Given exception thrown and attributes configured and cached value when resolving then shows cached value with error icon`() = runTest {
        coEvery { dao.get(appWidgetId) } returns createWidgetEntity(attributeIds = "unit_of_measurement", lastUpdate = "5 W")
        coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository
        coEvery { integrationRepository.getEntity(entityId) } throws RuntimeException("Server unavailable")

        val (text, errorIcon) = resolveWidgetViews(widget.getWidgetRemoteViews(context, appWidgetId))

        assertEquals("5 W", text.text.toString())
        assertEquals(View.VISIBLE, errorIcon.visibility)
        coVerify(exactly = 0) { dao.updateWidgetLastUpdate(any(), any()) }
    }

    @Test
    fun `Given exception thrown and no attributes configured and cached value when resolving then shows cached value with error icon`() = runTest {
        coEvery { dao.get(appWidgetId) } returns createWidgetEntity(attributeIds = null, lastUpdate = "5")
        coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository
        coEvery { integrationRepository.getEntity(entityId) } throws RuntimeException("Server unavailable")

        val (text, errorIcon) = resolveWidgetViews(widget.getWidgetRemoteViews(context, appWidgetId))

        assertEquals("5", text.text.toString())
        assertEquals(View.VISIBLE, errorIcon.visibility)
        coVerify(exactly = 0) { dao.updateWidgetLastUpdate(any(), any()) }
    }

    @Test
    fun `Given entity available with attributes when resolving then shows state and attribute`() = runTest {
        val entity = createEntity(state = "5", attributes = mapOf("unit_of_measurement" to "W"))
        coEvery { dao.get(appWidgetId) } returns createWidgetEntity(attributeIds = "unit_of_measurement", lastUpdate = "")
        coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository
        coEvery { integrationRepository.getEntity(entityId) } returns entity
        coEvery { serverManager.getServer(serverId) } returns null

        val (text, errorIcon) = resolveWidgetViews(widget.getWidgetRemoteViews(context, appWidgetId))

        assertEquals("5 W", text.text.toString())
        assertEquals(View.GONE, errorIcon.visibility)
        coVerify { dao.updateWidgetLastUpdate(appWidgetId, "5 W") }
    }

    @Test
    fun `Given entity available but configured attribute absent from state when resolving then shows only state without null`() = runTest {
        val entity = createEntity(state = "5")
        coEvery { dao.get(appWidgetId) } returns createWidgetEntity(attributeIds = "unit_of_measurement", lastUpdate = "")
        coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository
        coEvery { integrationRepository.getEntity(entityId) } returns entity
        coEvery { serverManager.getServer(serverId) } returns null

        val (text, errorIcon) = resolveWidgetViews(widget.getWidgetRemoteViews(context, appWidgetId))

        assertEquals("5", text.text.toString())
        assertEquals(View.GONE, errorIcon.visibility)
        coVerify { dao.updateWidgetLastUpdate(appWidgetId, "5") }
    }

    @Test
    fun `Given entity returns null without exception and attribute configured and cached value when resolving then shows cached value without error icon`() = runTest {
        coEvery { dao.get(appWidgetId) } returns createWidgetEntity(lastUpdate = "5 W")
        coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository
        coEvery { integrationRepository.getEntity(entityId) } returns null

        val (text, errorIcon) = resolveWidgetViews(widget.getWidgetRemoteViews(context, appWidgetId))

        assertEquals("5 W", text.text.toString())
        assertEquals(View.GONE, errorIcon.visibility)
        coVerify(exactly = 0) { dao.updateWidgetLastUpdate(any(), any()) }
    }
}
