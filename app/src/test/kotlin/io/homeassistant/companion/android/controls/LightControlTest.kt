package io.homeassistant.companion.android.controls

import android.app.PendingIntent
import android.content.Intent
import android.service.controls.Control
import android.service.controls.templates.ToggleRangeTemplate
import android.service.controls.templates.ToggleTemplate
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Bookmark
import io.homeassistant.companion.android.common.data.integration.EntityPosition
import io.homeassistant.companion.android.common.data.integration.LightControls
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ENTITY_ID = "light.living_room"

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class LightControlTest {

    @Test
    fun `Given a dimmable light off when providing the features then the template is a range that is not checked`() {
        val template = templateFor(isActive = false, brightness = EntityPosition(value = 0f, min = 0f, max = 100f))

        assertTrue(template is ToggleRangeTemplate)
        assertFalse((template as ToggleRangeTemplate).isChecked)
        assertEquals(0f, template.range.currentValue)
    }

    @Test
    fun `Given a dimmable light on when providing the features then the template is a checked range at its level`() {
        val template = templateFor(isActive = true, brightness = EntityPosition(value = 60f, min = 0f, max = 100f))

        assertTrue(template is ToggleRangeTemplate)
        assertTrue((template as ToggleRangeTemplate).isChecked)
        assertEquals(60f, template.range.currentValue)
    }

    @Test
    fun `Given a light without brightness when providing the features then the template is a toggle`() {
        val template = templateFor(isActive = true, brightness = null)

        assertTrue(template is ToggleTemplate)
        assertTrue((template as ToggleTemplate).isChecked)
    }

    private fun templateFor(isActive: Boolean, brightness: EntityPosition?) = LightControl.provideControlFeatures(
        context = ApplicationProvider.getApplicationContext(),
        control = Control.StatefulBuilder(ENTITY_ID, pendingIntent()),
        item = EntityDisplayWithContext(
            item = EntityDisplayWithoutContext(
                entityId = ENTITY_ID,
                name = "Living room",
                icon = Mdi.Bookmark,
                isActive = isActive,
                lightControls = LightControls(brightness = brightness, colorTemperature = null),
            ),
        ),
        info = HaControlInfo(systemId = "1.$ENTITY_ID", entityId = ENTITY_ID, serverId = 1),
    ).build().controlTemplate

    private fun pendingIntent() = PendingIntent.getActivity(
        ApplicationProvider.getApplicationContext(),
        0,
        Intent(),
        PendingIntent.FLAG_IMMUTABLE,
    )
}
