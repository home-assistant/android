package io.homeassistant.companion.android.common.notifications

import android.app.NotificationManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FullScreenAlarmDataTest {

    private val fullScreenAlarm = mapOf(NotificationData.FULL_SCREEN_ALARM to "true")

    @Test
    fun `Given full_screen_alarm true when checking isFullScreenAlarm then returns true`() {
        assertTrue(fullScreenAlarm.isFullScreenAlarm())
    }

    @Test
    fun `Given full_screen_alarm missing or not true when checking isFullScreenAlarm then returns false`() {
        assertFalse(emptyMap<String, String>().isFullScreenAlarm())
        assertFalse(mapOf(NotificationData.FULL_SCREEN_ALARM to "false").isFullScreenAlarm())
        assertFalse(mapOf(NotificationData.FULL_SCREEN_ALARM to "yes").isFullScreenAlarm())
    }

    @Test
    fun `Given full-screen alarm without channel when checking usesAlarmSound then returns true`() {
        assertTrue(fullScreenAlarm.usesAlarmSound())
    }

    @Test
    fun `Given full-screen alarm with a custom channel when checking usesAlarmSound then returns false`() {
        assertFalse((fullScreenAlarm + (NotificationData.CHANNEL to "Security")).usesAlarmSound())
    }

    @Test
    fun `Given alarm_stream channel when checking usesAlarmSound then returns true`() {
        assertTrue(mapOf(NotificationData.CHANNEL to NotificationData.ALARM_STREAM).usesAlarmSound())
    }

    @Test
    fun `Given regular notification when checking usesAlarmSound then returns false`() {
        assertFalse(mapOf(NotificationData.MESSAGE to "Hello").usesAlarmSound())
    }

    @Test
    fun `Given alarm_icon when getting alarmIconName then returns it over the notification icon`() {
        val data = mapOf(
            NotificationData.ALARM_ICON to "mdi:shield-alert",
            NotificationData.NOTIFICATION_ICON to "mdi:home",
        )
        assertEquals("mdi:shield-alert", data.alarmIconName())
    }

    @Test
    fun `Given only notification_icon when getting alarmIconName then returns the notification icon`() {
        assertEquals("mdi:home", mapOf(NotificationData.NOTIFICATION_ICON to "mdi:home").alarmIconName())
    }

    @Test
    fun `Given no icon or a non mdi icon when getting alarmIconName then returns null`() {
        assertNull(emptyMap<String, String>().alarmIconName())
        assertNull(mapOf(NotificationData.ALARM_ICON to "shield-alert").alarmIconName())
    }

    @Test
    fun `Given full-screen alarm without importance when handling importance then returns high`() {
        assertEquals(NotificationManager.IMPORTANCE_HIGH, handleImportance(fullScreenAlarm))
    }

    @Test
    fun `Given full-screen alarm with explicit importance when handling importance then keeps it`() {
        assertEquals(
            NotificationManager.IMPORTANCE_MAX,
            handleImportance(fullScreenAlarm + (NotificationData.IMPORTANCE to "max")),
        )
    }

    @Test
    fun `Given regular notification without importance when handling importance then returns default`() {
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, handleImportance(emptyMap()))
    }
}
