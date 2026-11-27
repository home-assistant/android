package io.homeassistant.companion.android.common.data.prefs

import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepositoryImpl.Companion.MIGRATION_PREF
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepositoryImpl.Companion.MIGRATION_VERSION
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepositoryImpl.Companion.PREF_TILE_TEMPLATES
import io.homeassistant.companion.android.common.data.prefs.impl.entities.TemplateTileConfig
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `Given migration already at current version when accessing prefs multiple times then migration check only runs once`() = runTest {
        // Migration is already current: the lazy check should run once on first access and never
        // touch the integration storage, and subsequent accesses must skip it via the fast path.
        initRepository(version = MIGRATION_VERSION)
        coEvery { localStorage.getBoolean(any()) } returns true

        repository.getShowShortcutText()
        repository.getWearHapticFeedback()
        repository.getWearToastConfirmation()

        // The migration version is read only on the first access, not on every call.
        coVerify(exactly = 1) { localStorage.getInt(MIGRATION_PREF) }
        // No migration was needed, so the integration storage was never consulted.
        coVerify(exactly = 0) { integrationStorage.getString(any()) }
    }

    @Test
    fun `Given missing invitation preference when reading then it is not acknowledged`() = runTest {
        initRepository()
        coEvery { localStorage.getBooleanOrNull("thermostat_layout_invitation_1") } returns null
        assertFalse(repository.isThermostatLayoutInvitationAcknowledged(1))
    }

    @Test
    fun `Given two tiles when acknowledging one invitation then the other remains unacknowledged`() = runTest {
        initRepository()
        val preferences = mutableMapOf<String, Boolean>()
        coEvery { localStorage.putBoolean(any(), any()) } coAnswers {
            preferences[firstArg()] = secondArg()
        }
        coEvery { localStorage.getBooleanOrNull(any()) } coAnswers { preferences[firstArg()] }
        repository.setThermostatLayoutInvitationAcknowledged(1, true)
        assertTrue(repository.isThermostatLayoutInvitationAcknowledged(1))
        assertFalse(repository.isThermostatLayoutInvitationAcknowledged(2))
        coVerify(exactly = 1) { localStorage.putBoolean("thermostat_layout_invitation_1", true) }
    }

    @Test
    fun `Given acknowledged invitation when clearing it then the key is removed`() = runTest {
        initRepository()
        var acknowledged: Boolean? = true
        coEvery { localStorage.getBooleanOrNull("thermostat_layout_invitation_1") } coAnswers { acknowledged }
        coEvery { localStorage.remove("thermostat_layout_invitation_1") } coAnswers { acknowledged = null }
        assertTrue(repository.isThermostatLayoutInvitationAcknowledged(1))
        repository.setThermostatLayoutInvitationAcknowledged(1, false)
        coVerify(exactly = 1) { localStorage.remove("thermostat_layout_invitation_1") }
        assertFalse(repository.isThermostatLayoutInvitationAcknowledged(1))
    }
}
