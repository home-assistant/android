package io.homeassistant.companion.android.util.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class DropUnlessResumedTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    private val lifecycleOwner = object : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)
    }
    private val received = mutableListOf<String>()
    private lateinit var callback: (String) -> Unit

    @Before
    fun setUp() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                callback = dropUnlessResumed { received += it }
            }
        }
    }

    @Test
    fun `Given a resumed lifecycle when invoking the callback then the block receives the argument`() {
        composeTestRule.runOnIdle {
            lifecycleOwner.lifecycle.currentState = Lifecycle.State.RESUMED
            callback("tap")
        }

        assertEquals(listOf("tap"), received)
    }

    @Test
    fun `Given a started lifecycle when invoking the callback then the block is dropped`() {
        composeTestRule.runOnIdle {
            lifecycleOwner.lifecycle.currentState = Lifecycle.State.STARTED
            callback("tap")
        }

        assertTrue(received.isEmpty())
    }
}
