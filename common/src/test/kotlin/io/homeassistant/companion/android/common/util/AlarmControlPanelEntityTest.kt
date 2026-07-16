package io.homeassistant.companion.android.common.util

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.EntityExt
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AlarmControlPanelEntityTest {

    @Test
    fun `Given an alarm without code and supporting arm_away feature should be able to be armed`() {
        val alarmEntity = createAlarmEntity("", requiredArmCode = false, supportArmAway = true, isArmed = false)
        val onPressedAction = getAlarmOnPressedAction(alarmEntity)

        assertEquals(onPressedAction, "alarm_arm_away")
    }

    @Test
    fun `Given an alarm without code but not supporting arm_away feature should not be able to be armed`() {
        val alarmEntity = createAlarmEntity("", requiredArmCode = false, supportArmAway = false, isArmed = false)
        val onPressedAction = getAlarmOnPressedAction(alarmEntity)

        assertEquals(onPressedAction, null)
    }

    @Test
    fun `Given an alarm with code that is required to arm should not be able to be armed`() {
        val alarmEntity = createAlarmEntity("A_C0DE", requiredArmCode = true, supportArmAway = true, isArmed = false)
        val onPressedAction = getAlarmOnPressedAction(alarmEntity)

        assertEquals(onPressedAction, null)
    }

    @Test
    fun `Given an alarm with code that is not required to arm should be able to be armed`() {
        val alarmEntity = createAlarmEntity("A_C0DE", requiredArmCode = false, supportArmAway = true, isArmed = false)
        val onPressedAction = getAlarmOnPressedAction(alarmEntity)

        assertEquals(onPressedAction, "alarm_arm_away")
    }

    @Test
    fun `Given an alarm with code cannot be disarmed`() {
        val alarmEntity = createAlarmEntity("A_C0DE", requiredArmCode = false, supportArmAway = true, isArmed = true)
        val onPressedAction = getAlarmOnPressedAction(alarmEntity)

        assertEquals(onPressedAction, null)
    }

    @Test
    fun `Given an alarm without code can be disarmed`() {
        val alarmEntity = createAlarmEntity("", requiredArmCode = false, supportArmAway = true, isArmed = true)
        val onPressedAction = getAlarmOnPressedAction(alarmEntity)

        assertEquals(onPressedAction, "alarm_disarm")
    }

    fun createAlarmEntity(code: String, requiredArmCode: Boolean, supportArmAway: Boolean, isArmed: Boolean): Entity {
        val state = if (isArmed) "armed_away" else "disarmed"

        val attributes = mutableMapOf<String, Any?>()
        attributes["code_format"] = if (code.isEmpty()) null else "text"
        attributes["code_arm_required"] = if (code.isEmpty()) false else requiredArmCode

        if (supportArmAway) {
            attributes["supported_features"] = EntityExt.ALARM_CONTROL_PANEL_SUPPORT_ARM_AWAY
        }
        return Entity("alarm_control_panel.an_alarm_id", state, attributes, LocalDateTime.now(), LocalDateTime.now())
    }
}
