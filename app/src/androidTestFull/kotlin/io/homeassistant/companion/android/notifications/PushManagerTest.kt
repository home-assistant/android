package io.homeassistant.companion.android.notifications

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class PushManagerTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val ruleChain = DetectLeaksAfterTestSuccess()

    @Inject
    lateinit var pushManager: PushManager

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun testProviders() {
        assertNotNull(pushManager.providers)
        assertEquals(pushManager.providers.size, 1)
        assertNotNull(pushManager.providers[FirebasePushProvider::class.java])
        assertEquals(pushManager.providers[FirebasePushProvider::class.java]!!::class, FirebasePushProvider::class)
    }
}
