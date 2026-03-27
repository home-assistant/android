package io.homeassistant.companion.android.common.data.prefs

import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.HAGesture
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
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
}
