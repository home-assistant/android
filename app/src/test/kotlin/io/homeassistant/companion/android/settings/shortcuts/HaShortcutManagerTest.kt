package io.homeassistant.companion.android.settings.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.database.IconDialogCompat
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class HaShortcutManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = HaShortcutManager(context, IconDialogCompat(context.assets))

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Given an entity path when buildShortcutInfo then it produces a package-scoped navigate intent`() {
        val info = manager.buildShortcutInfo(
            shortcutId = "shortcut_1",
            serverId = 2,
            label = "Label",
            longLabel = "Description",
            path = "entityId:light.kitchen",
            icon = null,
        )

        val intent = info.intent
        assertEquals(Intent.ACTION_VIEW, intent.action)
        // Scoped to our app rather than a hard-coded component.
        assertEquals(context.packageName, intent.`package`)
        assertNull(intent.component)
        assertEquals("homeassistant", intent.data?.scheme)
        assertEquals("navigate", intent.data?.host)
        assertEquals("light.kitchen", intent.data?.getQueryParameter("more-info-entity-id"))
        assertEquals("2", intent.data?.getQueryParameter("server_id"))
        // Primitive extras for the edit-form round-trip.
        assertEquals(2, intent.getIntExtra("server", -1))
        assertEquals("entityId:light.kitchen", intent.getStringExtra("path"))
    }

    @Test
    fun `Given an intent without icon extras when resolveIconFromIntent then returns null`() = runTest {
        assertNull(manager.resolveIconFromIntent(Intent()))
    }

    @Test
    fun `Given an unknown legacy iconId when resolveIconFromIntent then returns null instead of throwing`() = runTest {
        val intent = Intent().putExtra("iconId", Int.MAX_VALUE)
        assertNull(manager.resolveIconFromIntent(intent))
    }

    @Test
    fun `Given a legacy WebViewActivity shortcut when migrateLegacyShortcuts then it is rewritten to a navigate intent`() = runTest {
        mockkStatic(ShortcutManagerCompat::class)
        // Legacy shortcuts set the action to the path and stored path/server as extras.
        val legacyIntent = Intent("entityId:light.kitchen").apply {
            putExtra("server", 2)
            putExtra("path", "entityId:light.kitchen")
        }
        val legacy = ShortcutInfoCompat.Builder(context, "shortcut_1")
            .setShortLabel("Label")
            .setLongLabel("Description")
            .setIntent(legacyIntent)
            .build()
        every { ShortcutManagerCompat.getShortcuts(any(), any()) } returns listOf(legacy)
        val updated = slot<List<ShortcutInfoCompat>>()
        every { ShortcutManagerCompat.updateShortcuts(any(), capture(updated)) } returns true

        manager.migrateLegacyShortcuts()

        val migrated = updated.captured.single().intent
        assertEquals(Intent.ACTION_VIEW, migrated.action)
        assertEquals(context.packageName, migrated.`package`)
        assertEquals("light.kitchen", migrated.data?.getQueryParameter("more-info-entity-id"))
        assertEquals(2, migrated.getIntExtra("server", -1))
    }

    @Test
    fun `Given a shortcut already on the navigate format when migrateLegacyShortcuts then it is not updated`() = runTest {
        mockkStatic(ShortcutManagerCompat::class)
        val current = manager.buildShortcutInfo(
            shortcutId = "shortcut_1",
            serverId = 2,
            label = "Label",
            longLabel = "Description",
            path = "entityId:light.kitchen",
            icon = null,
        )
        every { ShortcutManagerCompat.getShortcuts(any(), any()) } returns listOf(current)

        manager.migrateLegacyShortcuts()

        verify(exactly = 0) { ShortcutManagerCompat.updateShortcuts(any(), any()) }
    }
}
