package io.homeassistant.companion.android.widgets.camera

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.CameraWidgetDao
import io.homeassistant.companion.android.database.widget.CameraWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import io.homeassistant.companion.android.widgets.EntitiesPerServer
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config
class CameraWidgetReceiverTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val appWidgetManager = mockk<AppWidgetManager>()
    private val glanceManager = mockk<GlanceAppWidgetManager>()
    private val appWidget = mockk<GlanceAppWidget>()
    private val stateUpdater = mockk<CameraWidgetStateUpdater>(relaxUnitFun = true)
    private val dao = mockk<CameraWidgetDao>()
    private val serverManager = mockk<ServerManager>()

    @After
    fun tearDown() = unmockkAll()

    private fun TestScope.receiver(): CameraWidget = CameraWidget(
        widgetScopeProvider = { this },
        glanceManagerProvider = { glanceManager },
        cameraAppWidgetManagerProvider = { appWidgetManager },
        glanceAppWidget = appWidget,
    ).apply {
        this.dao = this@CameraWidgetReceiverTest.dao
        this.serverManager = this@CameraWidgetReceiverTest.serverManager
        this.stateUpdater = this@CameraWidgetReceiverTest.stateUpdater
    }

    @Test
    fun `manual refresh routes only the requested installed widget to Glance`() = runTest {
        val receiver = receiver()
        every { glanceManager.getGlanceIdBy(WIDGET_ID) } returns mockk(relaxed = true)
        coJustRun { appWidget.update(context, any()) }

        receiver.handleCameraWidgetIntent(
            context,
            Intent(CameraWidget.UPDATE_IMAGE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, WIDGET_ID),
        )
        advanceUntilIdle()

        verify { stateUpdater.refreshAll(intArrayOf(WIDGET_ID), force = true) }
        coVerify(exactly = 1) { appWidget.update(context, any()) }
    }

    @Test
    fun `camera entities are excluded from realtime image subscriptions`() = runTest {
        val receiver = receiver()
        coEvery { dao.getAll() } returns listOf(
            CameraWidgetEntity(10, SERVER_ID, IMAGE_A, WidgetTapAction.REFRESH),
            CameraWidgetEntity(11, SERVER_ID, CAMERA_ID, WidgetTapAction.REFRESH),
        )

        assertEquals(
            mapOf(10 to EntitiesPerServer(SERVER_ID, listOf(IMAGE_A))),
            receiver.getWidgetEntitiesByServer(context),
        )
    }

    @Test
    fun `receiver entity update forwards state to the composition signal`() = runTest {
        val receiver = receiver()
        val entity = Entity(IMAGE_A, IMAGE_STATE, emptyMap(), java.time.LocalDateTime.MIN, java.time.LocalDateTime.MIN)

        receiver.onEntityUpdate(context, WIDGET_ID, entity)

        verify { stateUpdater.refreshFromEntity(WIDGET_ID, IMAGE_STATE) }
    }

    @Test
    fun `entry point lookup unwraps receiver restricted context to application`() {
        val applicationContext = mockk<Context>()
        val receiverContext = mockk<Context> {
            every { this@mockk.applicationContext } returns applicationContext
        }
        val expectedUpdater = mockk<CameraWidgetStateUpdater>()
        val entryPoint = mockk<CameraGlanceAppWidget.EntryPointContract> {
            every { cameraWidgetStateUpdater() } returns expectedUpdater
        }
        var requestedContext: Context? = null

        assertEquals(
            expectedUpdater,
            cameraWidgetStateUpdaterForContext(receiverContext) {
                requestedContext = it
                entryPoint
            },
        )
        assertEquals(applicationContext, requestedContext)
    }

    @Test
    fun `configuration update refreshes and schedules all currently installed widgets`() = runTest {
        val receiver = receiver()
        val widgetIds = intArrayOf(WIDGET_ID, WIDGET_ID + 1)
        every { appWidgetManager.getAppWidgetIds(ComponentName(context, CameraWidget::class.java)) } returns widgetIds
        coEvery { glanceManager.getGlanceIds(CameraGlanceAppWidget::class.java) } returns emptyList()
        mockkStatic("androidx.glance.appwidget.GlanceAppWidgetKt")
        coJustRun { appWidget.updateAll(context) }

        receiver.handleCameraWidgetIntent(context, Intent(BaseWidgetProvider.UPDATE_WIDGETS))
        advanceUntilIdle()

        verify { stateUpdater.refreshAll(widgetIds, force = true) }
        coVerify(exactly = 1) { appWidget.updateAll(context) }
    }

    @Test
    fun `screen on reconciles image widgets only while periodic work retains all widget ids`() = runTest {
        val receiver = receiver()
        val cameraWidgetId = WIDGET_ID + 1
        val orphanedImageWidgetId = WIDGET_ID + 2
        coEvery { dao.getAll() } returns listOf(
            CameraWidgetEntity(WIDGET_ID, SERVER_ID, IMAGE_A, WidgetTapAction.REFRESH),
            CameraWidgetEntity(cameraWidgetId, SERVER_ID, CAMERA_ID, WidgetTapAction.REFRESH),
            CameraWidgetEntity(orphanedImageWidgetId, SERVER_ID, IMAGE_A, WidgetTapAction.REFRESH),
        )
        every {
            appWidgetManager.getAppWidgetIds(ComponentName(context, CameraWidget::class.java))
        } returns intArrayOf(WIDGET_ID, cameraWidgetId)
        every { glanceManager.getGlanceIdBy(WIDGET_ID) } returns mockk(relaxed = true)
        coJustRun { appWidget.update(context, any()) }

        receiver.handleCameraWidgetIntent(context, Intent(Intent.ACTION_SCREEN_ON))
        advanceUntilIdle()

        verify { stateUpdater.refreshAll(intArrayOf(WIDGET_ID), force = false) }
        coVerify(exactly = 1) { appWidget.update(context, any()) }
        verify(exactly = 0) { glanceManager.getGlanceIdBy(cameraWidgetId) }
        verify(exactly = 0) { glanceManager.getGlanceIdBy(orphanedImageWidgetId) }
    }

    @Test
    fun `periodic update reconciles but does not force a camera image refresh signal`() = runTest {
        val receiver = receiver()
        val widgetIds = intArrayOf(WIDGET_ID, WIDGET_ID + 1)
        every { appWidgetManager.getAppWidgetIds(ComponentName(context, CameraWidget::class.java)) } returns widgetIds
        coEvery { serverManager.isRegistered() } returns false
        mockkStatic("androidx.glance.appwidget.GlanceAppWidgetKt")
        coJustRun { appWidget.updateAll(context) }

        receiver.handleCameraWidgetIntent(context, Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE))
        advanceUntilIdle()

        verify { stateUpdater.refreshAll(widgetIds, force = false) }
    }

    private companion object {
        const val SERVER_ID = 1
        const val WIDGET_ID = 47
        const val IMAGE_A = "image.front_snapshot"
        const val CAMERA_ID = "camera.front_door"
        const val IMAGE_STATE = "2026-11-26T10:00:00+00:00"
    }
}
