package io.homeassistant.companion.android.changelog

import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

private const val CURRENT_VERSION_CODE = 42
private const val SENSOR_ID = "test_sensor"
private const val FULL_FLAVOR = "full"
private const val MINIMAL_FLAVOR = "minimal"

private val sensorEntry = ChangelogEntry(
    contentRes = commonR.string.changelog_entry_bug_fixes,
    platforms = setOf(ChangelogPlatform.APP),
    action = ChangelogAction.OpenSensor(SENSOR_ID),
)
private val plainEntry = ChangelogEntry(
    contentRes = commonR.string.changelog_entry_bug_fixes,
    platforms = setOf(ChangelogPlatform.APP),
)
private val fullFlavorEntry = plainEntry.copy(requiresFlavor = ChangelogFlavor.FULL)
private val minimalFlavorEntry = plainEntry.copy(requiresFlavor = ChangelogFlavor.MINIMAL)

/** A fixed changelog so release-note edits never change what these tests exercise. */
private val fixtureChangelog = Changelog(new = listOf(sensorEntry), fixed = listOf(plainEntry))

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
    fun `Given version with the suffix of another flavor when reading state then version name is unchanged`() = runTest {
        val appVersion = AppVersion("2026.7.6-$MINIMAL_FLAVOR", CURRENT_VERSION_CODE)

        val state = resolvedState(createViewModel(flavor = FULL_FLAVOR, appVersion = appVersion))

        assertEquals("2026.7.6-$MINIMAL_FLAVOR", state.versionName)
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
        assertEquals(fixtureChangelog.toSections(), state.sections)
    }

    @Test
    fun `Given device without the sensor when reading state then the sensor entry loses its action`() = runTest {
        val state = resolvedState(createViewModel(sensorManagers = setOf(sensorManager(hasSensor = false))))

        assertEquals(
            Changelog(new = listOf(sensorEntry.copy(action = null)), fixed = listOf(plainEntry)).toSections(),
            state.sections,
        )
    }

    @Test
    fun `Given device with the sensor when reading state then the sensor entry keeps its action`() = runTest {
        val state = resolvedState(createViewModel(sensorManagers = setOf(sensorManager(hasSensor = true))))

        assertEquals(fixtureChangelog.toSections(), state.sections)
    }

    @Test
    fun `Given changelog without sensor action when reading state then sensor managers are not queried`() = runTest {
        val sensorManager = sensorManager(hasSensor = true)
        val changelog = Changelog(fixed = listOf(plainEntry))

        val state = resolvedState(createViewModel(sensorManagers = setOf(sensorManager), changelog = changelog))

        assertEquals(changelog.toSections(), state.sections)
        coVerify(exactly = 0) { sensorManager.hasBasicSensor(any()) }
    }

    @Test
    fun `Given full flavor when reading state then only entries of other flavors and emptied sections are dropped`() = runTest {
        val changelog = Changelog(new = listOf(minimalFlavorEntry), fixed = listOf(plainEntry, fullFlavorEntry, minimalFlavorEntry))

        val state = resolvedState(createViewModel(flavor = FULL_FLAVOR, changelog = changelog))

        assertEquals(Changelog(fixed = listOf(plainEntry, fullFlavorEntry)).toSections(), state.sections)
    }

    @Test
    fun `Given minimal flavor when reading state then only entries of other flavors and emptied sections are dropped`() = runTest {
        val changelog = Changelog(new = listOf(fullFlavorEntry), fixed = listOf(plainEntry, fullFlavorEntry, minimalFlavorEntry))

        val state = resolvedState(createViewModel(flavor = MINIMAL_FLAVOR, changelog = changelog))

        assertEquals(Changelog(fixed = listOf(plainEntry, minimalFlavorEntry)).toSections(), state.sections)
    }

    private fun TestScope.createViewModel(
        isAutomotive: Boolean = false,
        flavor: String = FULL_FLAVOR,
        appVersion: AppVersion = AppVersion("2026.7.6-$flavor", CURRENT_VERSION_CODE),
        sensorManagers: Set<SensorManager> = setOf(sensorManager(hasSensor = true)),
        changelog: Changelog = fixtureChangelog,
    ) = ChangelogViewModel(
        prefsRepository = prefsRepository,
        isAutomotive = isAutomotive,
        flavor = flavor,
        appVersion = appVersion,
        sensorManagers = sensorManagers,
        changelog = changelog,
        backgroundDispatcher = StandardTestDispatcher(testScheduler),
    )

    /** A manager owning the sensor targeted by the fixture changelog, present on the device or not. */
    private fun sensorManager(hasSensor: Boolean) = mockk<SensorManager> {
        coEvery { hasBasicSensor(SENSOR_ID) } returns hasSensor
    }

    /** The content once the view model resolved it, after letting the test scheduler run. */
    private fun TestScope.resolvedState(viewModel: ChangelogViewModel): ChangelogUiState.Content {
        advanceUntilIdle()
        return assertInstanceOf(ChangelogUiState.Content::class.java, viewModel.uiState.value)
    }
}
