package io.homeassistant.companion.android.tiles.dashboard

import io.homeassistant.companion.android.common.data.wear.dashboard.WearDashboardBindingKey
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardBinding
import io.homeassistant.companion.android.common.data.wear.dashboard.state.ResolvedComponentValue
import io.homeassistant.companion.android.common.data.wear.dashboard.state.WearDashboardResolvedState
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WearDashboardTileStateResolverTest {

    private val resolver = WearDashboardTileStateResolver()

    @Test
    fun `Given entity binding when resolving then uses cached state value`() {
        val binding = WearDashboardBinding.EntityState(entityId = "sensor.battery")
        val key = WearDashboardBindingKey.keyFor(binding)!!
        val state = WearDashboardResolvedState(mapOf(key to ResolvedComponentValue.TextValue("82")))

        assertEquals("82", resolver.resolveString(binding, state))
    }

    @Test
    fun `Given constant binding when resolving then returns literal value`() {
        val binding = WearDashboardBinding.Constant(JsonPrimitive("Hello"))
        val state = WearDashboardResolvedState()

        assertEquals("Hello", resolver.resolveString(binding, state))
    }

    @Test
    fun `Given truthy values when checking isTruthy then returns expected results`() {
        assertTrue(resolver.isTruthy("on"))
        assertTrue(resolver.isTruthy("locked"))
        assertFalse(resolver.isTruthy("off"))
        assertFalse(resolver.isTruthy("false"))
        assertFalse(resolver.isTruthy(""))
    }
}
