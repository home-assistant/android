package io.homeassistant.companion.android.common.data.integration.display

import io.homeassistant.companion.android.common.data.integration.Entity
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull

class AlarmDisplayTest {

    @Test
    fun `Given disarmed alarm without code supporting arm_away When resolved for display Then pressing arms away`() {
        val alarmEntity = createAlarmEntity("", requiredArmCode = false, supportArmAway = true, isArmed = false)

        val alarm = checkNotNull(alarmEntity.displayedAlarm())
        assertEquals("alarm_arm_away", alarm.onPressedAction)
        assertTrue(alarm.isActionable)
    }

    @Test
    fun `Given disarmed alarm with not required arm code supporting arm_away When resolved for display Then pressing arms away`() {
        val alarmEntity = createAlarmEntity("A_C0DE", requiredArmCode = false, supportArmAway = true, isArmed = false)

        assertEquals("alarm_arm_away", alarmEntity.displayedAlarm()?.onPressedAction)
    }

    @Test
    fun `Given armed alarm without code When resolved for display Then pressing disarms`() {
        val alarmEntity = createAlarmEntity("", requiredArmCode = false, supportArmAway = false, isArmed = true)

        assertEquals("alarm_disarm", alarmEntity.displayedAlarm()?.onPressedAction)
    }

    @Test
    fun `Given disarmed alarm without code not supporting arm_away When resolved for display Then it is not actionable`() {
        val alarmEntity = createAlarmEntity("", requiredArmCode = false, supportArmAway = false, isArmed = false)

        val alarm = checkNotNull(alarmEntity.displayedAlarm())
        assertNull(alarm.onPressedAction)
        assertFalse(alarm.isActionable)
    }

    @Test
    fun `Given disarmed alarm with required arm code supporting arm_away When resolved for display Then it is not actionable`() {
        val alarmEntity = createAlarmEntity("A_C0DE", requiredArmCode = true, supportArmAway = true, isArmed = false)

        val alarm = checkNotNull(alarmEntity.displayedAlarm())
        assertNull(alarm.onPressedAction)
        assertFalse(alarm.isActionable)
    }

    @Test
    fun `Given armed alarm with code When resolved for display Then it is not actionable`() {
        val alarmEntity = createAlarmEntity("A_C0DE", requiredArmCode = false, supportArmAway = true, isArmed = true)

        assertFalse(checkNotNull(alarmEntity.displayedAlarm()).isActionable)
    }

    /** Resolves the alarm display information through the public display resolution. */
    private fun Entity.displayedAlarm(): AlarmDisplay? = EntityDisplayWithoutContext(this).alarm

    private fun createAlarmEntity(code: String, requiredArmCode: Boolean, supportArmAway: Boolean, isArmed: Boolean): Entity {
        val state = if (isArmed) "armed_away" else "disarmed"

        val attributes = mutableMapOf<String, Any?>()
        attributes["code_format"] = if (code.isEmpty()) null else "text"
        attributes["code_arm_required"] = if (code.isEmpty()) false else requiredArmCode
        attributes["supported_features"] = if (supportArmAway) ALARM_CONTROL_PANEL_SUPPORT_ARM_AWAY + 4 else 4
        return Entity(
            "alarm_control_panel.an_alarm_id",
            state,
            attributes,
            LocalDateTime.now(),
            LocalDateTime.now(),
        )
    }
}
