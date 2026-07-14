package io.homeassistant.companion.android.frontend.navigation

import android.content.IntentSender
import android.net.Uri
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.testing.unit.TestSharedFlow
import io.mockk.mockk
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
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
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
                onLaunchMatterThreadIntent = {},
                onRequestFullscreen = {},
                onNavigateToWidgetConfig = { _, _ -> },
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
                onLaunchMatterThreadIntent = {},
                onRequestFullscreen = {},
                onNavigateToWidgetConfig = { _, _ -> },
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
                onLaunchMatterThreadIntent = {},
                onRequestFullscreen = {},
                onNavigateToWidgetConfig = { _, _ -> },
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
                onLaunchMatterThreadIntent = {},
                onRequestFullscreen = {},
                onNavigateToWidgetConfig = { _, _ -> },
            )
        }

        composeTestRule.waitForIdle()
        events.emit(FrontendEvent.ShowSnackbar(messageResId = android.R.string.ok))
        composeTestRule.waitForIdle()

        assertEquals("OK", capturedMessage)
        assertNull(capturedAction)
    }

    @Test
    fun `Given ShowSnackbar with action then onShowSnackbar receives the resolved action label`() {
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
                onLaunchMatterThreadIntent = {},
                onRequestFullscreen = {},
                onNavigateToWidgetConfig = { _, _ -> },
            )
        }

        composeTestRule.waitForIdle()
        events.emit(
            FrontendEvent.ShowSnackbar(
                messageResId = android.R.string.ok,
                action = FrontendEvent.ShowSnackbar.Action(
                    labelResId = android.R.string.cancel,
                    event = FrontendEvent.OpenExternalLink(Uri.parse("https://example.com/help")),
                ),
            ),
        )
        composeTestRule.waitForIdle()

        assertEquals("OK", capturedMessage)
        assertEquals("Cancel", capturedAction)
    }

    @Test
    fun `Given ShowSnackbar with action when action is not tapped then the action event is not dispatched`() {
        var openExternalLinkCalled = false
        val events = TestSharedFlow<FrontendEvent>()

        composeTestRule.setContent {
            FrontendEventHandler(
                events = events,
                // Returning false models a snackbar that was dismissed without tapping the action.
                onShowSnackbar = { _, _ -> false },
                onNavigateToSettings = {},
                onNavigateToAssist = { _, _, _ -> },
                onOpenExternalLink = { openExternalLinkCalled = true },
                onShowServerSwitcher = {},
                onNavigateToNfcWrite = { _, _ -> },
                onLaunchMatterThreadIntent = {},
                onRequestFullscreen = {},
                onNavigateToWidgetConfig = { _, _ -> },
            )
        }

        composeTestRule.waitForIdle()
        events.emit(
            FrontendEvent.ShowSnackbar(
                messageResId = android.R.string.ok,
                action = FrontendEvent.ShowSnackbar.Action(
                    labelResId = android.R.string.cancel,
                    event = FrontendEvent.OpenExternalLink(Uri.parse("https://example.com/help")),
                ),
            ),
        )
        composeTestRule.waitForIdle()

        assertEquals(false, openExternalLinkCalled)
    }

    @Test
    fun `Given ShowSnackbar with action when action is tapped then the action event is dispatched`() {
        var capturedUri: Uri? = null
        val helpUri = Uri.parse("https://example.com/help")
        val events = TestSharedFlow<FrontendEvent>()

        composeTestRule.setContent {
            FrontendEventHandler(
                events = events,
                // Returning true models the user tapping the snackbar action.
                onShowSnackbar = { _, _ -> true },
                onNavigateToSettings = {},
                onNavigateToAssist = { _, _, _ -> },
                onOpenExternalLink = { capturedUri = it },
                onShowServerSwitcher = {},
                onNavigateToNfcWrite = { _, _ -> },
                onLaunchMatterThreadIntent = {},
                onRequestFullscreen = {},
                onNavigateToWidgetConfig = { _, _ -> },
            )
        }

        composeTestRule.waitForIdle()
        events.emit(
            FrontendEvent.ShowSnackbar(
                messageResId = android.R.string.ok,
                action = FrontendEvent.ShowSnackbar.Action(
                    labelResId = android.R.string.cancel,
                    event = FrontendEvent.OpenExternalLink(helpUri),
                ),
            ),
        )
        composeTestRule.waitForIdle()

        assertEquals(helpUri, capturedUri)
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
                onLaunchMatterThreadIntent = {},
                onRequestFullscreen = {},
                onNavigateToWidgetConfig = { _, _ -> },
            )
        }

        val testUri = Uri.parse("https://example.com")
        composeTestRule.waitForIdle()
        events.emit(FrontendEvent.OpenExternalLink(uri = testUri))
        composeTestRule.waitForIdle()

        assertEquals(testUri, capturedUri)
    }

    @Test
    fun `Given LaunchApp event then onLaunchApp is called with the package name`() {
        var capturedPackageName: String? = null
        val events = TestSharedFlow<FrontendEvent>()

        composeTestRule.setContent {
            FrontendEventHandler(
                events = events,
                onShowSnackbar = { _, _ -> false },
                onNavigateToSettings = {},
                onNavigateToAssist = { _, _, _ -> },
                onOpenExternalLink = {},
                onShowServerSwitcher = {},
                onNavigateToNfcWrite = { _, _ -> },
                onRequestFullscreen = {},
                onLaunchMatterThreadIntent = {},
                onNavigateToWidgetConfig = { _, _ -> },
                onLaunchApp = { packageName -> capturedPackageName = packageName },
            )
        }

        composeTestRule.waitForIdle()
        events.emit(FrontendEvent.LaunchApp(packageName = "com.example.app"))
        composeTestRule.waitForIdle()

        assertEquals("com.example.app", capturedPackageName)
    }

    @Test
    fun `Given LaunchIntent event then onLaunchIntent is called with the intent uri`() {
        var capturedIntentUri: String? = null
        val events = TestSharedFlow<FrontendEvent>()

        composeTestRule.setContent {
            FrontendEventHandler(
                events = events,
                onShowSnackbar = { _, _ -> false },
                onNavigateToSettings = {},
                onNavigateToAssist = { _, _, _ -> },
                onOpenExternalLink = {},
                onShowServerSwitcher = {},
                onNavigateToNfcWrite = { _, _ -> },
                onLaunchMatterThreadIntent = {},
                onRequestFullscreen = {},
                onNavigateToWidgetConfig = { _, _ -> },
                onLaunchIntent = { intentUri -> capturedIntentUri = intentUri },
            )
        }

        composeTestRule.waitForIdle()
        events.emit(FrontendEvent.LaunchIntent(intentUri = "intent://scan#Intent;end"))
        composeTestRule.waitForIdle()

        assertEquals("intent://scan#Intent;end", capturedIntentUri)
    }

    @Test
    fun `Given OpenSecuritySettings event then onOpenSecuritySettings is called`() {
        var securitySettingsOpened = false
        val events = TestSharedFlow<FrontendEvent>()

        composeTestRule.setContent {
            FrontendEventHandler(
                events = events,
                onShowSnackbar = { _, _ -> false },
                onNavigateToSettings = {},
                onNavigateToAssist = { _, _, _ -> },
                onOpenExternalLink = {},
                onShowServerSwitcher = {},
                onNavigateToNfcWrite = { _, _ -> },
                onLaunchMatterThreadIntent = {},
                onRequestFullscreen = {},
                onNavigateToWidgetConfig = { _, _ -> },
                onOpenSecuritySettings = { securitySettingsOpened = true },
            )
        }

        composeTestRule.waitForIdle()
        events.emit(FrontendEvent.OpenSecuritySettings)
        composeTestRule.waitForIdle()

        assertEquals(true, securitySettingsOpened)
    }

    @Test
    fun `Given UpdateWebView event then onUpdateWebView is called`() {
        var webViewUpdated = false
        val events = TestSharedFlow<FrontendEvent>()

        composeTestRule.setContent {
            FrontendEventHandler(
                events = events,
                onShowSnackbar = { _, _ -> false },
                onNavigateToSettings = {},
                onNavigateToAssist = { _, _, _ -> },
                onOpenExternalLink = {},
                onShowServerSwitcher = {},
                onNavigateToNfcWrite = { _, _ -> },
                onLaunchMatterThreadIntent = {},
                onRequestFullscreen = {},
                onNavigateToWidgetConfig = { _, _ -> },
                onUpdateWebView = { webViewUpdated = true },
            )
        }

        composeTestRule.waitForIdle()
        events.emit(FrontendEvent.UpdateWebView)
        composeTestRule.waitForIdle()

        assertEquals(true, webViewUpdated)
    }

    @Test
    fun `Given Relaunch event then onRelaunch is called`() {
        var relaunched = false
        val events = TestSharedFlow<FrontendEvent>()

        composeTestRule.setContent {
            FrontendEventHandler(
                events = events,
                onShowSnackbar = { _, _ -> false },
                onNavigateToSettings = {},
                onNavigateToAssist = { _, _, _ -> },
                onOpenExternalLink = {},
                onShowServerSwitcher = {},
                onNavigateToNfcWrite = { _, _ -> },
                onLaunchMatterThreadIntent = {},
                onRequestFullscreen = {},
                onNavigateToWidgetConfig = { _, _ -> },
                onRelaunch = { relaunched = true },
            )
        }

        composeTestRule.waitForIdle()
        events.emit(FrontendEvent.Relaunch)
        composeTestRule.waitForIdle()

        assertEquals(true, relaunched)
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
                onLaunchMatterThreadIntent = {},
                onRequestFullscreen = {},
                onNavigateToWidgetConfig = { _, _ -> },
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
                onLaunchMatterThreadIntent = {},
                onRequestFullscreen = {},
                onNavigateToWidgetConfig = { _, _ -> },
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
                onLaunchMatterThreadIntent = {},
                onRequestFullscreen = {},
                onNavigateToWidgetConfig = { _, _ -> },
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
                onLaunchMatterThreadIntent = {},
                onRequestFullscreen = {},
                onNavigateToWidgetConfig = { _, _ -> },
            )
        }

        composeTestRule.waitForIdle()
        events.emit(FrontendEvent.NavigateToNfcWrite(messageId = 7, tagId = null))
        composeTestRule.waitForIdle()

        assertEquals(7, capturedMessageId)
        assertEquals(null, capturedTagId)
    }

    @Test
    fun `Given RequestFullscreen true event then onRequestFullscreen is called with true`() {
        assertEquals(true, runRequestFullscreenTest(fullscreen = true))
    }

    @Test
    fun `Given RequestFullscreen false event then onRequestFullscreen is called with false`() {
        assertEquals(false, runRequestFullscreenTest(fullscreen = false))
    }

    @Test
    fun `Given NavigateToWidgetConfig event then onNavigateToWidgetConfig is called with entityId and widgetType`() {
        var capturedEntityId: String? = null
        var capturedWidgetType: WidgetType? = null
        val events = TestSharedFlow<FrontendEvent>()

        composeTestRule.setContent {
            FrontendEventHandler(
                events = events,
                onShowSnackbar = { _, _ -> false },
                onNavigateToSettings = {},
                onNavigateToAssist = { _, _, _ -> },
                onOpenExternalLink = {},
                onShowServerSwitcher = {},
                onNavigateToNfcWrite = { _, _ -> },
                onLaunchMatterThreadIntent = {},
                onRequestFullscreen = {},
                onNavigateToWidgetConfig = { entityId, widgetType ->
                    capturedEntityId = entityId
                    capturedWidgetType = widgetType
                },
            )
        }

        composeTestRule.waitForIdle()
        events.emit(
            FrontendEvent.NavigateToWidgetConfig(
                entityId = "light.kitchen",
                widgetType = WidgetType.MediaPlayer,
            ),
        )
        composeTestRule.waitForIdle()

        assertEquals("light.kitchen", capturedEntityId)
        assertEquals(WidgetType.MediaPlayer, capturedWidgetType)
    }

    @Test
    fun `Given LaunchMatterThreadIntent event then onLaunchMatterThreadIntent is called with the intent sender`() {
        var capturedIntentSender: IntentSender? = null
        val intentSender = mockk<IntentSender>()
        val events = TestSharedFlow<FrontendEvent>()

        composeTestRule.setContent {
            FrontendEventHandler(
                events = events,
                onShowSnackbar = { _, _ -> false },
                onNavigateToSettings = {},
                onNavigateToAssist = { _, _, _ -> },
                onOpenExternalLink = {},
                onShowServerSwitcher = {},
                onNavigateToNfcWrite = { _, _ -> },
                onLaunchMatterThreadIntent = { capturedIntentSender = it },
                onRequestFullscreen = {},
                onNavigateToWidgetConfig = { _, _ -> },
            )
        }

        composeTestRule.waitForIdle()
        events.emit(FrontendEvent.LaunchMatterThreadIntent(intentSender = intentSender))
        composeTestRule.waitForIdle()

        assertEquals(intentSender, capturedIntentSender)
    }

    private fun runRequestFullscreenTest(fullscreen: Boolean): Boolean? {
        var captured: Boolean? = null
        val events = TestSharedFlow<FrontendEvent>()

        composeTestRule.setContent {
            FrontendEventHandler(
                events = events,
                onShowSnackbar = { _, _ -> false },
                onNavigateToSettings = {},
                onNavigateToAssist = { _, _, _ -> },
                onOpenExternalLink = {},
                onShowServerSwitcher = {},
                onNavigateToNfcWrite = { _, _ -> },
                onLaunchMatterThreadIntent = {},
                onRequestFullscreen = { captured = it },
                onNavigateToWidgetConfig = { _, _ -> },
            )
        }

        composeTestRule.waitForIdle()
        events.emit(FrontendEvent.RequestFullscreen(fullscreen = fullscreen))
        composeTestRule.waitForIdle()

        return captured
    }
}
