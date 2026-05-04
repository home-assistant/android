package io.homeassistant.companion.android.frontend.navigation

import android.net.Uri
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.TestSharedFlow
import junit.framework.TestCase.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class FrontendEventHandlerTest {

    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given NavigateToSettings event then onNavigateToSettings is called`() {
        var settingsNavigated = false
        var deepLink: SettingsActivity.Deeplink? = null
        val events = TestSharedFlow<FrontendEvent>()

        composeTestRule.setContent {
            FrontendEventHandler(
                events = events,
                onShowSnackbar = { _, _ -> false },
                onNavigateToSettings = {
                    settingsNavigated = true
                    deepLink = it
                },
                onNavigateToAssist = { _, _, _ -> },
                onOpenExternalLink = {},
                onShowServerSwitcher = {},
                onNavigateToNfcWrite = { _, _ -> },
            )
        }

        composeTestRule.waitForIdle()
        events.emit(FrontendEvent.NavigateToSettings)
        composeTestRule.waitForIdle()

        assertEquals(true, settingsNavigated)
        assertNull(deepLink)
    }

    @Test
    fun `Given NavigateToAssistSettings event then onNavigateToSettings is called with deeplink`() {
        var settingsNavigated = false
        var deepLink: SettingsActivity.Deeplink? = null
        val events = TestSharedFlow<FrontendEvent>()

        composeTestRule.setContent {
            FrontendEventHandler(
                events = events,
                onShowSnackbar = { _, _ -> false },
                onNavigateToSettings = {
                    settingsNavigated = true
                    deepLink = it
                },
                onNavigateToAssist = { _, _, _ -> },
                onOpenExternalLink = {},
                onShowServerSwitcher = {},
                onNavigateToNfcWrite = { _, _ -> },
            )
        }

        composeTestRule.waitForIdle()
        events.emit(FrontendEvent.NavigateToAssistSettings)
        composeTestRule.waitForIdle()

        assertEquals(true, settingsNavigated)
        assertEquals(SettingsActivity.Deeplink.AssistSettings, deepLink)
    }

    @Test
    fun `Given NavigateToAssist event then onNavigateToAssist is called with correct params`() {
        var capturedServerId: Int? = null
        var capturedPipelineId: String? = null
        var capturedStartListening: Boolean? = null
        val events = TestSharedFlow<FrontendEvent>()

        composeTestRule.setContent {
            FrontendEventHandler(
                events = events,
                onShowSnackbar = { _, _ -> false },
                onNavigateToSettings = { },
                onNavigateToAssist = { serverId, pipelineId, startListening ->
                    capturedServerId = serverId
                    capturedPipelineId = pipelineId
                    capturedStartListening = startListening
                },
                onOpenExternalLink = {},
                onShowServerSwitcher = {},
                onNavigateToNfcWrite = { _, _ -> },
            )
        }

        composeTestRule.waitForIdle()
        events.emit(
            FrontendEvent.NavigateToAssist(
                serverId = 1,
                pipelineId = "abc",
                startListening = false,
            ),
        )
        composeTestRule.waitForIdle()

        assertEquals(1, capturedServerId)
        assertEquals("abc", capturedPipelineId)
        assertEquals(false, capturedStartListening)
    }

    @Test
    fun `Given ShowSnackbar event then onShowSnackbar is called with resolved message`() {
        var capturedMessage: String? = null
        var capturedAction: String? = null
        val events = TestSharedFlow<FrontendEvent>()

        composeTestRule.setContent {
            FrontendEventHandler(
                events = events,
                onShowSnackbar = { message, action ->
                    capturedMessage = message
                    capturedAction = action
                    false
                },
                onNavigateToSettings = {},
                onNavigateToAssist = { _, _, _ -> },
                onOpenExternalLink = {},
                onShowServerSwitcher = {},
                onNavigateToNfcWrite = { _, _ -> },
            )
        }

        composeTestRule.waitForIdle()
        events.emit(FrontendEvent.ShowSnackbar(messageResId = android.R.string.ok))
        composeTestRule.waitForIdle()

        assertEquals("OK", capturedMessage)
        assertNull(capturedAction)
    }

    @Test
    fun `Given OpenExternalLink event then onOpenExternalLink is called with the URI`() {
        var capturedUri: Uri? = null
        val events = TestSharedFlow<FrontendEvent>()

        composeTestRule.setContent {
            FrontendEventHandler(
                events = events,
                onShowSnackbar = { _, _ -> false },
                onNavigateToSettings = {},
                onNavigateToAssist = { _, _, _ -> },
                onOpenExternalLink = { uri -> capturedUri = uri },
                onShowServerSwitcher = {},
                onNavigateToNfcWrite = { _, _ -> },
            )
        }

        val testUri = Uri.parse("https://example.com")
        composeTestRule.waitForIdle()
        events.emit(FrontendEvent.OpenExternalLink(uri = testUri))
        composeTestRule.waitForIdle()

        assertEquals(testUri, capturedUri)
    }

    @Test
    fun `Given ShowServerSwitcher event then onShowServerSwitcher is called`() {
        var serverSwitcherShown = false
        val events = TestSharedFlow<FrontendEvent>()

        composeTestRule.setContent {
            FrontendEventHandler(
                events = events,
                onShowSnackbar = { _, _ -> false },
                onNavigateToSettings = {},
                onNavigateToAssist = { _, _, _ -> },
                onOpenExternalLink = {},
                onShowServerSwitcher = { serverSwitcherShown = true },
                onNavigateToNfcWrite = { _, _ -> },
            )
        }

        composeTestRule.waitForIdle()
        events.emit(FrontendEvent.ShowServerSwitcher)
        composeTestRule.waitForIdle()

        assertEquals(true, serverSwitcherShown)
    }

    @Test
    fun `Given NavigateToDeveloperSettings event then onNavigateToSettings is called with Developer deeplink`() {
        var settingsNavigated = false
        var deepLink: SettingsActivity.Deeplink? = null
        val navigationEvents = TestSharedFlow<FrontendEvent>()

        composeTestRule.setContent {
            FrontendEventHandler(
                events = navigationEvents,
                onShowSnackbar = { _, _ -> false },
                onNavigateToSettings = {
                    settingsNavigated = true
                    deepLink = it
                },
                onNavigateToAssist = { _, _, _ -> },
                onOpenExternalLink = {},
                onShowServerSwitcher = {},
                onNavigateToNfcWrite = { _, _ -> },
            )
        }

        composeTestRule.waitForIdle()
        navigationEvents.emit(FrontendEvent.NavigateToDeveloperSettings)
        composeTestRule.waitForIdle()

        assertEquals(true, settingsNavigated)
        assertEquals(SettingsActivity.Deeplink.Developer, deepLink)
    }

    @Test
    fun `Given NavigateToNfcWrite event then onNavigateToNfcWrite is called with messageId and tagId`() {
        var capturedMessageId: Int? = null
        var capturedTagId: String? = "not-captured"
        val events = TestSharedFlow<FrontendEvent>()

        composeTestRule.setContent {
            FrontendEventHandler(
                events = events,
                onShowSnackbar = { _, _ -> false },
                onNavigateToSettings = {},
                onNavigateToAssist = { _, _, _ -> },
                onOpenExternalLink = {},
                onShowServerSwitcher = {},
                onNavigateToNfcWrite = { messageId, tagId ->
                    capturedMessageId = messageId
                    capturedTagId = tagId
                },
            )
        }

        composeTestRule.waitForIdle()
        events.emit(FrontendEvent.NavigateToNfcWrite(messageId = 42, tagId = "tag-abc"))
        composeTestRule.waitForIdle()

        assertEquals(42, capturedMessageId)
        assertEquals("tag-abc", capturedTagId)
    }

    @Test
    fun `Given NavigateToNfcWrite event without tagId then onNavigateToNfcWrite is called with null tagId`() {
        var capturedMessageId: Int? = null
        var capturedTagId: String? = "not-captured"
        val events = TestSharedFlow<FrontendEvent>()

        composeTestRule.setContent {
            FrontendEventHandler(
                events = events,
                onShowSnackbar = { _, _ -> false },
                onNavigateToSettings = {},
                onNavigateToAssist = { _, _, _ -> },
                onOpenExternalLink = {},
                onShowServerSwitcher = {},
                onNavigateToNfcWrite = { messageId, tagId ->
                    capturedMessageId = messageId
                    capturedTagId = tagId
                },
            )
        }

        composeTestRule.waitForIdle()
        events.emit(FrontendEvent.NavigateToNfcWrite(messageId = 7, tagId = null))
        composeTestRule.waitForIdle()

        assertEquals(7, capturedMessageId)
        assertEquals(null, capturedTagId)
    }
}
