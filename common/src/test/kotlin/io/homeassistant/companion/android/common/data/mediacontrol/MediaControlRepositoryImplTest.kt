package io.homeassistant.companion.android.common.data.mediacontrol

import app.cash.turbine.test
import io.homeassistant.companion.android.database.mediacontrol.MediaControlConfig
import io.homeassistant.companion.android.database.mediacontrol.MediaControlDao
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

class MediaControlRepositoryImplTest {

    private val dao: MediaControlDao = mockk(relaxed = true)

    private lateinit var repository: MediaControlRepositoryImpl

    private val testConfig = MediaControlEntityConfig(serverId = SERVER_ID, entityId = ENTITY_ID)
    private val storedConfig = MediaControlConfig(serverId = SERVER_ID, entityId = ENTITY_ID)

    @BeforeEach
    fun setUp() {
        repository = MediaControlRepositoryImpl(dao = dao)
    }

    @Test
    fun `Given stored configs when getting them then they are mapped`() = runTest {
        coEvery { dao.getAll() } returns listOf(storedConfig)

        assertEquals(listOf(testConfig), repository.getConfiguredEntities())
    }

    @Test
    fun `Given stored configs when observing them then they are mapped`() = runTest {
        every { dao.getAllFlow() } returns flowOf(listOf(storedConfig))

        repository.observeConfiguredEntities().test {
            assertEquals(listOf(testConfig), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `Given a config when adding it then it is inserted`() = runTest {
        repository.addConfiguredEntity(testConfig)

        coVerify { dao.insert(storedConfig) }
    }

    @Test
    fun `Given a config when removing it then it is deleted`() = runTest {
        repository.removeConfiguredEntity(testConfig)

        coVerify { dao.delete(storedConfig) }
    }
}
