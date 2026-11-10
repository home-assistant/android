package io.homeassistant.companion.android.changelog

import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

private const val CURRENT_VERSION_CODE = 42

@ExtendWith(MainDispatcherJUnit5Extension::class)
class ChangelogViewModelTest {

    private val prefsRepository: PrefsRepository = mockk(relaxUnitFun = true)

    @Test
    fun `Given creation when reading state before resolution then state is loading`() = runTest {
        assertEquals(ChangelogUiState.Loading, createViewModel().uiState.value)
    }

    @Test
    fun `Given creation when initializing then marks changelog seen with the version code`() = runTest {
        createViewModel()
        advanceUntilIdle()

        coVerify { prefsRepository.markChangelogSeen(CURRENT_VERSION_CODE) }
    }

    @Test
    fun `Given current flavor version when reading state then flavor suffix is stripped and release url built`() = runTest {
        val state = resolvedState(createViewModel())

        assertEquals("2026.7.6", state.versionName)
        assertEquals("https://github.com/home-assistant/android/releases/tag/2026.7.6", state.releaseUrl)
    }

    @Test
    fun `Given version without flavor suffix when reading state then version name is unchanged`() = runTest {
        val state = resolvedState(createViewModel(appVersion = AppVersion("2026.7.6", CURRENT_VERSION_CODE)))

        assertEquals("2026.7.6", state.versionName)
    }

    @ParameterizedTest
    @EnumSource(ChangelogPlatform::class, names = ["APP", "AUTOMOTIVE"])
    fun `Given specific platform when reading state then current platform is the specific platform with the authored content`(platform: ChangelogPlatform) = runTest {
        val state = resolvedState(createViewModel(isAutomotive = platform == ChangelogPlatform.AUTOMOTIVE))

        assertEquals(platform, state.currentPlatform)
        assertEquals(currentChangelog.toSections(), state.sections)
    }

    @Test
    fun `Given device without the sensor when reading state then sensor actions are dropped`() = runTest {
        val state = resolvedState(createViewModel(sensorManagers = setOf(sensorManager(hasSensor = false))))

        assertTrue(currentChangelog.toSections().sensorActions().isNotEmpty())
        assertEquals(emptyList<ChangelogAction.OpenSensor>(), state.sections.sensorActions())
    }

    @Test
    fun `Given device with the sensor when reading state then sensor actions are kept`() = runTest {
        val state = resolvedState(createViewModel(sensorManagers = setOf(sensorManager(hasSensor = true))))

        assertEquals(currentChangelog.toSections().sensorActions(), state.sections.sensorActions())
    }

    private fun createViewModel(
        isAutomotive: Boolean = false,
        appVersion: AppVersion = AppVersion("2026.7.6-${BuildConfig.FLAVOR}", CURRENT_VERSION_CODE),
        sensorManagers: Set<SensorManager> = setOf(sensorManager(hasSensor = true)),
    ) = ChangelogViewModel(prefsRepository, isAutomotive, appVersion, sensorManagers)

    /** A manager owning every sensor targeted by the authored changelog, present on the device or not. */
    private fun sensorManager(hasSensor: Boolean) = mockk<SensorManager> {
        every { hasSensor() } returns hasSensor
        coEvery { getAvailableSensors() } returns currentChangelog.toSections().sensorActions()
            .map { action -> mockk<SensorManager.BasicSensor> { every { id } returns action.sensorId } }
    }

    /** The content once the view model resolved it, after letting the test scheduler run. */
    private fun TestScope.resolvedState(viewModel: ChangelogViewModel): ChangelogUiState.Content {
        advanceUntilIdle()
        return assertInstanceOf(ChangelogUiState.Content::class.java, viewModel.uiState.value)
    }

    private fun List<ChangelogSection>.sensorActions() = flatMap { it.entries }
        .map { it.action }
        .filterIsInstance<ChangelogAction.OpenSensor>()
}
