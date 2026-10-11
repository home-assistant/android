package io.homeassistant.companion.android.util.vehicle

import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Account
import io.homeassistant.companion.android.common.data.integration.IntegrationException
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EntityActionTest {

    private val integrationRepository: IntegrationRepository = mockk(relaxed = true)

    private val personEntity = EntityDisplayWithoutContext("person.abc", "ABC", Mdi.Account)

    @Test
    fun `Given try firing navigation event when entity provided then expected event is fired`() = runTest {
        val event = slot<String>()
        val data = slot<Map<String, String>>()
        coEvery { integrationRepository.fireEvent(capture(event), capture(data)) } just Runs

        personEntity.tryFireNavigationEvent(integrationRepository)

        assertEquals(EVENT_ANDROID_NAVIGATION_STARTED, event.captured)
        assertEquals(personEntity.entityId, data.captured["entity_id"])
    }

    @Test
    fun `Given integration failure when try firing navigation event then exception is swallowed`() = runTest {
        coEvery { integrationRepository.fireEvent(any(), any()) } throws IntegrationException("Not working")

        personEntity.tryFireNavigationEvent(integrationRepository)

        // Exception throwing function is called, but must be caught for the test to succeed
        coVerify { integrationRepository.fireEvent(any(), any()) }
    }
}
