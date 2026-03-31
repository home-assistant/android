package io.homeassistant.companion.android

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.integration.PushWebsocketSupport
import io.homeassistant.companion.android.common.util.AppVersionProvider
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.assertNotNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class ApplicationModuleTest {
    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var appVersionProvider: AppVersionProvider

    @Inject
    @PushWebsocketSupport
    @JvmField
    var websocketSupport: Boolean? = null

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `Given injected appVersionProvider when invoking it returns current version`() {
        assertEquals(
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            appVersionProvider().value,
        )
    }

    @Test
    fun `Given injected push websocket support when checking the value it is false`() {
        val currentValue = websocketSupport
        assertNotNull(currentValue)
        assertFalse(currentValue)
    }
}
