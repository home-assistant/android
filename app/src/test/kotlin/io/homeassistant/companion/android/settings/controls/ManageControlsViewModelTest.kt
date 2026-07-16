package io.homeassistant.companion.android.settings.controls

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.integration.ControlsAuthRequiredSetting
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayItem
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.GetEntitiesForDisplayUseCase
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit4Rule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [Build.VERSION_CODES.TIRAMISU])
@OptIn(ExperimentalCoroutinesApi::class)
class ManageControlsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherJUnit4Rule()

    private lateinit var application: Application

    private val serverManager: ServerManager = mockk()
    private val prefsRepository: PrefsRepository = mockk()
    private val getEntitiesForDisplay: GetEntitiesForDisplayUseCase = mockk()

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        coEvery { serverManager.getServer(any<Int>()) } returns null
        coEvery { prefsRepository.getControlsAuthRequired() } returns ControlsAuthRequiredSetting.NONE
        coEvery { prefsRepository.getControlsAuthEntities() } returns emptyList()
        coEvery { prefsRepository.getControlsEnableStructure() } returns false
    }

    private fun fakeServer(id: Int) = Server(
        id = id,
        _name = "Server $id",
        connection = ServerConnectionInfo(externalUrl = "https://example.com"),
        session = ServerSessionInfo(),
        user = ServerUserInfo(),
    )

    private fun fakeItem(entityId: String, name: String, isHidden: Boolean = false) = EntityDisplayItem(
        entityId = entityId,
        name = name,
        icon = mockk(),
        isHidden = isHidden,
    )

    private fun createViewModel() = ManageControlsViewModel(
        serverManager = serverManager,
        prefsRepository = prefsRepository,
        getEntitiesForDisplay = getEntitiesForDisplay,
        application = application,
    )

    @Test
    fun `Given resolved entities when created then hidden entities are excluded and the rest sorted by display name`() = runTest {
        coEvery { serverManager.servers() } returns listOf(fakeServer(1))
        every { getEntitiesForDisplay(1, any<(Entity) -> Boolean>()) } returns flowOf(
            EntityDisplayState.Loading,
            EntityDisplayState.Loaded(
                listOf(
                    fakeItem("switch.plug", name = "plug"),
                    fakeItem("light.bulb", name = "Bulb"),
                    fakeItem("light.secret", name = "Secret", isHidden = true),
                ),
            ),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.entitiesLoaded)
        assertEquals(listOf("Bulb", "plug"), viewModel.entitiesList[1]?.map { it.name })
    }

    @Test
    fun `Given a server failing to resolve entities when created then that server is left out but loading completes`() = runTest {
        coEvery { serverManager.servers() } returns listOf(fakeServer(1), fakeServer(2))
        every { getEntitiesForDisplay(1, any<(Entity) -> Boolean>()) } returns flowOf(
            EntityDisplayState.Loading,
            EntityDisplayState.Error,
        )
        every { getEntitiesForDisplay(2, any<(Entity) -> Boolean>()) } returns flowOf(
            EntityDisplayState.Loading,
            EntityDisplayState.Loaded(listOf(fakeItem("light.bulb", name = "Bulb"))),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.entitiesLoaded)
        assertEquals(setOf(2), viewModel.entitiesList.keys)
    }
}
