package io.homeassistant.companion.android.util.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.CAMERA_DOMAIN

@Composable
fun getEntityDomainString(domain: String): String {
    return when (domain) {
        "automation" -> stringResource(commonR.string.domain_automation)
        "button" -> stringResource(commonR.string.domain_button)
        CAMERA_DOMAIN -> stringResource(commonR.string.domain_camera)
        "climate" -> stringResource(commonR.string.domain_climate)
        "cover" -> stringResource(commonR.string.domain_cover)
        "fan" -> stringResource(commonR.string.domain_fan)
        "input_boolean" -> stringResource(commonR.string.domain_input_boolean)
        "input_button" -> stringResource(commonR.string.domain_input_button)
        "input_number" -> stringResource(commonR.string.domain_input_number)
        "light" -> stringResource(commonR.string.domain_light)
        "lock" -> stringResource(commonR.string.domain_lock)
        "scene" -> stringResource(commonR.string.domain_scene)
        "script" -> stringResource(commonR.string.domain_script)
        "switch" -> stringResource(commonR.string.domain_switch)
        "vacuum" -> stringResource(commonR.string.domain_vacuum)
        else -> ""
    }
}
