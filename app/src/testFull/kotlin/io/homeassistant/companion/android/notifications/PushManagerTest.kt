package io.homeassistant.companion.android.notifications

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(RobolectricTestRunner::class)
class PushManagerTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var pushManager: PushManager

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `Given pushManager when retrieving FirebasePushProvider then returns FirebasePushProvider`() {
        assertTrue(pushManager.providers[FirebasePushProvider::class.java] is FirebasePushProvider)
    }

    @Test
    fun `Given pushManager when injecting then contains only one provider`() {
        assertTrue(pushManager.providers.size == 1)
    }
}
