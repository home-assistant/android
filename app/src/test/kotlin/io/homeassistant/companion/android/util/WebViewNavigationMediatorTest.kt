package io.homeassistant.companion.android.util

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WebViewNavigationMediatorTest {

    private val mediator = WebViewNavigationMediator()

    @Test
    fun `Given a visible server when set then it is published`() {
        mediator.setVisibleServer(3)

        assertEquals(3, mediator.visibleServerId.value)
    }

    @Test
    fun `Given the active server placeholder when set then it is published as null`() {
        mediator.setVisibleServer(ServerManager.SERVER_ID_ACTIVE)

        assertNull(mediator.visibleServerId.value)
    }

    @Test
    fun `Given a visible server when cleared then null is published`() {
        mediator.setVisibleServer(3)

        mediator.setVisibleServer(null)

        assertNull(mediator.visibleServerId.value)
    }

    @Test
    fun `Given a navigation request when made then the target is emitted`() = runTest {
        mediator.navigationRequests.test {
            mediator.requestNavigation(FrontendTarget.Path("/lovelace/cameras"))

            assertEquals(FrontendTarget.Path("/lovelace/cameras"), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }
}
