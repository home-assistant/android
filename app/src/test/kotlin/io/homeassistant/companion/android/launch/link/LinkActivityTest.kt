package io.homeassistant.companion.android.launch.link

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.homeassistant.companion.android.launch.LaunchActivity
import io.homeassistant.companion.android.testing.unit.seedFakeAndroidId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@UninstallModules(LinkModule::class)
@HiltAndroidTest
class LinkActivityTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val linkHandler: LinkHandler = mockk {
        coEvery { handleLink(any()) } returns LinkDestination.NoDestination
    }

    private val link = "homeassistant://navigate/lovelace/0".toUri()

    @Before
    fun setUp() {
        ApplicationProvider.getApplicationContext<Context>().seedFakeAndroidId()
    }

    @Test
    fun `Given link intent when launched then handles the link`() {
        ActivityScenario.launch<LinkActivity>(linkIntent()).use {
            coVerify(exactly = 1) { linkHandler.handleLink(link) }
        }
    }

    @Test
    fun `Given link intent relaunched from recents when launched then opens the app without handling the link`() {
        val intent = linkIntent().addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        ActivityScenario.launch<LinkActivity>(intent).use {
            val started = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
            assertEquals(LaunchActivity::class.java.name, started.component?.className)
            // No deep link, the app opens on its default destination
            assertNull(started.extras)
            coVerify(exactly = 0) { linkHandler.handleLink(any()) }
        }
    }

    private fun linkIntent(): Intent = Intent(
        Intent.ACTION_VIEW,
        link,
        ApplicationProvider.getApplicationContext(),
        LinkActivity::class.java,
    )
}
