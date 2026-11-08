package io.homeassistant.companion.android.util

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NavigatorTest {
    private val navigator = Navigator()

    @Test
    fun `Given navigations requested before collecting when collecting then each is delivered once in order`() = runTest {
        navigator.navigateTo("first")
        navigator.navigateTo(Navigator.NavigatorItem(id = "second", popBackstackTo = "first"))

        navigator.flow.test {
            assertEquals(Navigator.NavigatorItem("first"), awaitItem())
            assertEquals(Navigator.NavigatorItem(id = "second", popBackstackTo = "first"), awaitItem())
        }
        navigator.flow.test {
            expectNoEvents()
        }
    }
}
