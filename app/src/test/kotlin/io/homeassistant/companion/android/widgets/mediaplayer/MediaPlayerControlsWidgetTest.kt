package io.homeassistant.companion.android.widgets.mediaplayer

import android.content.Intent
import android.widget.FrameLayout
import android.widget.ImageView
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.EntityExt.MEDIA_PLAYER_SUPPORT_PAUSE
import io.homeassistant.companion.android.common.data.integration.EntityExt.MEDIA_PLAYER_SUPPORT_STOP
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerConnectionStateProvider
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetDao
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetEntity
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import java.time.LocalDateTime
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class MediaPlayerControlsWidgetTest {

    private val dao = mockk<MediaPlayerControlsWidgetDao>()
    private val serverManager = mockk<ServerManager>()
    private val integrationRepository = mockk<IntegrationRepository>()
    private val connectionStateProvider = mockk<ServerConnectionStateProvider>()
    private val context = RuntimeEnvironment.getApplication()
    private val appWidgetId = 1
    private val serverId = 1
    private val entityId = "media_player.test"

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Given playing entity when play pause widget action is received then pause is called`() = runTest {
        runPlaybackActionTest(
            state = "playing",
            supportedFeatures = MEDIA_PLAYER_SUPPORT_PAUSE,
            expectedAction = "media_pause",
        )
    }

    @Test
    fun `Given playing entity without pause support when play pause widget action is received then stop is called`() = runTest {
        runPlaybackActionTest(
            state = "playing",
            supportedFeatures = MEDIA_PLAYER_SUPPORT_STOP,
            expectedAction = "media_stop",
        )
    }

    @Test
    fun `Given non-playing entity when play pause widget action is received then play is called`() = runTest {
        runPlaybackActionTest(state = "paused", expectedAction = "media_play")
    }

    @Test
    fun `Given playing stop-only entity when rendering widget then stop icon is shown`() = runTest {
        runPlaybackIconTest(
            state = "playing",
            supportedFeatures = MEDIA_PLAYER_SUPPORT_STOP,
            expectedIcon = R.drawable.ic_stop,
        )
    }

    @Test
    fun `Given playing pause-capable entity when rendering widget then pause icon is shown`() = runTest {
        runPlaybackIconTest(
            state = "playing",
            supportedFeatures = MEDIA_PLAYER_SUPPORT_PAUSE,
            expectedIcon = R.drawable.ic_pause,
        )
    }

    @Test
    fun `Given non-playing entity when rendering widget then play icon is shown`() = runTest {
        runPlaybackIconTest(
            state = "paused",
            expectedIcon = R.drawable.ic_play,
        )
    }

    private suspend fun runPlaybackIconTest(
        state: String,
        supportedFeatures: Int? = null,
        expectedIcon: Int,
    ) {
        val widget = createWidget()
        val entity = createEntity(state, supportedFeatures)
        val receiver = createReceiver()
        val parent = FrameLayout(context)

        coEvery { dao.get(appWidgetId) } returns widget
        coEvery { serverManager.connectionStateProvider(serverId) } returns connectionStateProvider
        every { connectionStateProvider.urlFlow() } returns flowOf(UrlState.HasUrl(null))

        val views = receiver.getWidgetRemoteViews(context, appWidgetId, entity)
        val playPauseButton = views.apply(context, parent).findViewById<ImageView>(R.id.widgetPlayPauseButton)

        assertEquals(expectedIcon, shadowOf(playPauseButton.drawable).createdFromResId)
    }

    private suspend fun runPlaybackActionTest(
        state: String,
        supportedFeatures: Int? = null,
        expectedAction: String,
    ) {
        val widget = createWidget()
        val entity = createEntity(state, supportedFeatures)
        val receiver = createReceiver()

        coEvery { dao.get(appWidgetId) } returns widget
        coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository
        coEvery { integrationRepository.getEntity(entityId) } returns entity
        coJustRun {
            integrationRepository.callAction(
                MEDIA_PLAYER_DOMAIN,
                expectedAction,
                mapOf("entity_id" to entityId),
            )
        }

        receiver.onReceiveIntentNotHandled(
            context,
            Intent(MediaPlayerControlsWidget.CALL_PLAYPAUSE),
            appWidgetId,
        )

        coVerify {
            integrationRepository.callAction(
                MEDIA_PLAYER_DOMAIN,
                expectedAction,
                mapOf("entity_id" to entityId),
            )
        }
    }

    private fun createWidget() = MediaPlayerControlsWidgetEntity(
        id = appWidgetId,
        serverId = serverId,
        entityId = entityId,
        label = null,
        showSkip = false,
        showSeek = false,
        showVolume = false,
        showSource = false,
    )

    private fun createEntity(state: String, supportedFeatures: Int? = null) = Entity(
        entityId = entityId,
        state = state,
        attributes = supportedFeatures?.let { mapOf("supported_features" to it) } ?: emptyMap(),
        lastChanged = LocalDateTime.MIN,
        lastUpdated = LocalDateTime.MIN,
    )

    private fun createReceiver() = MediaPlayerControlsWidget().also {
        it.dao = dao
        it.serverManager = serverManager
    }
}
