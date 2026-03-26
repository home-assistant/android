package io.homeassistant.companion.android.common.data.prefs

import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.HAGesture
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class PrefsRepositoryImplTest {
    private val localStorage = mockk<LocalStorage>()
    private val integrationStorage = mockk<LocalStorage>()

    private lateinit var repository: PrefsRepositoryImpl

    @BeforeEach
    fun setup() {
        coEvery { localStorage.getInt(MIGRATION_PREF) } returns MIGRATION_VERSION
        repository = PrefsRepositoryImpl(localStorage = localStorage, integrationStorage = integrationStorage)
    }

    @ParameterizedTest
    @CsvSource(
        "SWIPE_UP_THREE,SERVER_LIST",
        "SWIPE_DOWN_THREE,QUICKBAR_DEFAULT",
        "SWIPE_LEFT_THREE,SERVER_PREVIOUS",
        "SWIPE_RIGHT_THREE,SERVER_NEXT",
    )
    fun `Given gesture with default action when getting pref then default action is returned`(
        gestureName: String,
        actionName: String,
    ) = runTest {
        coEvery { localStorage.getString("gesture_action_$gestureName") } returns null

        val result = repository.getGestureAction(HAGesture.valueOf(gestureName))

        assertEquals(GestureAction.valueOf(actionName), result)
    }

    @Test
    fun `Given user customized gesture action when getting pref then user action is returned`() = runTest {
        coEvery { localStorage.getString("gesture_action_SWIPE_LEFT_THREE") } returns "QUICKBAR_DEFAULT"

        val result = repository.getGestureAction(HAGesture.valueOf("SWIPE_LEFT_THREE"))

        assertEquals(GestureAction.QUICKBAR_DEFAULT, result)
    }

    @ParameterizedTest
    @ValueSource(strings = ["SWIPE_UP_TWO", "SWIPE_DOWN_TWO", "SWIPE_LEFT_TWO", "SWIPE_RIGHT_TWO"])
    fun `Given gesture with no default action when getting pref then none action is returned`(
        gestureName: String,
    ) = runTest {
        coEvery { localStorage.getString("gesture_action_$gestureName") } returns null

        val result = repository.getGestureAction(HAGesture.valueOf(gestureName))

        assertEquals(GestureAction.NONE, result)
    }

    @Test
    fun `Given no preference set when checking change log popup enabled then default is true`() = runTest {
        coEvery { localStorage.getBooleanOrNull("change_log_popup_enabled") } returns null

        val result = repository.isChangeLogPopupEnabled()

        assertEquals(true, result)
    }

    @Test
    fun `Given user sets change log popup enabled to true when retrieving then value is true`() = runTest {
        coEvery { localStorage.putBoolean("change_log_popup_enabled", true) } returns Unit
        coEvery { localStorage.getBooleanOrNull("change_log_popup_enabled") } returns true
        repository.setChangeLogPopupEnabled(true)

        val result = repository.isChangeLogPopupEnabled()

        assertEquals(true, result)
    }

    @Test
    fun `Given user sets change log popup enabled to false when retrieving then value is false`() = runTest {
        coEvery { localStorage.putBoolean("change_log_popup_enabled", false) } returns Unit
        coEvery { localStorage.getBooleanOrNull("change_log_popup_enabled") } returns false
        repository.setChangeLogPopupEnabled(false)

        val result = repository.isChangeLogPopupEnabled()

        assertEquals(false, result)
    }

    @Test
    fun `Given migration already at current version when accessing prefs multiple times then migration check only runs once`() = runTest {
        // This test verifies the fix where migrationChecked.set(true) is called
        // even when migration is not needed to prevent repeated migration checks

        // Setup - migration already at current version, no migration needed
        coEvery { localStorage.getInt(MIGRATION_PREF) } returns MIGRATION_VERSION
        coEvery { localStorage.getString(any()) } returns "test_value"

        // Execute multiple calls
        repository.getAppVersion()
        repository.getCurrentLang()
        repository.getControlsAuthRequired()

        // Verify migration version check only happened once
        // This confirms migrationChecked.set(true) was called after first check
        // even though no actual migration was performed
        coVerify(exactly = 1) { localStorage.getInt(MIGRATION_PREF) }

        // Verify no integration storage was accessed since no migration was needed
        coVerify(exactly = 0) { integrationStorage.getString(any()) }
    }

    @Nested
    inner class MigrationV2Test {

        @Test
        fun `Given version 1 prefs with single entity when migrating then entity written as list and old keys removed`() = runTest {
            // Simulate a user at migration version 1 who had a single entity configured
            coEvery { localStorage.getInt(MIGRATION_PREF) } returns 1
            coEvery { localStorage.getInt("media_control_server_id") } returns 42
            coEvery { localStorage.getString("media_control_entity_id") } returns "media_player.tv"
            coEvery { localStorage.getString("media_control_entities") } returns null
            coEvery { localStorage.putString(any(), any()) } returns Unit
            coEvery { localStorage.remove(any()) } returns Unit
            coEvery { localStorage.putInt(any(), any()) } returns Unit

            // Trigger migration by accessing the repository
            repository.getMediaControlEntities()

            val expectedJson = Json.encodeToString(listOf(MediaControlEntityConfig(serverId = 42, entityId = "media_player.tv")))
            coVerify { localStorage.putString("media_control_entities", expectedJson) }
            coVerify { localStorage.remove("media_control_server_id") }
            coVerify { localStorage.remove("media_control_entity_id") }
            coVerify { localStorage.putInt(MIGRATION_PREF, 2) }
        }

        @Test
        fun `Given version 1 prefs with no entity configured when migrating then empty list written`() = runTest {
            coEvery { localStorage.getInt(MIGRATION_PREF) } returns 1
            coEvery { localStorage.getInt("media_control_server_id") } returns null
            coEvery { localStorage.getString("media_control_entity_id") } returns null
            coEvery { localStorage.getString("media_control_entities") } returns null
            coEvery { localStorage.putString(any(), any()) } returns Unit
            coEvery { localStorage.remove(any()) } returns Unit
            coEvery { localStorage.putInt(any(), any()) } returns Unit

            repository.getMediaControlEntities()

            val expectedJson = Json.encodeToString(emptyList<MediaControlEntityConfig>())
            coVerify { localStorage.putString("media_control_entities", expectedJson) }
            coVerify { localStorage.putInt(MIGRATION_PREF, 2) }
        }
    }

    @Nested
    inner class MediaControlEntitiesTest {

        @Test
        fun `Given no stored entities when getMediaControlEntities called then empty list returned`() = runTest {
            coEvery { localStorage.getString("media_control_entities") } returns null

            val result = repository.getMediaControlEntities()

            assertEquals(emptyList<MediaControlEntityConfig>(), result)
        }

        @Test
        fun `Given stored entities when getMediaControlEntities called then list returned`() = runTest {
            val entities = listOf(
                MediaControlEntityConfig(serverId = 1, entityId = "media_player.tv"),
                MediaControlEntityConfig(serverId = 2, entityId = "media_player.radio"),
            )
            coEvery { localStorage.getString("media_control_entities") } returns Json.encodeToString(entities)

            val result = repository.getMediaControlEntities()

            assertEquals(entities, result)
        }

        @Test
        fun `Given entities when setMediaControlEntities called then serialized to prefs`() = runTest {
            val entities = listOf(MediaControlEntityConfig(serverId = 1, entityId = "media_player.tv"))
            coEvery { localStorage.putString(any(), any()) } returns Unit

            repository.setMediaControlEntities(entities)

            coVerify { localStorage.putString("media_control_entities", Json.encodeToString(entities)) }
        }
    }
}
