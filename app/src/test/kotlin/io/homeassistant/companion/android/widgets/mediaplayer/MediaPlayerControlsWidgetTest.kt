package io.homeassistant.companion.android.widgets.mediaplayer

import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.EntityExt.MEDIA_PLAYER_STATE_BUFFERING
import io.homeassistant.companion.android.common.data.integration.EntityExt.MEDIA_PLAYER_SUPPORT_PAUSE
import io.homeassistant.companion.android.common.data.integration.EntityExt.MEDIA_PLAYER_SUPPORT_PLAY
import io.homeassistant.companion.android.common.data.integration.EntityExt.MEDIA_PLAYER_SUPPORT_PREVIOUS_TRACK
import io.homeassistant.companion.android.common.data.integration.EntityExt.MEDIA_PLAYER_SUPPORT_SEEK
import io.homeassistant.companion.android.common.data.integration.EntityExt.MEDIA_PLAYER_SUPPORT_STOP
import io.homeassistant.companion.android.common.data.integration.EntityExt.MEDIA_PLAYER_SUPPORT_VOLUME_SET
import io.homeassistant.companion.android.common.data.integration.EntityExt.MEDIA_PLAYER_SUPPORT_VOLUME_STEP
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
    fun `Given buffering entity with pause support when play pause widget action is received then pause is called`() = runTest {
        runPlaybackActionTest(
            state = MEDIA_PLAYER_STATE_BUFFERING,
            supportedFeatures = MEDIA_PLAYER_SUPPORT_PAUSE,
            expectedAction = "media_pause",
        )
    }

    @Test
    fun `Given non-playing entity when play pause widget action is received then play is called`() = runTest {
        runPlaybackActionTest(
            state = "paused",
            supportedFeatures = MEDIA_PLAYER_SUPPORT_PLAY,
            expectedAction = "media_play",
        )
    }

    @Test
    fun `Given playing stop-only entity when rendering widget then stop icon is shown`() = runTest {
        runPlaybackIconTest(
            state = "playing",
            supportedFeatures = MEDIA_PLAYER_SUPPORT_STOP,
            expectedIcon = R.drawable.ic_stop,
            expectedContentDescription = commonR.string.widget_media_stop_button,
        )
    }

    @Test
    fun `Given playing pause-capable entity when rendering widget then pause icon is shown`() = runTest {
        runPlaybackIconTest(
            state = "playing",
            supportedFeatures = MEDIA_PLAYER_SUPPORT_PAUSE,
            expectedIcon = R.drawable.ic_pause,
            expectedContentDescription = commonR.string.widget_media_pause_button,
        )
    }

    @Test
    fun `Given buffering entity with stop support when rendering widget then stop icon is shown`() = runTest {
        runPlaybackIconTest(
            state = MEDIA_PLAYER_STATE_BUFFERING,
            supportedFeatures = MEDIA_PLAYER_SUPPORT_STOP,
            expectedIcon = R.drawable.ic_stop,
            expectedContentDescription = commonR.string.widget_media_stop_button,
        )
    }

    @Test
    fun `Given playing entity without playback actions when rendering widget then playback button is hidden`() = runTest {
        runPlaybackIconTest(
            state = "playing",
            supportedFeatures = 0,
            expectedVisibility = View.GONE,
        )
    }

    @Test
    fun `Given non-playing entity when rendering widget then play icon is shown`() = runTest {
        runPlaybackIconTest(
            state = "paused",
            supportedFeatures = MEDIA_PLAYER_SUPPORT_PLAY,
            expectedIcon = R.drawable.ic_play,
            expectedContentDescription = commonR.string.widget_media_play_button,
        )
    }

    @Test
    fun `Given configured controls when rendering widget then only supported controls are shown`() = runTest {
        val widget = createWidget(showSkip = true, showSeek = true, showVolume = true)
        val entity = createEntity(
            state = "paused",
            supportedFeatures = MEDIA_PLAYER_SUPPORT_PLAY or
                MEDIA_PLAYER_SUPPORT_PREVIOUS_TRACK or
                MEDIA_PLAYER_SUPPORT_SEEK or
                MEDIA_PLAYER_SUPPORT_VOLUME_SET,
        )
        val views = renderWidget(widget, entity)
        val root = views.apply(context, FrameLayout(context))

        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widgetVolumeDownButton).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widgetVolumeUpButton).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widgetPrevTrackButton).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.widgetNextTrackButton).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widgetRewindButton).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widgetFastForwardButton).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widgetPlayPauseButton).visibility)
    }

    @Test
    fun `Given step volume support when rendering widget then volume controls are shown`() = runTest {
        val widget = createWidget(showSkip = false, showSeek = false, showVolume = true)
        val entity = createEntity(
            state = "paused",
            supportedFeatures = MEDIA_PLAYER_SUPPORT_PLAY or MEDIA_PLAYER_SUPPORT_VOLUME_STEP,
        )
        val root = renderWidget(widget, entity).apply(context, FrameLayout(context))

        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widgetVolumeDownButton).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widgetVolumeUpButton).visibility)
    }

    @Test
    fun `Given configured controls without optional capabilities when rendering widget then optional controls are hidden`() = runTest {
        val widget = createWidget(showSkip = true, showSeek = true, showVolume = true)
        val entity = createEntity(
            state = "paused",
            supportedFeatures = MEDIA_PLAYER_SUPPORT_PLAY,
        )
        val root = renderWidget(widget, entity).apply(context, FrameLayout(context))

        assertEquals(View.GONE, root.findViewById<View>(R.id.widgetVolumeDownButton).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.widgetVolumeUpButton).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.widgetPrevTrackButton).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.widgetNextTrackButton).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.widgetRewindButton).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.widgetFastForwardButton).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widgetPlayPauseButton).visibility)
    }

    private suspend fun runPlaybackIconTest(
        state: String,
        supportedFeatures: Int? = null,
        expectedVisibility: Int = View.VISIBLE,
        expectedIcon: Int? = null,
        expectedContentDescription: Int? = null,
    ) {
        val widget = createWidget()
        val entity = createEntity(state, supportedFeatures)
        val root = renderWidget(widget, entity).apply(context, FrameLayout(context))
        val playPauseButton = root.findViewById<ImageView>(R.id.widgetPlayPauseButton)

        assertEquals(expectedVisibility, playPauseButton.visibility)
        if (expectedVisibility == View.VISIBLE) {
            assertEquals(expectedIcon, shadowOf(playPauseButton.drawable).createdFromResId)
            assertEquals(context.getString(expectedContentDescription!!), playPauseButton.contentDescription)
        }
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

    private fun createWidget(
        showSkip: Boolean,
        showSeek: Boolean,
        showVolume: Boolean,
    ) = MediaPlayerControlsWidgetEntity(
        id = appWidgetId,
        serverId = serverId,
        entityId = entityId,
        label = null,
        showSkip = showSkip,
        showSeek = showSeek,
        showVolume = showVolume,
        showSource = false,
    )

    private suspend fun renderWidget(
        widget: MediaPlayerControlsWidgetEntity,
        entity: Entity,
    ) = MediaPlayerControlsWidget().also {
        it.dao = dao
        it.serverManager = serverManager
        coEvery { dao.get(appWidgetId) } returns widget
        coEvery { serverManager.connectionStateProvider(serverId) } returns connectionStateProvider
        every { connectionStateProvider.urlFlow() } returns flowOf(UrlState.HasUrl(null))
    }.getWidgetRemoteViews(context, appWidgetId, entity)

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
