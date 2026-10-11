package io.homeassistant.companion.android.widgets.mediaplayer

import android.content.Intent
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetDao
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetEntity
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class MediaPlayerControlsWidgetTest {

    private val dao = mockk<MediaPlayerControlsWidgetDao>()
    private val serverManager = mockk<ServerManager>()
    private val integrationRepository = mockk<IntegrationRepository>()
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
        runPlaybackActionTest(state = "playing", expectedAction = "media_pause")
    }

    @Test
    fun `Given non-playing entity when play pause widget action is received then play is called`() = runTest {
        runPlaybackActionTest(state = "paused", expectedAction = "media_play")
    }

    private suspend fun runPlaybackActionTest(
        state: String,
        expectedAction: String,
    ) {
        val widget = MediaPlayerControlsWidgetEntity(
            id = appWidgetId,
            serverId = serverId,
            entityId = entityId,
            label = null,
            showSkip = false,
            showSeek = false,
            showVolume = false,
            showSource = false,
        )
        val entity = Entity(
            entityId = entityId,
            state = state,
            attributes = emptyMap(),
            lastChanged = LocalDateTime.MIN,
            lastUpdated = LocalDateTime.MIN,
        )
        val receiver = MediaPlayerControlsWidget().also {
            it.dao = dao
            it.serverManager = serverManager
        }

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
}
