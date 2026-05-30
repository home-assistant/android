package io.homeassistant.companion.android.common.data.wear.dashboard

import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardAction
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardBinding
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardComponent
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardConfig
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardPage
import io.homeassistant.companion.android.common.data.wear.dashboard.validation.validate
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WearDashboardValidationTest {
    @Test
    fun `Given valid car dashboard when validating then result is valid`() {
        val result = validate(WearDashboardTestFixtures.carDashboard)

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `Given duplicate page ids when validating then result reports duplicate error`() {
        val config = WearDashboardTestFixtures.carDashboard.copy(
            pages = listOf(
                WearDashboardPage(
                    id = "compact",
                    root = WearDashboardComponent.Text(
                        text = WearDashboardBinding.Constant(JsonPrimitive("A")),
                    ),
                ),
                WearDashboardPage(
                    id = "compact",
                    root = WearDashboardComponent.Text(
                        text = WearDashboardBinding.Constant(JsonPrimitive("B")),
                    ),
                ),
            ),
        )

        val result = validate(config)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.path.endsWith(".id") && it.message.contains("Duplicate") })
    }

    @Test
    fun `Given invalid service action when validating then result reports service error`() {
        val config = WearDashboardConfig(
            id = "invalid-service",
            pages = listOf(
                WearDashboardPage(
                    id = "main",
                    root = WearDashboardComponent.Button(
                        icon = WearDashboardBinding.Constant(JsonPrimitive("mdi:lock")),
                        tapAction = WearDashboardAction.CallService(
                            domain = "",
                            service = "press",
                        ),
                    ),
                ),
            ),
        )

        val result = validate(config)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.path.contains("domain") })
    }
}
