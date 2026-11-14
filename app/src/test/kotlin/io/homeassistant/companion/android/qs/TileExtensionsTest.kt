package io.homeassistant.companion.android.qs

import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import io.github.timoptr.mdiicons.MdiIcon
import io.github.timoptr.mdiicons.toBitmap
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.EntityChange
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.qs.TileDao
import io.homeassistant.companion.android.database.qs.TileEntity
import io.homeassistant.companion.android.settings.qs.TileId
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.homeassistant.companion.android.util.mockServer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private const val SERVER_ID = 1
private const val ENTITY_ID = "light.kitchen"
private val TILE_ID = TileId("tile_1")
private const val MDI_BITMAP_FACADE = "io.github.timoptr.mdiicons.MdiIconBitmapKt"

class TileExtensionsTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherJUnit5Extension()

    private val serverManager: ServerManager = mockk()
    private val integrationRepository: IntegrationRepository = mockk()
    private val tileDao: TileDao = mockk()
    private val tile: Tile = mockk(relaxed = true)
    private val states = mutableListOf<Int>()
    private val changes = MutableSharedFlow<EntityChange>()
    private lateinit var service: TileExtensions

    private class TestTileService(private val tile: Tile, ioDispatcher: CoroutineDispatcher) : TileExtensions(ioDispatcher) {
        override val tileId: TileId = TILE_ID
        override fun getTile(): Tile = tile
        override fun getApplicationContext(): Context = mockk(relaxed = true)
    }

    @BeforeEach
    fun setUp() {
        SdkVersion.sdkInt = Build.VERSION_CODES.TIRAMISU
        mockkStatic(MDI_BITMAP_FACADE)
        every { any<MdiIcon>().toBitmap(any<Context>(), any(), any()) } returns mockk()
    }

    @AfterEach
    fun tearDown() {
        if (::service.isInitialized) service.onDestroy()
        unmockkAll()
    }

    @Test
    fun `Given the entity is removed when listening then the tile becomes unavailable after showing its state`() = runTest(mainDispatcherExtension.testDispatcher) {
        service = createService()

        service.onStartListening()
        changes.subscriptionCount.first { it > 0 }
        advanceUntilIdle()
        assertEquals(Tile.STATE_INACTIVE, states.last())

        changes.emit(EntityChange.StateChanged(entity(state = "on")))
        advanceUntilIdle()
        assertEquals(Tile.STATE_ACTIVE, states.last())

        changes.emit(EntityChange.Removed(ENTITY_ID))
        advanceUntilIdle()
        assertEquals(listOf(Tile.STATE_INACTIVE, Tile.STATE_ACTIVE, Tile.STATE_UNAVAILABLE), states)
    }

    private fun createService(): TileExtensions {
        every { tile.state = capture(states) } returns Unit
        coEvery { tileDao.get(TILE_ID.value) } returns TileEntity(
            tileId = TILE_ID.value,
            added = true,
            serverId = SERVER_ID,
            iconName = null,
            entityId = ENTITY_ID,
            label = "Kitchen",
            subtitle = null,
            shouldVibrate = false,
            authRequired = false,
        )
        coEvery { serverManager.getServer(SERVER_ID) } returns mockServer(url = null, name = "Home", serverId = SERVER_ID)
        coEvery { serverManager.integrationRepository(SERVER_ID) } returns integrationRepository
        coEvery { integrationRepository.getEntity(ENTITY_ID) } returns entity(state = "off")
        coEvery { integrationRepository.getEntityChanges(listOf(ENTITY_ID)) } returns changes

        return TestTileService(tile, mainDispatcherExtension.testDispatcher).apply {
            serverManager = this@TileExtensionsTest.serverManager
            tileDao = this@TileExtensionsTest.tileDao
        }
    }

    private fun entity(state: String) = Entity(
        entityId = ENTITY_ID,
        state = state,
        attributes = emptyMap(),
        lastChanged = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
        lastUpdated = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
    )
}
