package io.homeassistant.companion.android.settings.gestures

import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.GesturePointers
import io.homeassistant.companion.android.common.util.HAGesture
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class)
class GesturesViewModelTest {

    private val prefsRepository: PrefsRepository = mockk()
    private val savedActions = mutableMapOf(
        HAGesture.SWIPE_LEFT_ONE_EDGE to GestureAction.QUICKBAR_DEFAULT,
        HAGesture.SWIPE_RIGHT_ONE_EDGE to GestureAction.NAVIGATE_DASHBOARD,
        HAGesture.SWIPE_LEFT_THREE to GestureAction.SERVER_PREVIOUS,
    )
    private val multiFingerGestures = HAGesture.entries.filter { it.pointers != GesturePointers.ONE }

    @BeforeEach
    fun setup() {
        coEvery { prefsRepository.getGestureAction(any()) } answers {
            savedActions[firstArg<HAGesture>()] ?: GestureAction.NONE
        }
        coEvery { prefsRepository.setGestureAction(any(), any()) } answers {
            savedActions[firstArg<HAGesture>()] = secondArg<GestureAction>()
        }
    }

    @Test
    fun `Given no inset information when settings are created then only multi finger gestures are available`() = runTest {
        val viewModel = GesturesViewModel(prefsRepository)

        assertEquals(multiFingerGestures, viewModel.availableGestures)
        advanceUntilIdle()

        assertEquals(multiFingerGestures, viewModel.availableGestures)
        assertEquals(GestureAction.QUICKBAR_DEFAULT, viewModel.gestureActions[HAGesture.SWIPE_LEFT_ONE_EDGE])
        assertEquals(GestureAction.NAVIGATE_DASHBOARD, viewModel.gestureActions[HAGesture.SWIPE_RIGHT_ONE_EDGE])
        coVerify(exactly = 0) { prefsRepository.setGestureAction(any(), any()) }
    }

    @ParameterizedTest
    @CsvSource(
        "false,false,true,true",
        "true,true,false,false",
        "true,false,true,false",
        "false,true,false,true",
    )
    fun `Given reserved system edges when availability changes then only the corresponding inward swipes are hidden`(
        leftEdgeReserved: Boolean,
        rightEdgeReserved: Boolean,
        swipeLeftAvailable: Boolean,
        swipeRightAvailable: Boolean,
    ) = runTest {
        val viewModel = GesturesViewModel(prefsRepository)
        advanceUntilIdle()

        viewModel.updateGestureAvailability(
            leftEdgeReserved = leftEdgeReserved,
            rightEdgeReserved = rightEdgeReserved,
        )

        assertEquals(swipeLeftAvailable, HAGesture.SWIPE_LEFT_ONE_EDGE in viewModel.availableGestures)
        assertEquals(swipeRightAvailable, HAGesture.SWIPE_RIGHT_ONE_EDGE in viewModel.availableGestures)
        assertEquals(multiFingerGestures, viewModel.availableGestures.filter { it.pointers != GesturePointers.ONE })
        assertEquals(
            multiFingerGestures.size + listOf(swipeLeftAvailable, swipeRightAvailable).count { it },
            viewModel.availableGestures.size,
        )
        coVerify(exactly = 0) { prefsRepository.setGestureAction(any(), any()) }
    }

    @Test
    fun `Given a customized edge action when navigation mode changes then its saved action survives hiding and restoring the gesture`() = runTest {
        val viewModel = GesturesViewModel(prefsRepository)
        advanceUntilIdle()
        viewModel.updateGestureAvailability(leftEdgeReserved = false, rightEdgeReserved = false)
        viewModel.setGestureAction(HAGesture.SWIPE_LEFT_ONE_EDGE, GestureAction.SHOW_SIDEBAR)
        advanceUntilIdle()
        val customizedActions = viewModel.gestureActions.toMap()

        viewModel.updateGestureAvailability(leftEdgeReserved = true, rightEdgeReserved = true)
        assertEquals(multiFingerGestures, viewModel.availableGestures)
        assertEquals(customizedActions, viewModel.gestureActions.toMap())
        viewModel.updateGestureAvailability(leftEdgeReserved = true, rightEdgeReserved = false)
        viewModel.updateGestureAvailability(leftEdgeReserved = false, rightEdgeReserved = true)
        viewModel.updateGestureAvailability(leftEdgeReserved = false, rightEdgeReserved = false)

        assertEquals(HAGesture.entries.toList(), viewModel.availableGestures)
        assertEquals(customizedActions, viewModel.gestureActions.toMap())
        assertEquals(GestureAction.SHOW_SIDEBAR, savedActions[HAGesture.SWIPE_LEFT_ONE_EDGE])
        assertEquals(GestureAction.NAVIGATE_DASHBOARD, savedActions[HAGesture.SWIPE_RIGHT_ONE_EDGE])
        assertEquals(GestureAction.SERVER_PREVIOUS, savedActions[HAGesture.SWIPE_LEFT_THREE])
        coVerify(exactly = 1) { prefsRepository.setGestureAction(HAGesture.SWIPE_LEFT_ONE_EDGE, GestureAction.SHOW_SIDEBAR) }
        coVerify(exactly = 1) { prefsRepository.setGestureAction(any(), any()) }
    }
}
