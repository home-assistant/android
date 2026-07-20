package io.homeassistant.companion.android.settings.shortcuts.data.entities

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShortcutDestinationTest {

    @Test
    fun `Given valid entity ids when isValid then returns true`() {
        listOf(
            "light.living_room",
            "switch.kitchen",
            "binary_sensor.front_door",
            "sensor.temperature_1",
            "light.kitchen__sink",
            "sensor.foo__bar",
        ).forEach { assertTrue(ShortcutDestination.Entity(it).isValid, it) }
    }

    @Test
    fun `Given invalid entity ids when isValid then returns false`() {
        listOf(
            "",
            "light",
            "light.",
            ".living_room",
            "_light.kitchen",
            "light._kitchen",
            "binary__sensor.front_door",
            "Light.Kitchen",
            "light.kitchen-room",
        ).forEach { assertFalse(ShortcutDestination.Entity(it).isValid, it) }
    }

    @Test
    fun `Given valid dashboard paths when isValid then returns true`() {
        listOf(
            "/lovelace/home",
            "/my-dashboard",
            "/lovelace/0",
        ).forEach { assertTrue(ShortcutDestination.Dashboard(it).isValid, it) }
    }

    @Test
    fun `Given invalid dashboard paths when isValid then returns false`() {
        listOf(
            "",
            "lovelace/home",
            "https://example.com",
            "http://example.com",
            "//example.com",
            "entityId:light.living_room",
        ).forEach { assertFalse(ShortcutDestination.Dashboard(it).isValid, it) }
    }
}
