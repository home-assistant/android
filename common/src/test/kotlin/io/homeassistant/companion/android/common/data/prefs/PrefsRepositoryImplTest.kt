package io.homeassistant.companion.android.common.data.prefs

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.HAGesture
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(ConsoleLogExtension::class)
class PrefsRepositoryImplTest {
    private val keyChangesFlow = MutableSharedFlow<String>()
    private val mapperSlot = slot<suspend () -> Any>()
    private val localStorage = mockk<LocalStorage> {
        every { observeChanges(*anyVararg<String>()) } returns keyChangesFlow
        coEvery {
            observeChanges(*anyVararg<String>(), mapper = capture(mapperSlot))
        } answers {
            merge(keyChangesFlow, flowOf("")).map { mapperSlot.captured.invoke() }
        }
    }
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

        assertTrue(result)
    }

    @Test
    fun `Given user sets change log popup enabled to true when retrieving then value is true`() = runTest {
        coEvery { localStorage.putBoolean("change_log_popup_enabled", true) } returns Unit
        coEvery { localStorage.getBooleanOrNull("change_log_popup_enabled") } returns true
        repository.setChangeLogPopupEnabled(true)

        val result = repository.isChangeLogPopupEnabled()

        assertTrue(result)
    }

    @Test
    fun `Given user sets change log popup enabled to false when retrieving then value is false`() = runTest {
        coEvery { localStorage.putBoolean("change_log_popup_enabled", false) } returns Unit
        coEvery { localStorage.getBooleanOrNull("change_log_popup_enabled") } returns false
        repository.setChangeLogPopupEnabled(false)

        val result = repository.isChangeLogPopupEnabled()

        assertFalse(result)
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

    @Test
    fun `Given collecting flow when autoplay key changes then updated value is emitted`() = runTest {
        coEvery { localStorage.getBoolean("autoplay_video") } returns false

        repository.autoPlayVideoFlow().test {
            assertFalse(awaitItem())

            coEvery { localStorage.getBoolean("autoplay_video") } returns true
            keyChangesFlow.emit("autoplay_video")

            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given collecting flow when full screen changes then updated full screen enabled is emitted`() = runTest {
        coEvery { localStorage.getBoolean("fullscreen_enabled") } returns true

        repository.fullScreenEnabledFlow().test {
            assertTrue(awaitItem())

            coEvery { localStorage.getBoolean("fullscreen_enabled") } returns false
            keyChangesFlow.emit("fullscreen_enabled")

            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Nested
    inner class ZoomSettingsFlow {

        @Test
        fun `Given collecting flow when zoom key changes then updated settings are emitted`() = runTest {
            coEvery { localStorage.getInt("page_zoom_level") } returns 100
            coEvery { localStorage.getBoolean("pinch_to_zoom_enabled") } returns false

            repository.zoomSettingsFlow().test {
                awaitItem() // consume initial emission

                coEvery { localStorage.getInt("page_zoom_level") } returns 200
                keyChangesFlow.emit("page_zoom_level")

                val updated = awaitItem()
                assertEquals(200, updated.zoomLevel)
                assertFalse(updated.pinchToZoomEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given collecting flow when pinch to zoom key changes then updated settings are emitted`() = runTest {
            coEvery { localStorage.getInt("page_zoom_level") } returns 100
            coEvery { localStorage.getBoolean("pinch_to_zoom_enabled") } returns false

            repository.zoomSettingsFlow().test {
                awaitItem() // consume initial emission

                coEvery { localStorage.getBoolean("pinch_to_zoom_enabled") } returns true
                keyChangesFlow.emit("pinch_to_zoom_enabled")

                val updated = awaitItem()
                assertEquals(100, updated.zoomLevel)
                assertTrue(updated.pinchToZoomEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
