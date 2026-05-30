package io.homeassistant.companion.android.common.data.wear.dashboard.state

import io.homeassistant.companion.android.common.data.wear.dashboard.WearDashboardTestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WearDashboardDependencyExtractorTest {

    @Test
    fun `Given car dashboard when extracting dependencies then entity ids and templates are found`() {
        val dependencies = WearDashboardDependencyExtractor.extract(WearDashboardTestFixtures.carDashboard)

        assertEquals(
            setOf("sensor.car_battery", "button.car_lock", "button.car_start_air_conditioner"),
            dependencies.entityIds,
        )
        assertEquals(setOf("{{ states('sensor.car_battery') }}%"), dependencies.templates)
    }

    @Test
    fun `Given car dashboard when extracting dependencies then binding paths include text and value bindings`() {
        val dependencies = WearDashboardDependencyExtractor.extract(WearDashboardTestFixtures.carDashboard)
        val paths = dependencies.bindings.map { it.path }.toSet()

        assertTrue("battery_ring.value" in paths)
        assertTrue("battery_text.text" in paths)
        assertTrue("lock.icon" in paths)
    }
}
