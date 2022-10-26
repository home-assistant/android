package io.homeassistant.companion.android.common.data.prefs

import io.homeassistant.companion.android.common.data.LocalStorageMock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WearPrefsRepositoryTest {

    private lateinit var prefsRepository: WearPrefsRepository

    @Before
    fun setup() {
        prefsRepository = WearPrefsRepositoryImpl(
            localStorage = LocalStorageMock(),
            integrationStorage = LocalStorageMock()
        )
    }

    @Test
    fun templateTileContentTest() = runTest {
        assertEquals("", prefsRepository.getTemplateTileContent())

        prefsRepository.setTemplateTileContent("TEST")
        assertEquals("TEST", prefsRepository.getTemplateTileContent())
    }

    @Test
    fun showShortcutTextTest() = runTest {
        assertFalse(prefsRepository.getShowShortcutText())

        prefsRepository.setShowShortcutTextEnabled(true)
        assertTrue(prefsRepository.getShowShortcutText())
    }

    @Test
    fun templateTileRefreshIntervalTest() = runTest {
        assertEquals(0, prefsRepository.getTemplateTileRefreshInterval())

        prefsRepository.setTemplateTileRefreshInterval(1000)
        assertEquals(1000, prefsRepository.getTemplateTileRefreshInterval())
    }

    @Test
    fun wearHapticFeedbackTest() = runTest {
        assertFalse(prefsRepository.getWearHapticFeedback())

        prefsRepository.setWearHapticFeedback(true)
        assertTrue(prefsRepository.getWearHapticFeedback())
    }

    @Test
    fun wearToastConfirmationTest() = runTest {
        assertFalse(prefsRepository.getWearToastConfirmation())

        prefsRepository.setWearToastConfirmation(true)
        assertTrue(prefsRepository.getWearToastConfirmation())
    }

    @Test
    fun wearFavoritesOnlyTest() = runTest {
        assertFalse(prefsRepository.getWearFavoritesOnly())

        prefsRepository.setWearFavoritesOnly(true)
        assertTrue(prefsRepository.getWearFavoritesOnly())
    }
}
