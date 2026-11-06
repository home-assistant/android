package io.homeassistant.companion.android.changelog

import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.assist.AssistActivity
import io.homeassistant.companion.android.common.util.openUri
import io.homeassistant.companion.android.frontend.navigation.WidgetType
import io.homeassistant.companion.android.settings.SettingsActivity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The file the `Context.openUri` extension compiles into, needed to stub it. */
private const val CONTEXT_EXT_CLASS = "io.homeassistant.companion.android.common.util.ContextExtKt"

class ChangelogActionExtTest {

    private val context = mockk<Context>(relaxed = true)
    private val intent = mockk<Intent>()
    private val onShowSnackbar: suspend (message: String, action: String?) -> Boolean = { _, _ -> true }

    @BeforeEach
    fun setUp() {
        mockkObject(SettingsActivity.Companion)
        mockkObject(AssistActivity.Companion)
        mockkStatic(CONTEXT_EXT_CLASS)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Given OpenUrl when handled then the URL is opened`() = runTest {
        coEvery { context.openUri(any(), any()) } just runs

        ChangelogAction.OpenUrl("https://home-assistant.io").perform(context, onShowSnackbar)

        coVerify { context.openUri("https://home-assistant.io", onShowSnackbar) }
        verify(exactly = 0) { context.startActivity(any()) }
    }

    @Test
    fun `Given OpenSettings when handled then the deeplinked settings are started`() = runTest {
        every {
            SettingsActivity.newInstance(context, SettingsActivity.Deeplink.AssistSettings)
        } returns intent

        ChangelogAction.OpenSettings(SettingsActivity.Deeplink.AssistSettings).perform(context, onShowSnackbar)

        verify { context.startActivity(intent) }
    }

    @Test
    fun `Given OpenWidgetConfig when handled then the widget configuration is started`() = runTest {
        val widgetType = mockk<WidgetType> {
            every { toConfigureIntent(context, null) } returns intent
        }

        ChangelogAction.OpenWidgetConfig(widgetType).perform(context, onShowSnackbar)

        verify { context.startActivity(intent) }
    }

    @Test
    fun `Given OpenAssist when handled then Assist is started`() = runTest {
        every { AssistActivity.newInstance(context, any(), any(), any(), any(), any()) } returns intent

        ChangelogAction.OpenAssist.perform(context, onShowSnackbar)

        verify { context.startActivity(intent) }
    }
}
