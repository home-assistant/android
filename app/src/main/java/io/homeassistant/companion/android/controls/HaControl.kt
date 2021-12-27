package io.homeassistant.companion.android.controls

import android.content.Context
import android.service.controls.Control
import android.service.controls.actions.ControlAction
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse

interface HaControl {

    fun createControl(context: Context, entity: Entity<Map<String, Any>>, area: AreaRegistryResponse?): Control

    fun performAction(integrationRepository: IntegrationRepository, action: ControlAction): Boolean
}
