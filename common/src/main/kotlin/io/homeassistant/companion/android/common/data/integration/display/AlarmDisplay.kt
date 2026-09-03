package io.homeassistant.companion.android.common.data.integration.display

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Immutable
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.ALARM_CONTROL_PANEL_DOMAIN
import io.homeassistant.companion.android.common.data.integration.supportsFeature

@VisibleForTesting
internal const val ALARM_CONTROL_PANEL_SUPPORT_ARM_AWAY = 2

/** Display information specific to `alarm_control_panel` entities. */
@Immutable
data class AlarmDisplay(
    /** Action to run when the panel is pressed, null when it cannot be acted on. */
    val onPressedAction: String?,
) {
    /** Whether the panel can be acted on. */
    val isActionable: Boolean get() = onPressedAction != null
}

/** Alarm panel display information, null when the entity is not an alarm control panel. */
internal fun Entity.alarmDisplay(): AlarmDisplay? {
    if (!isAlarmControlPanelEntity()) return null
    return AlarmDisplay(onPressedAction = alarmOnPressedAction())
}

/** Action to run when the panel is pressed, based on its current state and support by the app. */
private fun Entity.alarmOnPressedAction(): String? {
    if (alarmCanBeDisarmedWithoutCode()) {
        return "alarm_disarm"
    }

    if (alarmCanBeArmedAwayWithoutCode()) {
        return "alarm_arm_away"
    }

    return null
}

private fun Entity.isAlarmControlPanelEntity(): Boolean {
    return domain == ALARM_CONTROL_PANEL_DOMAIN
}

private fun Entity.alarmHasNoCode(): Boolean {
    // Retrieving the alarm entity code format to know if the alarm currently has a code
    // If code format cannot be retrieved, consider we have a code by default and actions are not applicable
    return isAlarmControlPanelEntity() && (attributes["code_format"] as? String)?.isNotEmpty() != true
}

private fun Entity.alarmCanBeArmedWithoutCode(): Boolean {
    return isAlarmControlPanelEntity() && attributes["code_arm_required"] as? Boolean == false
}

private fun Entity.supportsAlarmControlPanelArmAway(): Boolean =
    isAlarmControlPanelEntity() && supportsFeature(ALARM_CONTROL_PANEL_SUPPORT_ARM_AWAY)

private fun Entity.alarmIsDisarmed(): Boolean {
    return isAlarmControlPanelEntity() && state == "disarmed"
}

private fun Entity.alarmCanBeArmedAwayWithoutCode(): Boolean {
    if (!alarmIsDisarmed() || !supportsAlarmControlPanelArmAway()) {
        return false
    }

    return alarmHasNoCode() || alarmCanBeArmedWithoutCode()
}

private fun Entity.alarmCanBeDisarmedWithoutCode(): Boolean {
    return isAlarmControlPanelEntity() && !alarmIsDisarmed() && alarmHasNoCode()
}
