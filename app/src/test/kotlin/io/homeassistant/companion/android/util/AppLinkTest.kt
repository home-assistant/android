package io.homeassistant.companion.android.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.launch.LaunchActivity
import io.homeassistant.companion.android.settings.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val SERVER_ID = 1
private const val OTHER_PACKAGE = "com.example.other"
private const val OTHER_ACTIVITY = "com.example.other.MainActivity"
private const val STORE_URL = "https://play.google.com/store/apps/details?id=$OTHER_PACKAGE"
private const val IN_APP_FLAGS = Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AppLinkTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `Given an app link when parsed then the package is extracted`() {
        assertParsed("app://$OTHER_PACKAGE", AppLink.App(OTHER_PACKAGE))
    }

    @Test
    fun `Given an intent link when parsed then the whole URI is kept`() {
        val uri = "intent://scan/#Intent;scheme=zxing;package=$OTHER_PACKAGE;end"
        assertParsed(uri, AppLink.IntentUri(uri))
    }

    @Test
    fun `Given settings links when parsed then only notification history is recognized`() {
        assertParsed("settings://notification_history", AppLink.NotificationHistory)
        assertParsed("settings://unknown", AppLink.Frontend(FrontendTarget.Default))
    }

    @Test
    fun `Given external links when parsed then the URI to open is extracted`() {
        assertParsed("https://www.home-assistant.io", AppLink.External("https://www.home-assistant.io"))
        assertParsed("deep-link://myapp://open", AppLink.External("myapp://open"))
    }

    @Test
    fun `Given frontend links when parsed then they map to a frontend target`() {
        assertParsed("/lovelace/energy", AppLink.Frontend(FrontendTarget.Path("/lovelace/energy")))
        assertParsed("entityId:light.kitchen", AppLink.Frontend(FrontendTarget.EntityMoreInfo("light.kitchen")))
        assertParsed("  ", AppLink.Frontend(FrontendTarget.Default))
    }

    @Test
    fun `Given a link surrounded by whitespace when parsed then the whitespace is ignored`() {
        assertParsed("  app://$OTHER_PACKAGE\n", AppLink.App(OTHER_PACKAGE))
    }

    @Test
    fun `Given an installed app when creating the intent then the app is launched`() {
        registerLauncherActivity()

        val intent = context.createAppLinkIntent("app://$OTHER_PACKAGE", SERVER_ID)

        assertEquals(ComponentName(OTHER_PACKAGE, OTHER_ACTIVITY), intent.component)
        assertNewTaskOnly(intent)
    }

    @Test
    fun `Given a missing app when creating the intent then the store listing is opened`() {
        val intent = context.createAppLinkIntent("app://$OTHER_PACKAGE", SERVER_ID)

        assertExternalView(intent, STORE_URL)
    }

    @Test
    fun `Given an intent link to a missing app when creating the intent then the store listing is opened`() {
        val intent = context.createAppLinkIntent("intent://scan/#Intent;scheme=zxing;package=$OTHER_PACKAGE;end", SERVER_ID)

        assertExternalView(intent, STORE_URL)
    }

    @Test
    fun `Given an intent link without package when creating the intent then the parsed intent is used`() {
        val intent = context.createAppLinkIntent("intent://scan/#Intent;scheme=zxing;end", SERVER_ID)

        assertExternalView(intent, "zxing://scan/")
    }

    @Test
    fun `Given an invalid intent link when creating the intent then the dashboard is opened`() {
        val intent = context.createAppLinkIntent("intent:#Intent;invalid", SERVER_ID)

        assertInApp(intent, LaunchActivity::class.java.name)
    }

    @Test
    fun `Given external links when creating the intent then they are viewed by another app`() {
        assertExternalView(context.createAppLinkIntent("https://www.home-assistant.io", SERVER_ID), "https://www.home-assistant.io")
        assertExternalView(context.createAppLinkIntent("deep-link://myapp://open", SERVER_ID), "myapp://open")
    }

    @Test
    fun `Given the notification history link when creating the intent then settings are opened in app`() {
        val intent = context.createAppLinkIntent("settings://notification_history", SERVER_ID)

        assertInApp(intent, SettingsActivity::class.java.name)
    }

    @Test
    fun `Given a frontend path when creating the intent then the app is opened in its own task`() {
        val intent = context.createAppLinkIntent("/lovelace/energy", SERVER_ID)

        assertInApp(intent, LaunchActivity::class.java.name)
    }

    private fun assertParsed(rawLink: String, expected: AppLink) {
        assertEquals(expected, AppLink.parse(rawLink))
    }

    private fun assertExternalView(intent: Intent, expectedUri: String) {
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(expectedUri, intent.dataString)
        assertNewTaskOnly(intent)
    }

    private fun assertNewTaskOnly(intent: Intent) {
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertFalse(intent.flags and IN_APP_FLAGS != 0)
    }

    private fun assertInApp(intent: Intent, expectedClass: String) {
        assertEquals(expectedClass, intent.component?.className)
        val expectedFlags = IN_APP_FLAGS or Intent.FLAG_ACTIVITY_NEW_TASK
        assertEquals(expectedFlags, intent.flags and expectedFlags)
    }

    private fun registerLauncherActivity() {
        val component = ComponentName(OTHER_PACKAGE, OTHER_ACTIVITY)
        val launcherFilter = IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(component)
            addIntentFilterForActivity(component, launcherFilter)
        }
    }
}
