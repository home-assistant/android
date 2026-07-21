package io.homeassistant.companion.android.common.data.integration

import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.ALARM_CONTROL_PANEL_DOMAIN

@VisibleForTesting
internal const val ALARM_CONTROL_PANEL_SUPPORT_ARM_AWAY = 2

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

private fun Entity.supportsAlarmControlPanelArmAway(): Boolean {
    if (!isAlarmControlPanelEntity()) {
        return false
    }

    return (attributes["supported_features"] as Int) and
        ALARM_CONTROL_PANEL_SUPPORT_ARM_AWAY == ALARM_CONTROL_PANEL_SUPPORT_ARM_AWAY
}

private fun Entity.alarmIsDisarmed(): Boolean {
    return isAlarmControlPanelEntity() && state == "disarmed"
}

private fun Entity.alarmCanBeArmedAwayWithoutCode(): Boolean {
    if (!isAlarmControlPanelEntity()) {
        return false
    }

    if (!alarmIsDisarmed() || !supportsAlarmControlPanelArmAway()) {
        return false
    }

    return alarmHasNoCode() || alarmCanBeArmedWithoutCode()
}

private fun Entity.alarmCanBeDisarmedWithoutCode(): Boolean {
    return isAlarmControlPanelEntity() && !alarmIsDisarmed() && alarmHasNoCode()
}

/** @return `true` if [getAlarmOnPressedAction] would return an action, `false` otherwise */
fun Entity.isAlarmActionable(): Boolean {
    return getAlarmOnPressedAction() != null
}

/** @return action string for alarm control panel entities, based on its current state and support by the app */
fun Entity.getAlarmOnPressedAction(): String? {
    if (!isAlarmControlPanelEntity()) {
        return null
    }

    if (alarmCanBeDisarmedWithoutCode()) {
        return "alarm_disarm"
    }

    if (alarmCanBeArmedAwayWithoutCode()) {
        return "alarm_arm_away"
    }

    return null
}
