package io.homeassistant.companion.android.modes

import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.modes.HomeAssistantModeManager
import io.homeassistant.companion.android.modes.HomeAssistantModeSwitch.Action
import io.homeassistant.companion.android.modes.HomeAssistantModeSwitch.Display
import io.homeassistant.companion.android.modes.HomeAssistantModeSwitch.Update
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

private val ADDED = Display(checked = true, summary = commonR.string.home_assistant_mode_summary)
private val NOT_ADDED = Display(checked = false, summary = commonR.string.home_assistant_mode_summary)
private val NO_ACCESS = Display(checked = false, summary = commonR.string.home_assistant_mode_summary_no_access)

class HomeAssistantModeSwitchTest {

    private var hasAccess = true
    private var added = false
    private var androidAccepts = true

    private val homeAssistantModeManager = mockk<HomeAssistantModeManager> {
        coEvery { hasAccess() } answers { hasAccess }
        coEvery { getState() } answers { stateInAndroid() }
        coEvery { add() } answers {
            if (hasAccess && androidAccepts) added = true
            hasAccess && added
        }
        coEvery { remove() } answers {
            if (hasAccess && androidAccepts) added = false
            hasAccess && !added
        }
    }

    private val modeSwitch = HomeAssistantModeSwitch(homeAssistantModeManager)

    private fun stateInAndroid(): HomeAssistantModeManager.State? = if (hasAccess) {
        HomeAssistantModeManager.State(added = added, enabled = added, active = false)
    } else {
        null
    }

    /**
     * Makes the next read of the mode's state wait for [read], and completes [reading] once it started. The reads
     * after it answer right away.
     */
    private fun delayNextStateRead(
        reading: CompletableDeferred<Unit>,
        read: CompletableDeferred<HomeAssistantModeManager.State?>,
    ) {
        coEvery { homeAssistantModeManager.getState() } coAnswers {
            reading.complete(Unit)
            read.await()
        } andThenAnswer { stateInAndroid() }
    }

    /** Changes the switch without access, then comes back to the settings after granting access or not. */
    private suspend fun requestAccessThenReturn(add: Boolean, grant: Boolean) {
        hasAccess = false
        assertEquals(Update(display = null, action = Action.RequestAccess), modeSwitch.onSwitchChanged(add))
        hasAccess = grant
        modeSwitch.onResume()
    }

    @Test
    fun `Given access when turning the switch on then the mode is added`() = runTest {
        assertEquals(Update(ADDED), modeSwitch.onSwitchChanged(add = true))
    }

    @Test
    fun `Given added mode when turning the switch off then the mode is removed`() = runTest {
        added = true

        assertEquals(Update(NOT_ADDED), modeSwitch.onSwitchChanged(add = false))
    }

    @Test
    fun `Given access requested when unlocked after returning with access then the mode is added once`() = runTest {
        requestAccessThenReturn(add = true, grant = true)

        assertEquals(Update(ADDED), modeSwitch.onUnlocked())
        assertNull(modeSwitch.onUnlocked())
        coVerify(exactly = 1) { homeAssistantModeManager.add() }
    }

    @Test
    fun `Given added mode and no access when turning the switch off then access is requested, then the mode removed`() = runTest {
        added = true
        requestAccessThenReturn(add = false, grant = true)

        assertEquals(Update(NOT_ADDED), modeSwitch.onUnlocked())
        coVerify(exactly = 0) { homeAssistantModeManager.add() }
    }

    @Test
    fun `Given access requested when resuming locked then nothing changes until unlocked`() = runTest {
        requestAccessThenReturn(add = true, grant = true)

        coVerify(exactly = 0) { homeAssistantModeManager.add() }
        assertEquals(Update(ADDED), modeSwitch.onUnlocked())
    }

    @Test
    fun `Given access requested when unlocked before leaving for the access screen then the request is kept`() = runTest {
        hasAccess = false
        modeSwitch.onSwitchChanged(add = true)

        assertNull(modeSwitch.onUnlocked())

        hasAccess = true
        modeSwitch.onResume()
        assertEquals(Update(ADDED), modeSwitch.onUnlocked())
    }

    @Test
    fun `Given access requested when returning without access then the request is dropped`() = runTest {
        requestAccessThenReturn(add = true, grant = false)

        assertEquals(Update(NO_ACCESS), modeSwitch.onUnlocked())
        hasAccess = true
        modeSwitch.onResume()
        assertNull(modeSwitch.onUnlocked())
        coVerify(exactly = 0) { homeAssistantModeManager.add() }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Given a request saved with the screen when restored and unlocked after returning then it is applied`(add: Boolean) = runTest {
        added = !add
        hasAccess = false
        modeSwitch.onSwitchChanged(add)
        val recreated = HomeAssistantModeSwitch(homeAssistantModeManager)
        recreated.restore(modeSwitch.savedState)
        hasAccess = true

        recreated.onResume()

        assertEquals(Update(if (add) ADDED else NOT_ADDED), recreated.onUnlocked())
    }

    @Test
    fun `Given access requested when the screen is recreated while access is checked then the request is kept`() = runTest {
        requestAccessThenReturn(add = true, grant = true)
        val checkingAccess = CompletableDeferred<Unit>()
        coEvery { homeAssistantModeManager.hasAccess() } coAnswers {
            checkingAccess.complete(Unit)
            awaitCancellation()
        }
        val unlocked = launch { modeSwitch.onUnlocked() }
        checkingAccess.await()

        unlocked.cancelAndJoin()
        val recreated = HomeAssistantModeSwitch(homeAssistantModeManager)
        recreated.restore(modeSwitch.savedState)
        coEvery { homeAssistantModeManager.hasAccess() } answers { hasAccess }
        recreated.onResume()

        assertEquals(Update(ADDED), recreated.onUnlocked())
    }

    @Test
    fun `Given access granted when leaving the settings while the mode is added then it is added on return`() = runTest {
        requestAccessThenReturn(add = true, grant = true)
        val adding = CompletableDeferred<Unit>()
        coEvery { homeAssistantModeManager.add() } coAnswers {
            adding.complete(Unit)
            awaitCancellation()
        } andThenAnswer {
            added = true
            true
        }
        val unlocked = launch { modeSwitch.onUnlocked() }
        adding.await()

        unlocked.cancelAndJoin()
        modeSwitch.onResume()

        assertEquals(Update(ADDED), modeSwitch.onUnlocked())
        assertNull(modeSwitch.onUnlocked())
    }

    @Test
    fun `Given the state is still read after returning when unlocked then the change is made and shown after it`() = runTest {
        requestAccessThenReturn(add = true, grant = true)
        val reading = CompletableDeferred<Unit>()
        val read = CompletableDeferred<HomeAssistantModeManager.State?>()
        delayNextStateRead(reading, read)
        val shown = mutableListOf<Update?>()
        val resumed = launch { shown += modeSwitch.onResume() }
        reading.await()
        val unlocked = launch { shown += modeSwitch.onUnlocked() }
        yield()

        coVerify(exactly = 0) { homeAssistantModeManager.add() }
        read.complete(stateInAndroid())
        joinAll(resumed, unlocked)

        assertEquals(listOf(Update(NOT_ADDED), Update(ADDED)), shown)
    }

    @Test
    fun `Given a request is being dropped without access when the switch is changed then the new request is kept`() = runTest {
        requestAccessThenReturn(add = true, grant = false)
        val reading = CompletableDeferred<Unit>()
        val read = CompletableDeferred<HomeAssistantModeManager.State?>()
        delayNextStateRead(reading, read)
        val unlocked = launch { modeSwitch.onUnlocked() }
        reading.await()
        val changed = async { modeSwitch.onSwitchChanged(add = true) }
        yield()

        read.complete(null)
        unlocked.join()

        assertEquals(Update(display = null, action = Action.RequestAccess), changed.await())
        hasAccess = true
        modeSwitch.onResume()
        assertEquals(Update(ADDED), modeSwitch.onUnlocked())
    }

    @Test
    fun `Given no request when saving and restoring then nothing is pending`() = runTest {
        assertNull(modeSwitch.savedState)

        modeSwitch.restore(null)
        modeSwitch.onResume()

        assertNull(modeSwitch.onUnlocked())
    }

    @Test
    fun `Given access requested when the switch is changed directly after returning with access then the request is dropped`() = runTest {
        requestAccessThenReturn(add = true, grant = true)

        modeSwitch.onSwitchChanged(add = true)
        assertEquals(Update(NOT_ADDED), modeSwitch.onSwitchChanged(add = false))

        assertNull(modeSwitch.onUnlocked())
        assertEquals(NOT_ADDED, modeSwitch.onResume().display)
    }

    @Test
    fun `Given access requested when the switch changes again before returning then access is requested once and the change kept`() = runTest {
        hasAccess = false
        assertEquals(Update(display = null, action = Action.RequestAccess), modeSwitch.onSwitchChanged(add = true))

        assertEquals(Update(display = null), modeSwitch.onSwitchChanged(add = true))

        hasAccess = true
        modeSwitch.onResume()
        assertEquals(Update(ADDED), modeSwitch.onUnlocked())
        coVerify(exactly = 1) { homeAssistantModeManager.add() }
    }

    @Test
    fun `Given no access screen when requesting access then the request is dropped with a message`() = runTest {
        hasAccess = false
        modeSwitch.onSwitchChanged(add = true)

        val update = modeSwitch.onAccessScreenUnavailable()

        assertEquals(
            Update(display = null, action = Action.ShowMessage(commonR.string.home_assistant_mode_access_unavailable)),
            update,
        )
        modeSwitch.onResume()
        assertNull(modeSwitch.onUnlocked())
    }

    @Test
    fun `Given added mode when resuming then the switch shows it without changing anything`() = runTest {
        added = true

        assertEquals(Update(ADDED), modeSwitch.onResume())
        coVerify(exactly = 0) { homeAssistantModeManager.add() }
        coVerify(exactly = 0) { homeAssistantModeManager.remove() }
    }

    @Test
    fun `Given added mode and revoked access when resuming then the switch says access is missing`() = runTest {
        added = true
        hasAccess = false

        assertEquals(Update(NO_ACCESS), modeSwitch.onResume())
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Given Android refuses when changing the switch then the matching message is shown`(add: Boolean) = runTest {
        added = !add
        androidAccepts = false

        val update = modeSwitch.onSwitchChanged(add)

        val message = if (add) {
            commonR.string.home_assistant_mode_add_failed
        } else {
            commonR.string.home_assistant_mode_remove_failed
        }
        assertEquals(Update(if (add) NOT_ADDED else ADDED, Action.ShowMessage(message)), update)
    }
}
