package io.homeassistant.companion.android.common.data.integration

import java.time.LocalDateTime
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class AlarmControlPanelEntityExtTest {

    @Test
    fun `Given disarmed alarm without code supporting arm_away When pressed Then alarm is actionable and action is alarm_arm_away`() {
        val alarmEntity = createAlarmEntity("", requiredArmCode = false, supportArmAway = true, isArmed = false)

        assertTrue(alarmEntity.isAlarmActionable())
        assertEquals("alarm_arm_away", alarmEntity.getAlarmOnPressedAction())
    }

    @Test
    fun `Given disarmed alarm with not required arm code supporting arm_away When pressed Then alarm is actionable and action is alarm_arm_away`() {
        val alarmEntity = createAlarmEntity("A_C0DE", requiredArmCode = false, supportArmAway = true, isArmed = false)

        assertTrue(alarmEntity.isAlarmActionable())
        assertEquals("alarm_arm_away", alarmEntity.getAlarmOnPressedAction())
    }

    @Test
    fun `Given armed alarm without code When pressed Then alarm is actionable and action is alarm_disarm`() {
        val alarmEntity = createAlarmEntity("", requiredArmCode = false, supportArmAway = false, isArmed = true)

        assertTrue(alarmEntity.isAlarmActionable())
        assertEquals("alarm_disarm", alarmEntity.getAlarmOnPressedAction())
    }

    @Test
    fun `Given disarmed alarm without code not supporting arm_away When pressed Then alarm is not actionable and action is null`() {
        val alarmEntity = createAlarmEntity("", requiredArmCode = false, supportArmAway = false, isArmed = false)

        assertFalse(alarmEntity.isAlarmActionable())
        assertEquals(null, alarmEntity.getAlarmOnPressedAction())
    }

    @Test
    fun `Given disarmed alarm with required arm code supporting arm_away When pressed Then alarm is not actionable and action is null`() {
        val alarmEntity = createAlarmEntity("A_C0DE", requiredArmCode = true, supportArmAway = true, isArmed = false)

        assertFalse(alarmEntity.isAlarmActionable())
        assertEquals(null, alarmEntity.getAlarmOnPressedAction())
    }

    @Test
    fun `Given armed alarm with code When pressed Then alarm is not actionable and action is null`() {
        val alarmEntity = createAlarmEntity("A_C0DE", requiredArmCode = false, supportArmAway = true, isArmed = true)

        assertFalse(alarmEntity.isAlarmActionable())
        assertEquals(null, alarmEntity.getAlarmOnPressedAction())
    }

    @Test
    fun `Given not an alarm entity When pressed Then alarm is not actionable and action is null`() {
        val otherEntity = Entity("other_domain.an_entity_id", "", mapOf(), LocalDateTime.now(), LocalDateTime.now())
        assertFalse(otherEntity.isAlarmActionable())
        assertEquals(null, otherEntity.getAlarmOnPressedAction())
    }

    private fun createAlarmEntity(code: String, requiredArmCode: Boolean, supportArmAway: Boolean, isArmed: Boolean): Entity {
        val state = if (isArmed) "armed_away" else "disarmed"

        val attributes = mutableMapOf<String, Any?>()
        attributes["code_format"] = if (code.isEmpty()) null else "text"
        attributes["code_arm_required"] = if (code.isEmpty()) false else requiredArmCode
        attributes["supported_features"] = if (supportArmAway) ALARM_CONTROL_PANEL_SUPPORT_ARM_AWAY + 4 else 4
        return Entity("alarm_control_panel.an_alarm_id", state, attributes, LocalDateTime.now(), LocalDateTime.now())
    }
}
