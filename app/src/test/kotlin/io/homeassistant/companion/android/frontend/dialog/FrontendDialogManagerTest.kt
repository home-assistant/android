package io.homeassistant.companion.android.frontend.dialog

import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(ConsoleLogExtension::class)
class FrontendDialogManagerTest {

    @Test
    fun `Given JS confirm shown when user confirms then suspend returns true and slot clears`() = runTest {
        val manager = FrontendDialogManager()

        val outcome = async { manager.showJsConfirm("Are you sure?") }
        advanceUntilIdle()

        val pending = manager.pendingDialog.value
        assertInstanceOf(FrontendDialog.Confirm::class.java, pending)
        assertEquals("Are you sure?", (pending as FrontendDialog.Confirm).message)
        assertFalse(outcome.isCompleted)

        pending.onConfirm()
        advanceUntilIdle()

        assertEquals(true, outcome.await())
        assertNull(manager.pendingDialog.value)
    }

    @Test
    fun `Given JS confirm shown when user cancels then suspend returns false and slot clears`() = runTest {
        val manager = FrontendDialogManager()

        val outcome = async { manager.showJsConfirm("Discard?") }
        advanceUntilIdle()
        (manager.pendingDialog.value as FrontendDialog.Confirm).onCancel()
        advanceUntilIdle()

        assertEquals(false, outcome.await())
        assertNull(manager.pendingDialog.value)
    }

    @Test
    fun `Given JS confirm in flight when scope cancels then slot is cleared`() = runTest {
        val manager = FrontendDialogManager()

        val outcome = async { manager.showJsConfirm("Confirm?") }
        advanceUntilIdle()
        assertInstanceOf(FrontendDialog.Confirm::class.java, manager.pendingDialog.value)

        outcome.cancel()
        advanceUntilIdle()

        assertNull(manager.pendingDialog.value)
    }

    @Test
    fun `Given dialog shown when second show then it queues until first completes`() = runTest {
        val manager = FrontendDialogManager()

        val first = async { manager.showJsConfirm("first") }
        advanceUntilIdle()
        val second = async { manager.showJsConfirm("second") }
        advanceUntilIdle()

        assertEquals("first", (manager.pendingDialog.value as FrontendDialog.Confirm).message)
        assertFalse(second.isCompleted)

        (manager.pendingDialog.value as FrontendDialog.Confirm).onConfirm()
        advanceUntilIdle()
        assertTrue(first.isCompleted)
        assertEquals("second", (manager.pendingDialog.value as FrontendDialog.Confirm).message)

        (manager.pendingDialog.value as FrontendDialog.Confirm).onCancel()
        advanceUntilIdle()
        assertEquals(false, second.await())
    }
}
