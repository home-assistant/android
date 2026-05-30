package io.homeassistant.companion.android.common.data.wear.dashboard

import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardAction
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardAppSurface
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardBinding
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardComponent
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardConfig
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardPage
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardSurfaces
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardTileSurface
import kotlinx.serialization.json.JsonPrimitive

internal object WearDashboardTestFixtures {
    val carDashboard: WearDashboardConfig = WearDashboardConfig(
        version = 1,
        id = "car",
        title = "Car",
        surfaces = WearDashboardSurfaces(
            tile = WearDashboardTileSurface(page = "compact"),
            app = WearDashboardAppSurface(startPage = "compact"),
        ),
        pages = listOf(
            WearDashboardPage(
                id = "compact",
                title = "Car",
                root = WearDashboardComponent.Box(
                    id = "root",
                    children = listOf(
                        WearDashboardComponent.ProgressRing(
                            id = "battery_ring",
                            value = WearDashboardBinding.EntityState(entityId = "sensor.car_battery"),
                            min = 0,
                            max = 100,
                        ),
                        WearDashboardComponent.Text(
                            id = "battery_text",
                            text = WearDashboardBinding.Template(
                                template = "{{ states('sensor.car_battery') }}%",
                            ),
                        ),
                        WearDashboardComponent.Row(
                            id = "actions",
                            children = listOf(
                                WearDashboardComponent.Button(
                                    id = "lock",
                                    icon = WearDashboardBinding.Constant(JsonPrimitive("mdi:lock")),
                                    tapAction = WearDashboardAction.CallService(
                                        domain = "button",
                                        service = "press",
                                        data = mapOf("entity_id" to JsonPrimitive("button.car_lock")),
                                    ),
                                ),
                                WearDashboardComponent.Button(
                                    id = "climate",
                                    icon = WearDashboardBinding.Constant(JsonPrimitive("mdi:air-conditioner")),
                                    tapAction = WearDashboardAction.CallService(
                                        domain = "button",
                                        service = "press",
                                        data = mapOf(
                                            "entity_id" to JsonPrimitive("button.car_start_air_conditioner"),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    const val CAR_DASHBOARD_JSON = """
        {
          "version": 1,
          "id": "car",
          "title": "Car",
          "surfaces": {
            "tile": { "page": "compact" },
            "app": { "startPage": "compact" }
          },
          "pages": [
            {
              "id": "compact",
              "title": "Car",
              "root": {
                "type": "box",
                "id": "root",
                "children": [
                  {
                    "type": "progress_ring",
                    "id": "battery_ring",
                    "value": {
                      "type": "entity_state",
                      "entityId": "sensor.car_battery"
                    },
                    "min": 0,
                    "max": 100
                  },
                  {
                    "type": "text",
                    "id": "battery_text",
                    "text": {
                      "type": "template",
                      "template": "{{ states('sensor.car_battery') }}%"
                    }
                  },
                  {
                    "type": "row",
                    "id": "actions",
                    "children": [
                      {
                        "type": "button",
                        "id": "lock",
                        "icon": {
                          "type": "constant",
                          "value": "mdi:lock"
                        },
                        "tapAction": {
                          "type": "call_service",
                          "domain": "button",
                          "service": "press",
                          "data": {
                            "entity_id": "button.car_lock"
                          }
                        }
                      },
                      {
                        "type": "button",
                        "id": "climate",
                        "icon": {
                          "type": "constant",
                          "value": "mdi:air-conditioner"
                        },
                        "tapAction": {
                          "type": "call_service",
                          "domain": "button",
                          "service": "press",
                          "data": {
                            "entity_id": "button.car_start_air_conditioner"
                          }
                        }
                      }
                    ]
                  }
                ]
              }
            }
          ]
        }
    """
}
