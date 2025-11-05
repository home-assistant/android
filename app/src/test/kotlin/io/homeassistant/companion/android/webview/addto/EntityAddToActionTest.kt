package io.homeassistant.companion.android.webview.addto

import android.content.Context
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.isAutomotive
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EntityAddToActionTest {

    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        mockkStatic("io.homeassistant.companion.android.common.util.ContextExtKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("io.homeassistant.companion.android.common.util.ContextExtKt")
    }

    @Test
    fun `Given automotive context when testing AndroidAutoFavorite then return correct attributes`() {
        every { context.isAutomotive() } returns true
        every { context.getString(commonR.string.add_to_android_auto_driving_favorite) } returns "Add to driving favorites"

        val action = EntityAddToAction.AndroidAutoFavorite

        assertEquals("mdi:car", action.mdiIcon)
        assertEquals("Add to driving favorites", action.text(context))
        assertEquals(true, action.enabled)
        assertEquals(null, action.details(context))
    }

    @Test
    fun `Given non-automotive context when testing AndroidAutoFavorite then return correct attributes`() {
        every { context.isAutomotive() } returns false
        every { context.getString(commonR.string.add_to_android_auto_favorite) } returns "Add to Android Auto favorites"

        val action = EntityAddToAction.AndroidAutoFavorite

        assertEquals("mdi:car", action.mdiIcon)
        assertEquals("Add to Android Auto favorites", action.text(context))
        assertEquals(true, action.enabled)
        assertEquals(null, action.details(context))
    }

    @Test
    fun `Given enabled Shortcut when testing attributes then return correct values`() {
        every { context.getString(commonR.string.add_to_shortcut) } returns "Add to home screen"

        val action = EntityAddToAction.Shortcut(enabled = true)

        assertEquals("mdi:open-in-new", action.mdiIcon)
        assertEquals("Add to home screen", action.text(context))
        assertEquals(true, action.enabled)
        assertEquals(null, action.details(context))
    }

    @Test
    fun `Given disabled Shortcut when testing attributes then return correct values`() {
        every { context.getString(commonR.string.add_to_shortcut) } returns "Add to home screen"
        every { context.getString(commonR.string.add_to_shortcut_limit) } returns "Maximum shortcuts reached"

        val action = EntityAddToAction.Shortcut(enabled = false)

        assertEquals("mdi:open-in-new", action.mdiIcon)
        assertEquals("Add to home screen", action.text(context))
        assertEquals(false, action.enabled)
        assertEquals("Maximum shortcuts reached", action.details(context))
    }

    @Test
    fun `Given Tile when testing attributes then return correct values`() {
        every { context.getString(commonR.string.add_to_tile) } returns "Add to Quick Settings"

        val action = EntityAddToAction.Tile

        assertEquals("mdi:tune", action.mdiIcon)
        assertEquals("Add to Quick Settings", action.text(context))
        assertEquals(true, action.enabled)
        assertEquals(null, action.details(context))
    }

    @Test
    fun `Given EntityWidget when testing attributes then return correct values`() {
        every { context.getString(commonR.string.add_to_entity_widget) } returns "Add entity widget"

        val action = EntityAddToAction.EntityWidget

        assertEquals("mdi:shape", action.mdiIcon)
        assertEquals("Add entity widget", action.text(context))
        assertEquals(true, action.enabled)
        assertEquals(null, action.details(context))
    }

    @Test
    fun `Given MediaPlayerWidget when testing attributes then return correct values`() {
        every { context.getString(commonR.string.add_to_media_player_widget) } returns "Add media player widget"

        val action = EntityAddToAction.MediaPlayerWidget

        assertEquals("mdi:play-box-multiple", action.mdiIcon)
        assertEquals("Add media player widget", action.text(context))
        assertEquals(true, action.enabled)
        assertEquals(null, action.details(context))
    }

    @Test
    fun `Given CameraWidget when testing attributes then return correct values`() {
        every { context.getString(commonR.string.add_to_camera_widget) } returns "Add camera widget"

        val action = EntityAddToAction.CameraWidget

        assertEquals("mdi:camera-image", action.mdiIcon)
        assertEquals("Add camera widget", action.text(context))
        assertEquals(true, action.enabled)
        assertEquals(null, action.details(context))
    }

    @Test
    fun `Given TodoWidget when testing attributes then return correct values`() {
        every { context.getString(commonR.string.add_to_todo_widget) } returns "Add to-do widget"

        val action = EntityAddToAction.TodoWidget

        assertEquals("mdi:clipboard-list", action.mdiIcon)
        assertEquals("Add to-do widget", action.text(context))
        assertEquals(true, action.enabled)
        assertEquals(null, action.details(context))
    }

    @Test
    fun `Given enabled Watch when testing attributes then return correct values`() {
        val watchName = "Pixel Watch"
        every { context.getString(commonR.string.add_to_watch_favorite, watchName) } returns "Add to $watchName favorites"

        val action = EntityAddToAction.Watch(name = watchName, enabled = true)

        assertEquals("mdi:watch-import", action.mdiIcon)
        assertEquals("Add to $watchName favorites", action.text(context))
        assertEquals(true, action.enabled)
        assertEquals(null, action.details(context))
    }

    @Test
    fun `Given disabled Watch when testing attributes then return correct values`() {
        val watchName = "Pixel Watch"
        every { context.getString(commonR.string.add_to_watch_favorite, watchName) } returns "Add to $watchName favorites"
        every { context.getString(commonR.string.add_to_watch_favorite_disconnected) } returns "Watch disconnected"

        val action = EntityAddToAction.Watch(name = watchName, enabled = false)

        assertEquals("mdi:watch-import", action.mdiIcon)
        assertEquals("Add to $watchName favorites", action.text(context))
        assertEquals(false, action.enabled)
        assertEquals("Watch disconnected", action.details(context))
    }
}
