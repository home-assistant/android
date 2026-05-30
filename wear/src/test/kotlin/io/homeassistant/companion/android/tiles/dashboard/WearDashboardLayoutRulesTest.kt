package io.homeassistant.companion.android.tiles.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WearDashboardLayoutRulesTest {

    @Test
    fun `Given more children than max when limiting then truncates list`() {
        val children = listOf(1, 2, 3, 4, 5, 6, 7)

        assertEquals(listOf(1, 2, 3), WearDashboardLayoutRules.limitChildren(children, maxChildren = 3))
    }

    @Test
    fun `Given hidden visibility values when checking visibility then returns false`() {
        assertFalse(WearDashboardLayoutRules.isVisible("false"))
        assertFalse(WearDashboardLayoutRules.isVisible("hidden"))
        assertTrue(WearDashboardLayoutRules.isVisible(null))
        assertTrue(WearDashboardLayoutRules.isVisible("on"))
    }

    @Test
    fun `Given max depth when checking descend then respects limit`() {
        assertTrue(WearDashboardLayoutRules.canDescend(depth = 0, maxDepth = 5))
        assertFalse(WearDashboardLayoutRules.canDescend(depth = 5, maxDepth = 5))
    }
}
