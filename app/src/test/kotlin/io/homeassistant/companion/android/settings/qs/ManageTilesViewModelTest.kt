package io.homeassistant.companion.android.settings.qs

import android.app.Application
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.turbineScope
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayItem
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.GetEntitiesForDisplayUseCase
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.qs.TileDao
import io.homeassistant.companion.android.database.qs.TileEntity
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit4Rule
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName
import io.homeassistant.companion.android.util.icondialog.mdiName
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [Build.VERSION_CODES.S])
@OptIn(ExperimentalCoroutinesApi::class)
class ManageTilesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherJUnit4Rule()

    private lateinit var application: Application

    private val serverManager: ServerManager = mockk(relaxed = false)
    private val tileDao: TileDao = mockk(relaxed = false)
    private val getEntitiesForDisplay: GetEntitiesForDisplayUseCase = mockk()

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        coEvery { serverManager.servers() } coAnswers { awaitCancellation() }
        coEvery { serverManager.getServer(any<Int>()) } returns null
        coEvery { tileDao.get(any()) } returns null
        coEvery { tileDao.getAll() } returns emptyList()
        coEvery { tileDao.add(any()) } returns 1L
        every { tileDao.getAllFlow() } returns flowOf(emptyList())
        every { getEntitiesForDisplay(any(), any<(Entity) -> Boolean>()) } returns flowOf(EntityDisplayState.Loading)
    }

    private fun fakeServer(id: Int) = Server(
        id = id,
        _name = "Server $id",
        connection = ServerConnectionInfo(externalUrl = "https://example.com"),
        session = ServerSessionInfo(),
        user = ServerUserInfo(),
    )

    private fun fakeEntity(entityId: String) = Entity(
        entityId = entityId,
        state = "on",
        attributes = emptyMap(),
        lastChanged = LocalDateTime.now(),
        lastUpdated = LocalDateTime.now(),
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
        getEntitiesForDisplay = getEntitiesForDisplay,
    )

    @Test
    fun `Given no saved tile id when created then first tile is selected`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(tileSlots.first().id, viewModel.state.value.selectedTileId)
    }

    @Test
    fun `Given a saved tile id when created then that tile is selected and tile_data_missing is emitted`() = runTest {
        val targetId = tileSlots[1].id

        turbineScope {
            val viewModel = createViewModel(SavedStateHandle(mapOf("id" to targetId.value)))
            val snackbar = viewModel.tileInfoSnackbar.testIn(backgroundScope)
            advanceUntilIdle()

            assertEquals(tileSlots[1].id, viewModel.state.value.selectedTileId)
            assertEquals(commonR.string.tile_data_missing, snackbar.awaitItem())
            snackbar.cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `Given an existing setup tile when selectTile then fields are populated from the entity`() = runTest {
        val tileId = tileSlots[0].id.value
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
        assertEquals("mdi:account", viewModel.state.value.customIcon?.mdiName)
        assertEquals(commonR.string.tile_save, viewModel.state.value.submitButtonLabel)
    }

    @Test
    fun `Given a setup tile selected when selecting a slot without setup data then the tile fields are reset`() = runTest {
        val setupTileId = tileSlots[0].id.value
        coEvery { tileDao.get(setupTileId) } returns fakeTile(
            dbId = 5,
            tileId = setupTileId,
            label = "Living Room",
            subtitle = "sub",
            entityId = "switch.lamp",
            iconName = "mdi:account",
        )

        val viewModel = createViewModel()
        viewModel.selectTile(tileSlots[0].id)
        advanceUntilIdle()

        viewModel.selectTile(tileSlots[1].id)
        advanceUntilIdle()

        assertEquals("", viewModel.state.value.tileLabel)
        assertEquals("", viewModel.state.value.tileSubtitle)
        assertNull(viewModel.state.value.selectedEntityId)
        assertNull(viewModel.state.value.customIcon)
    }

    @Test
    fun `Given a tile with serverId 0 when selectTile then server falls back to active server id`() = runTest {
        val tileId = tileSlots[0].id.value
        coEvery { tileDao.get(tileId) } returns fakeTile(tileId = tileId, serverId = 0)
        coEvery { serverManager.getServer(any<Int>()) } returns fakeServer(id = 7)

        val viewModel = createViewModel()
        viewModel.selectTile()
        advanceUntilIdle()

        assertEquals(7, viewModel.state.value.selectedServerId)
    }

    @Test
    fun `Given a tile with explicit serverId when selectTile then that server id is used`() = runTest {
        val tileId = tileSlots[0].id.value
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

        viewModel.selectTile(TileId(""))
        advanceUntilIdle()

        assertEquals(tileSlots[0].id, viewModel.state.value.selectedTileId)
    }

    @Test
    fun `Given an mdi icon when selectIcon then it becomes the custom and displayed icon`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val icon = CommunityMaterial.getIconByMdiName("mdi:account")!!
        viewModel.selectIcon(icon)

        assertTrue(viewModel.state.value.customIcon === icon)
        assertTrue(viewModel.state.value.selectedIcon === icon)
    }

    @Test
    fun `Given a null icon and no matching entity when selectIcon then selectedIcon is null`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectIcon(null)

        assertNull(viewModel.state.value.customIcon)
        assertNull(viewModel.state.value.selectedIcon)
    }

    @Test
    fun `Given no custom icon when selectEntityId then the entity id is set and icon follows the entity`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertNull(viewModel.state.value.customIcon)

        viewModel.selectEntityId("light.x")

        assertEquals("light.x", viewModel.state.value.selectedEntityId)
        assertNull(viewModel.state.value.customIcon)
        // selectedIcon is null because the entity is not in the loaded entities.
        assertNull(viewModel.state.value.selectedIcon)
    }

    @Test
    fun `Given a custom icon selected when selectEntityId then the icon is not reset`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val icon = CommunityMaterial.getIconByMdiName("mdi:account")!!
        viewModel.selectIcon(icon)
        viewModel.selectEntityId("light.y")

        assertTrue(viewModel.state.value.customIcon === icon)
        assertEquals("light.y", viewModel.state.value.selectedEntityId)
    }

    @Test
    fun `Given current state when addTile then tileDao receives a TileEntity with the mapped fields and tile_updated is emitted`() = runTest {
        // Store a tile so that selectedTileId is populated from the DB primary key.
        val tileId = tileSlots[0].id.value
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

            viewModel.addTile(application)
            advanceUntilIdle()

            // On SDK S (31 < TIRAMISU 33) the StatusBarManager branch is skipped and
            // tile_updated is emitted unconditionally.
            coVerify(exactly = 1) {
                tileDao.add(
                    match {
                        it.id == 42 &&
                            it.label == "Living" &&
                            it.subtitle == "Lights" &&
                            it.entityId == "light.z" &&
                            it.shouldVibrate &&
                            it.authRequired &&
                            it.iconName == "mdi:account" &&
                            it.tileId == tileSlots[0].id.value
                    },
                )
            }
            assertEquals(commonR.string.tile_updated, snackbar.awaitItem())
            snackbar.cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `Given no existing tile row when addTile then a new row is inserted with id 0 and added false`() = runTest {
        val tileId = tileSlots[0].id.value
        coEvery { tileDao.get(tileId) } returns null // no existing row for this tile slot

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setTileLabel("New tile")
        viewModel.selectEntityId("light.new")

        turbineScope {
            val snackbar = viewModel.tileInfoSnackbar.testIn(backgroundScope)

            viewModel.addTile(application)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                tileDao.add(
                    match {
                        it.id == 0 && !it.added && it.label == "New tile" && it.entityId == "light.new" && it.tileId == tileId
                    },
                )
            }
            assertEquals(commonR.string.tile_updated, snackbar.awaitItem())
            snackbar.cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `Given a tile saved once when addTile is called again then the second save reuses the same persisted id`() = runTest {
        val tileId = tileSlots[0].id.value
        // First save: no existing row.
        coEvery { tileDao.get(tileId) } returns null andThen fakeTile(dbId = 7, tileId = tileId, label = "First save")

        val viewModel = createViewModel()
        advanceUntilIdle()

        turbineScope {
            val snackbar = viewModel.tileInfoSnackbar.testIn(backgroundScope)

            viewModel.setTileLabel("First save")
            viewModel.selectEntityId("light.a")
            viewModel.addTile(application)
            advanceUntilIdle()
            assertEquals(commonR.string.tile_updated, snackbar.awaitItem())

            // Second save: DAO now returns the row that was persisted with id = 7 by the first save.
            viewModel.setTileLabel("Second save")
            viewModel.addTile(application)
            advanceUntilIdle()
            assertEquals(commonR.string.tile_updated, snackbar.awaitItem())

            coVerify(exactly = 1) {
                tileDao.add(match { it.id == 7 && it.label == "Second save" })
            }
            snackbar.cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `Given showSubtitle when setTileSubtitle then state tileSubtitle is set`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setTileSubtitle("My subtitle")

        assertEquals("My subtitle", viewModel.state.value.tileSubtitle)
    }

    @Test
    fun `Given a blank subtitle when addTile then persisted subtitle is null`() = runTest {
        val tileId = tileSlots[0].id.value
        coEvery { tileDao.get(tileId) } returns fakeTile(dbId = 9, tileId = tileId)

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setTileLabel("Label")
        viewModel.selectEntityId("light.b")
        // tileSubtitle left as default "" (blank)

        viewModel.addTile(application)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            tileDao.add(match { it.subtitle == null })
        }
    }

    @Test
    fun `Given existing tile with null subtitle when selectTile then state tileSubtitle is empty string`() = runTest {
        val tileId = tileSlots[0].id.value
        coEvery { tileDao.get(tileId) } returns fakeTile(dbId = 11, tileId = tileId, subtitle = null)

        val viewModel = createViewModel()
        viewModel.selectTile()
        advanceUntilIdle()

        assertEquals("", viewModel.state.value.tileSubtitle)
    }

    @Test
    fun `Given stored tiles when created then the slot items carry the labels of the configured tiles`() = runTest {
        every { tileDao.getAllFlow() } returns flowOf(
            listOf(
                fakeTile(tileId = "tile_1", label = "Living Room"),
                fakeTile(tileId = "tile_2", label = ""),
            ),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val slotItems = viewModel.state.value.tileSlotItems
        assertEquals(tileSlots.size, slotItems.size)
        assertEquals("Living Room", slotItems.first { it.id == TileId("tile_1") }.label)
        // A blank label counts as not configured.
        assertNull(slotItems.first { it.id == TileId("tile_2") }.label)
    }

    @Test
    fun `Given the entity picker when loading entities then only entities usable in a tile are requested`() = runTest {
        // Regression test: the filter passed to the use case must exclude entities a tile
        // cannot act on, otherwise the picker offers entities that do nothing when clicked.
        val filter = slot<(Entity) -> Boolean>()
        every { getEntitiesForDisplay(any(), capture(filter)) } returns flowOf(EntityDisplayState.Loading)

        createViewModel()
        advanceUntilIdle()

        assertTrue(filter.captured(fakeEntity("light.bulb")))
        assertTrue(filter.captured(fakeEntity("scene.movie_night")))
        assertFalse(filter.captured(fakeEntity("sensor.temperature")))
    }

    @Test
    fun `Given a setup tile with slow entity loading when selectTile then the tile fields are populated before the entities finish loading`() = runTest {
        // The tile fields come from the database and must not wait for the (potentially slow)
        // entity loading; only the entity list itself stays in Loading.
        val tileId = tileSlots[0].id.value
        coEvery { tileDao.get(tileId) } returns
            fakeTile(tileId = tileId, label = "Living Room", entityId = "switch.lamp", serverId = 2)
        every { getEntitiesForDisplay(2, any<(Entity) -> Boolean>()) } returns flow {
            emit(EntityDisplayState.Loading)
            delay(10_000.milliseconds)
            emit(EntityDisplayState.Loaded(emptyList()))
        }

        val viewModel = createViewModel()
        advanceTimeBy(100.milliseconds)

        assertEquals("Living Room", viewModel.state.value.tileLabel)
        assertEquals("switch.lamp", viewModel.state.value.selectedEntityId)
        assertInstanceOf(EntityDisplayState.Loading::class.java, viewModel.state.value.entityDisplayState)
    }

    @Test
    fun `Given a fast server switch while the first server is still loading when the slower flow resolves then it does not overwrite the newer server's entities`() = runTest {
        // The first server resolves slowly (simulates a slow network) with an entity that must never
        // reach the final state, while the second (newer) server resolves quickly with the entity
        // that is expected to win. Without cancelling the stale in-flight collection, the slow flow
        // would emit last and clobber the fresh one.
        val icon = CommunityMaterial.getIconByMdiName("mdi:account")!!
        every { getEntitiesForDisplay(1, any<(Entity) -> Boolean>()) } returns flow {
            emit(EntityDisplayState.Loading)
            delay(100.milliseconds)
            emit(EntityDisplayState.Loaded(listOf(EntityDisplayItem(entityId = "light.stale", name = "Stale", icon = icon))))
        }
        every { getEntitiesForDisplay(2, any<(Entity) -> Boolean>()) } returns flow {
            emit(EntityDisplayState.Loading)
            delay(10.milliseconds)
            emit(EntityDisplayState.Loaded(listOf(EntityDisplayItem(entityId = "light.fresh", name = "Fresh", icon = icon))))
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectServerId(1)
        viewModel.selectServerId(2)
        advanceUntilIdle()

        val finalState = assertInstanceOf(EntityDisplayState.Loaded::class.java, viewModel.state.value.entityDisplayState)
        assertEquals(listOf("light.fresh"), finalState.entities.map { it.entityId })
    }
}
