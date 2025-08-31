package io.homeassistant.companion.android.common.data.prefs

import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepositoryImpl.Companion.MIGRATION_PREF
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepositoryImpl.Companion.MIGRATION_VERSION
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepositoryImpl.Companion.PREF_TILE_TEMPLATES
import io.homeassistant.companion.android.common.data.prefs.impl.entities.TemplateTileConfig
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WearPrefsRepositoryImplTest {
    private val localStorage = mockk<LocalStorage>()
    private val integrationStorage = mockk<LocalStorage>()

    private lateinit var repository: WearPrefsRepositoryImpl

    fun initRepository(version: Int? = MIGRATION_VERSION) {
        coEvery { localStorage.getInt(MIGRATION_PREF) } returns version
        repository = WearPrefsRepositoryImpl(localStorage = localStorage, integrationStorage = integrationStorage)
    }

    @Test
    fun `Given template for tile when storing then it properly serialize it into the local storage`() = runTest {
        initRepository()
        val slot = slot<String>()
        coJustRun { localStorage.putString(PREF_TILE_TEMPLATES, capture(slot)) }

        repository.setAllTemplateTiles(
            mapOf(
                1 to TemplateTileConfig("template", 10),
                2 to TemplateTileConfig("template2", 20),
            ),
        )
        assertEquals("""{"1":{"template":"template","refresh_interval":10},"2":{"template":"template2","refresh_interval":20}}""".trimIndent(), slot.captured)
    }

    @Test
    fun `Given json for tile when retrieving it then it properly deserialize it from the local storage`() = runTest {
        initRepository()
        coEvery { localStorage.getString(PREF_TILE_TEMPLATES) } returns """{"1":{"template":"template","refresh_interval":10},"2":{"template":"template2","refresh_interval":20}}""".trimIndent()

        val result = repository.getAllTemplateTiles()

        assertEquals(
            mapOf(
                1 to TemplateTileConfig("template", 10),
                2 to TemplateTileConfig("template2", 20),
            ),
            result,
        )
    }
}
