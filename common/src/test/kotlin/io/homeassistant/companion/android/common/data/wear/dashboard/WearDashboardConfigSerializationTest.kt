package io.homeassistant.companion.android.common.data.wear.dashboard

import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardConfig
import io.homeassistant.companion.android.common.data.wear.dashboard.model.wearDashboardJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WearDashboardConfigSerializationTest {
    @Test
    fun `Given car dashboard JSON when deserializing then round-trip preserves config`() {
        val decoded = wearDashboardJson.decodeFromString<WearDashboardConfig>(
            WearDashboardTestFixtures.CAR_DASHBOARD_JSON,
        )

        val encoded = wearDashboardJson.encodeToString(decoded)
        val roundTripped = wearDashboardJson.decodeFromString<WearDashboardConfig>(encoded)

        assertEquals(decoded, roundTripped)
    }

    @Test
    fun `Given car dashboard object when serializing and deserializing then values match`() {
        val config = WearDashboardTestFixtures.carDashboard

        val roundTripped = wearDashboardJson.decodeFromString<WearDashboardConfig>(
            wearDashboardJson.encodeToString(config),
        )

        assertEquals(config, roundTripped)
    }

    @Test
    fun `Given JSON with unknown keys when deserializing then keys are ignored`() {
        val jsonWithUnknownKeys = """
            {
              "version": 1,
              "id": "car",
              "futureField": "ignored",
              "pages": [
                {
                  "id": "compact",
                  "deprecatedTitle": "Old",
                  "root": {
                    "type": "text",
                    "futureComponentField": true,
                    "text": {
                      "type": "constant",
                      "value": "Hello",
                      "extra": 1
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val config = wearDashboardJson.decodeFromString<WearDashboardConfig>(jsonWithUnknownKeys)

        assertEquals("car", config.id)
        assertEquals(1, config.pages.size)
        assertEquals("compact", config.pages.first().id)
    }
}
