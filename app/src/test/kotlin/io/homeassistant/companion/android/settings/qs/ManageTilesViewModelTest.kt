package io.homeassistant.companion.android.settings.qs

import android.app.Application
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.turbineScope
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.qs.TileDao
import io.homeassistant.companion.android.database.qs.TileEntity
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit4Rule
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [ManageTilesViewModel].
 *
 * Note: the ViewModel's init block dispatches to [kotlinx.coroutines.Dispatchers.IO] with a
 * hardcoded dispatcher. [serverManager.servers] is parked via [kotlinx.coroutines.awaitCancellation]
 * so the IO block suspends indefinitely and never reaches the `withContext(Main){ selectTile() }`
 * re-invocation that would otherwise clobber state set by individual tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [Build.VERSION_CODES.S])
@OptIn(ExperimentalCoroutinesApi::class)
class ManageTilesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherJUnit4Rule()

    private lateinit var application: Application
    private lateinit var slots: List<TileSlot>

    private val serverManager: ServerManager = mockk(relaxed = false)
    private val tileDao: TileDao = mockk(relaxed = false)

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        slots = loadTileSlots(application.resources)

        // Park the init IO block so it never re-invokes selectTile() and clobbers state set by tests.
        // (The block dispatches to a hardcoded Dispatchers.IO that the test scheduler cannot control.)
        coEvery { serverManager.servers() } coAnswers { awaitCancellation() }
        coEvery { serverManager.getServer(any<Int>()) } returns null
        coEvery { tileDao.get(any()) } returns null
        coEvery { tileDao.getAll() } returns emptyList()
        coEvery { tileDao.add(any()) } just Runs
    }

    private fun fakeServer(id: Int) = Server(
        id = id,
        _name = "Server $id",
        connection = ServerConnectionInfo(externalUrl = "https://example.com"),
        session = ServerSessionInfo(),
        user = ServerUserInfo(),
    )

    private fun fakeTile(
        dbId: Int = 0,
        tileId: String = "tile_1",
        label: String = "Label",
        subtitle: String? = null,
        entityId: String = "light.test",
        serverId: Int = 1,
        shouldVibrate: Boolean = false,
        authRequired: Boolean = false,
        iconName: String? = null,
        added: Boolean = true,
    ) = TileEntity(
        id = dbId,
        tileId = tileId,
        added = added,
        serverId = serverId,
        iconName = iconName,
        entityId = entityId,
        label = label,
        subtitle = subtitle,
        shouldVibrate = shouldVibrate,
        authRequired = authRequired,
    )

    private fun createViewModel(savedState: SavedStateHandle = SavedStateHandle()) = ManageTilesViewModel(
        savedStateHandle = savedState,
        serverManager = serverManager,
        tileDao = tileDao,
        application = application,
    )

    @Test
    fun `Given no deeplink id when created then first tile is selected`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(viewModel.slots[0].id, viewModel.state.value.selectedTileId)
        assertTrue(viewModel.slots.isNotEmpty())
    }

    @Test
    fun `Given deeplink id when created then that tile is selected and tile_data_missing is emitted`() = runTest {
        val targetId = slots[1].id

        turbineScope {
            val viewModel = createViewModel(SavedStateHandle(mapOf("id" to targetId)))
            val snackbar = viewModel.tileInfoSnackbar.testIn(backgroundScope)
            advanceUntilIdle()

            assertEquals(viewModel.slots[1].id, viewModel.state.value.selectedTileId)
            assertEquals(application.getString(commonR.string.tile_data_missing), snackbar.awaitItem())
            snackbar.cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `Given an existing setup tile when selectTile then fields are populated from the entity`() = runTest {
        val tileId = slots[0].id
        val setupTile = fakeTile(
            dbId = 5,
            tileId = tileId,
            label = "Living Room",
            subtitle = "sub",
            entityId = "switch.lamp",
            serverId = 2,
            shouldVibrate = true,
            authRequired = true,
            iconName = "mdi:account",
            added = true,
        )
        coEvery { tileDao.get(tileId) } returns setupTile

        val viewModel = createViewModel()
        viewModel.selectTile()
        advanceUntilIdle()

        assertEquals("Living Room", viewModel.state.value.tileLabel)
        assertEquals("sub", viewModel.state.value.tileSubtitle)
        assertEquals("switch.lamp", viewModel.state.value.selectedEntityId)
        assertTrue(viewModel.state.value.selectedShouldVibrate)
        assertTrue(viewModel.state.value.tileAuthRequired)
        assertEquals(2, viewModel.state.value.selectedServerId)
        assertEquals("mdi:account", viewModel.state.value.selectedIconId)
        assertEquals(commonR.string.tile_save, viewModel.state.value.submitButtonLabel)
    }

    @Test
    fun `Given a tile with serverId 0 when selectTile then server falls back to active server id`() = runTest {
        val tileId = slots[0].id
        coEvery { tileDao.get(tileId) } returns fakeTile(tileId = tileId, serverId = 0)
        coEvery { serverManager.getServer(any<Int>()) } returns fakeServer(id = 7)

        val viewModel = createViewModel()
        viewModel.selectTile()
        advanceUntilIdle()

        assertEquals(7, viewModel.state.value.selectedServerId)
    }

    @Test
    fun `Given a tile with explicit serverId when selectTile then that server id is used`() = runTest {
        val tileId = slots[0].id
        coEvery { tileDao.get(tileId) } returns fakeTile(tileId = tileId, serverId = 3)

        val viewModel = createViewModel()
        viewModel.selectTile()
        advanceUntilIdle()

        assertEquals(3, viewModel.state.value.selectedServerId)
    }

    @Test
    fun `Given id empty when selectTile then first tile is selected`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectTile("")
        advanceUntilIdle()

        assertEquals(viewModel.slots[0].id, viewModel.state.value.selectedTileId)
    }

    @Test
    fun `Given an mdi icon when selectIcon then selectedIconId is the mdi name and selectedIcon is set`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val icon = CommunityMaterial.getIconByMdiName("mdi:account")!!
        viewModel.selectIcon(icon)

        assertEquals("mdi:account", viewModel.state.value.selectedIconId)
        assertTrue(viewModel.state.value.selectedIcon === icon)
    }

    @Test
    fun `Given a null icon and no matching entity when selectIcon then selectedIcon is null`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectIcon(null)

        assertNull(viewModel.state.value.selectedIconId)
        assertNull(viewModel.state.value.selectedIcon)
    }

    @Test
    fun `Given no custom icon when selectEntityId then the entity id is set and icon follows the entity`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertNull(viewModel.state.value.selectedIconId)

        viewModel.selectEntityId("light.x")

        assertEquals("light.x", viewModel.state.value.selectedEntityId)
        assertNull(viewModel.state.value.selectedIconId)
        // selectedIcon is null because sortedEntities is empty.
        assertNull(viewModel.state.value.selectedIcon)
    }

    @Test
    fun `Given a custom icon selected when selectEntityId then the icon is not reset`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val icon = CommunityMaterial.getIconByMdiName("mdi:account")!!
        viewModel.selectIcon(icon)
        // selectedIconId is now "mdi:account" — selectEntityId must not call selectIcon(null).
        viewModel.selectEntityId("light.y")

        assertEquals("mdi:account", viewModel.state.value.selectedIconId)
        assertEquals("light.y", viewModel.state.value.selectedEntityId)
    }

    @Test
    fun `Given current state when addTile then tileDao receives a TileEntity with the mapped fields and tile_updated is emitted`() = runTest {
        // Store a tile so that selectedTileId is populated from the DB primary key.
        val tileId = slots[0].id
        val storedTile = fakeTile(
            dbId = 42,
            tileId = tileId,
            label = "Old",
            entityId = "light.old",
            added = true,
        )
        coEvery { tileDao.get(tileId) } returns storedTile

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Override state that will be written into the new TileEntity.
        viewModel.setTileLabel("Living")
        viewModel.setTileSubtitle("Lights")
        viewModel.selectEntityId("light.z")
        viewModel.setShouldVibrate(true)
        viewModel.setAuthRequired(true)
        viewModel.selectIcon(CommunityMaterial.getIconByMdiName("mdi:account")!!)

        turbineScope {
            // Subscribe before calling addTile so we don't miss the emission.
            val snackbar = viewModel.tileInfoSnackbar.testIn(backgroundScope)

            viewModel.addTile()
            advanceUntilIdle()

            // On SDK S (31 < TIRAMISU 33) the StatusBarManager branch is skipped and
            // tile_updated is emitted unconditionally.
            coVerify(exactly = 1) {
                tileDao.add(
                    match {
                        it.label == "Living" &&
                            it.subtitle == "Lights" &&
                            it.entityId == "light.z" &&
                            it.shouldVibrate &&
                            it.authRequired &&
                            it.iconName == "mdi:account" &&
                            it.tileId == viewModel.slots[0].id
                    },
                )
            }
            assertEquals(application.getString(commonR.string.tile_updated), snackbar.awaitItem())
            snackbar.cancelAndConsumeRemainingEvents()
        }
    }
}
