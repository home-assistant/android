package io.homeassistant.companion.android.settings.network

import android.content.Context
import android.os.Looper
import androidx.preference.Preference
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.settings.developer.DeveloperSettingsFragment
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit4Rule
import io.homeassistant.companion.android.testing.unit.seedFakeAndroidId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class RequestTimeoutNavigationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val mainDispatcherRule = MainDispatcherJUnit4Rule()

    @Test
    fun `Given troubleshooting when opening timeout and going back then troubleshooting is restored`() = runTest {
        ApplicationProvider.getApplicationContext<Context>().seedFakeAndroidId()
        hiltRule.inject()
        val intent = SettingsActivity.newInstance(ApplicationProvider.getApplicationContext(), SettingsActivity.Deeplink.Developer)
        val controller = Robolectric.buildActivity(SettingsActivity::class.java, intent).setup()
        val manager = controller.get().supportFragmentManager
        advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()
        manager.executePendingTransactions()
        val troubleshooting = manager.findFragmentById(R.id.content) as DeveloperSettingsFragment

        troubleshooting.findPreference<Preference>("network_request_timeout")!!.performClick()
        manager.executePendingTransactions()
        assertInstanceOf(RequestTimeoutFragment::class.java, manager.findFragmentById(R.id.content))

        manager.popBackStackImmediate()
        assertInstanceOf(DeveloperSettingsFragment::class.java, manager.findFragmentById(R.id.content))
        controller.pause().stop().destroy()
        advanceUntilIdle()
    }
}
