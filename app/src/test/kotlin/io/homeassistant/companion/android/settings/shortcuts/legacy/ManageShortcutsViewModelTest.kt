package io.homeassistant.companion.android.settings.shortcuts.legacy

import android.app.Application
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.settings.shortcuts.HaShortcutManager
import io.homeassistant.companion.android.settings.shortcuts.SHORTCUT_EXTRA_PATH
import io.homeassistant.companion.android.settings.shortcuts.SHORTCUT_EXTRA_SERVER
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit4Rule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [Build.VERSION_CODES.TIRAMISU])
@OptIn(ExperimentalCoroutinesApi::class)
class ManageShortcutsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherJUnit4Rule()

    private lateinit var application: Application

    private val serverManager: ServerManager = mockk()
    private val shortcutManager: HaShortcutManager = mockk()
    private val entitiesForDisplayManager: EntitiesForDisplayManager = mockk()

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()

        val dynamicIds = ShortcutManagerCompat.getShortcuts(
            application,
            ShortcutManagerCompat.FLAG_MATCH_DYNAMIC,
        ).map { it.id }
        if (dynamicIds.isNotEmpty()) {
            ShortcutManagerCompat.removeDynamicShortcuts(application, dynamicIds)
        }

        coEvery { serverManager.getServer(any<Int>()) } returns fakeServer(0)
        coEvery { serverManager.servers() } returns listOf(fakeServer(0))
        every { entitiesForDisplayManager.snapshotInContext(serverId = any()) } returns emptyFlow()
        coEvery { shortcutManager.resolveIconFromIntent(any()) } returns null
    }

    private fun fakeServer(id: Int) = Server(
        id = id,
        _name = "Server $id",
        connection = ServerConnectionInfo(externalUrl = "https://example.com"),
        session = ServerSessionInfo(),
        user = ServerUserInfo(),
    )

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun fakeShortcutInfo(id: String, path: String = "path_$id") = ShortcutInfoCompat.Builder(application, id)
        .setShortLabel("Label $id")
        .setLongLabel("Description $id")
        .setIcon(IconCompat.createWithResource(application, android.R.drawable.ic_dialog_info))
        .setIntent(
            Intent(Intent.ACTION_VIEW)
                .putExtra(SHORTCUT_EXTRA_SERVER, 0)
                .putExtra(SHORTCUT_EXTRA_PATH, path),
        )
        .build()

    private fun createViewModel() = ManageShortcutsViewModel(
        serverManager = serverManager,
        shortcutManager = shortcutManager,
        entitiesForDisplayManager = entitiesForDisplayManager,
        application = application,
    )

    @Test
    fun `Given more dynamic shortcuts than slots when creating the view model then extra shortcuts are ignored`() = runTest {
        val slotShortcuts = (1..5).map { fakeShortcutInfo("shortcut_$it") }
        val strayShortcuts = listOf(
            fakeShortcutInfo("shortcutStray1"),
            fakeShortcutInfo("shortcutStray2"),
            fakeShortcutInfo("1"),
            fakeShortcutInfo("shortcut_05"),
        )
        ShortcutManagerCompat.addDynamicShortcuts(application, slotShortcuts + strayShortcuts)

        val viewModel = createViewModel()
        advanceUntilIdle()

        (1..5).forEach { index ->
            assertEquals("path_shortcut_$index", viewModel.shortcuts[index - 1].path.value)
        }
        assertEquals("", viewModel.shortcuts.last().path.value)
    }

    @Test
    fun `Given a single dynamic shortcut when creating the view model then it fills the slot matching its id`() = runTest {
        ShortcutManagerCompat.addDynamicShortcuts(application, listOf(fakeShortcutInfo("shortcut_3")))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("path_shortcut_3", viewModel.shortcuts[2].path.value)
        assertEquals("", viewModel.shortcuts[0].path.value)
    }

    @Test
    fun `Given a pinned id that is not exactly a slot id when creating the shortcut then it is not added as dynamic`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        listOf("shortcut for lights", "1", "shortcut_05").forEach { pinnedId ->
            every {
                shortcutManager.buildShortcutInfo(any(), any(), any(), any(), any(), any())
            } returns fakeShortcutInfo(pinnedId)

            viewModel.createShortcut(pinnedId, 0, "Label", "Description", "path", null)
        }

        assertTrue(
            ShortcutManagerCompat.getShortcuts(application, ShortcutManagerCompat.FLAG_MATCH_DYNAMIC).isEmpty(),
        )
    }

    @Test
    fun `Given shortcut ids when checking if reserved then only exact slot ids are reserved`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        (1..5).forEach { slot ->
            assertTrue(viewModel.isReservedShortcutId("shortcut_$slot"))
        }
        listOf("", "1", "shortcutA", "shortcut_0", "shortcut_6", "shortcut_05", "shortcut_1x").forEach { id ->
            assertFalse(viewModel.isReservedShortcutId(id))
        }
    }

    @Test
    fun `Given a slot id when creating the shortcut then it is added as dynamic`() = runTest {
        every {
            shortcutManager.buildShortcutInfo(any(), any(), any(), any(), any(), any())
        } returns fakeShortcutInfo("shortcut_2")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.createShortcut("shortcut_2", 0, "Label", "Description", "path", null)

        assertEquals(
            listOf("shortcut_2"),
            ShortcutManagerCompat.getShortcuts(application, ShortcutManagerCompat.FLAG_MATCH_DYNAMIC).map { it.id },
        )
    }

    @Test
    fun `Given an exhausted add shortcut quota when saving a launcher shortcut then use eviction aware publication`() = runTest {
        val existing = (1..4).map { fakeShortcutInfo("shortcut_$it") } + conversationShortcut()
        ShortcutManagerCompat.addDynamicShortcuts(application, existing)
        val shortcut = fakeShortcutInfo("shortcut_5")
        every { shortcutManager.buildShortcutInfo(any(), any(), any(), any(), any(), any()) } returns shortcut
        val viewModel = createViewModel()
        advanceUntilIdle()
        // Robolectric does not enforce the native quota, so make the old publication API fail.
        mockkStatic(ShortcutManagerCompat::class)
        every { ShortcutManagerCompat.addDynamicShortcuts(application, any()) } throws IllegalArgumentException("Quota exceeded")

        viewModel.createShortcut("shortcut_5", 0, "Label", "Description", "path", null)

        verify(exactly = 1) { ShortcutManagerCompat.pushDynamicShortcut(application, shortcut) }
        assertTrue(viewModel.dynamicShortcuts.any { it.id == "shortcut_5" })
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `Given a full quota on the compat path when saving a launcher shortcut then evict the conversation first`() = runTest {
        shadowOf(application.getSystemService(ShortcutManager::class.java)).setMaxShortcutCountPerActivity(5)
        val existing = (1..4).map { fakeShortcutInfo("shortcut_$it") } + conversationShortcut()
        ShortcutManagerCompat.addDynamicShortcuts(application, existing)
        every {
            shortcutManager.buildShortcutInfo(any(), any(), any(), any(), any(), any())
        } returns fakeShortcutInfo("shortcut_5")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.createShortcut("shortcut_5", 0, "Label", "Description", "path", null)

        assertEquals((1..5).map { "shortcut_$it" }, viewModel.dynamicShortcuts.map { it.id })
    }

    private fun conversationShortcut() = ShortcutInfoCompat.Builder(
        fakeShortcutInfo("notification_conversation_test"),
    ).setRank(Int.MAX_VALUE).setLongLived(true).build()
}
