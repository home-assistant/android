package io.homeassistant.companion.android.common.data.mediacontrols

import app.cash.turbine.test
import io.homeassistant.companion.android.database.mediacontrol.MediaControlsConfig
import io.homeassistant.companion.android.database.mediacontrol.MediaControlsDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val SERVER_ID = 1
private const val ENTITY_ID = "media_player.test"

class MediaControlsRepositoryImplTest {

    private val dao: MediaControlsDao = mockk(relaxed = true)

    private lateinit var repository: MediaControlsRepositoryImpl

    private val testConfig = MediaControlsEntityConfig(serverId = SERVER_ID, entityId = ENTITY_ID)
    private val storedConfig = MediaControlsConfig(serverId = SERVER_ID, entityId = ENTITY_ID)

    @BeforeEach
    fun setUp() {
        repository = MediaControlsRepositoryImpl(dao = dao)
    }

    @Test
    fun `Given stored configs when getting them then they are mapped`() = runTest {
        coEvery { dao.getAll() } returns listOf(storedConfig)

        assertEquals(listOf(testConfig), repository.getEntities())
    }

    @Test
    fun `Given stored configs when observing them then they are mapped`() = runTest {
        every { dao.getAllFlow() } returns flowOf(listOf(storedConfig))

        repository.observeEntities().test {
            assertEquals(listOf(testConfig), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `Given a config when adding it then it is inserted`() = runTest {
        repository.addEntity(testConfig)

        coVerify { dao.insert(storedConfig) }
    }

    @Test
    fun `Given a config when removing it then it is deleted`() = runTest {
        repository.removeEntity(testConfig)

        coVerify { dao.delete(storedConfig) }
    }
}
