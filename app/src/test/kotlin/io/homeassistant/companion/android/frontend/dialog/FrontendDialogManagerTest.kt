package io.homeassistant.companion.android.frontend.dialog

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class FrontendDialogManagerTest {

    @Test
    fun `Given confirm shown when user confirms then suspend returns true and slot clears`() = runTest {
        val manager = FrontendDialogManager()

        val outcome = async { manager.showConfirm("Are you sure?") }
        advanceUntilIdle()

        val pending = assertInstanceOf(FrontendDialog.Confirm::class.java, manager.pendingDialog.value)
        assertEquals("Are you sure?", pending.message)
        assertFalse(outcome.isCompleted)

        pending.onConfirm()
        advanceUntilIdle()

        assertEquals(true, outcome.await())
        assertNull(manager.pendingDialog.value)
    }

    @Test
    fun `Given confirm shown when user cancels then suspend returns false and slot clears`() = runTest {
        val manager = FrontendDialogManager()

        val outcome = async { manager.showConfirm("Discard?") }
        advanceUntilIdle()
        (manager.pendingDialog.value as FrontendDialog.Confirm).onCancel()
        advanceUntilIdle()

        assertEquals(false, outcome.await())
        assertNull(manager.pendingDialog.value)
    }

    @Test
    fun `Given confirm in flight when scope cancels then slot is cleared`() = runTest {
        val manager = FrontendDialogManager()

        val outcome = async { manager.showConfirm("Confirm?") }
        advanceUntilIdle()
        assertInstanceOf(FrontendDialog.Confirm::class.java, manager.pendingDialog.value)

        outcome.cancel()
        advanceUntilIdle()

        assertNull(manager.pendingDialog.value)
    }

    @Test
    fun `Given dialog shown when second show then it queues until first completes`() = runTest {
        val manager = FrontendDialogManager()

        val first = async { manager.showConfirm("first") }
        advanceUntilIdle()
        val second = async { manager.showConfirm("second") }
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

    @Test
    fun `Given information shown when user dismisses then suspend returns and slot clears`() = runTest {
        val manager = FrontendDialogManager()

        val outcome = async { manager.showInformation("Already paired") }
        advanceUntilIdle()

        val pending = assertInstanceOf(FrontendDialog.Information::class.java, manager.pendingDialog.value)
        assertEquals("Already paired", pending.message)
        assertFalse(outcome.isCompleted)

        pending.onDismiss()
        advanceUntilIdle()

        assertTrue(outcome.isCompleted)
        assertNull(manager.pendingDialog.value)
    }

    @Test
    fun `Given HTTP auth shown when user proceeds then suspend returns Proceed with credentials`() = runTest {
        val manager = FrontendDialogManager()

        val outcome = async {
            manager.showHttpAuth(host = "example.com", message = { "auth required" }, isAuthError = false)
        }
        advanceUntilIdle()

        val pending = assertInstanceOf(FrontendDialog.HttpAuth::class.java, manager.pendingDialog.value)
        pending.onProceed("alice", "s3cret", true)
        advanceUntilIdle()

        val resolved = assertInstanceOf(HttpAuthOutcome.Proceed::class.java, outcome.await())
        assertEquals("alice", resolved.username)
        assertEquals("s3cret", resolved.password)
        assertEquals(true, resolved.remember)
        assertNull(manager.pendingDialog.value)
    }

    @Test
    fun `Given HTTP auth shown when user cancels then suspend returns Cancel`() = runTest {
        val manager = FrontendDialogManager()

        val outcome = async {
            manager.showHttpAuth(host = "example.com", message = { "auth required" }, isAuthError = false)
        }
        advanceUntilIdle()
        (manager.pendingDialog.value as FrontendDialog.HttpAuth).onCancel()
        advanceUntilIdle()

        assertEquals(HttpAuthOutcome.Cancel, outcome.await())
        assertNull(manager.pendingDialog.value)
    }

    @Test
    fun `Given isAuthError true when HTTP auth shown then dialog carries the flag`() = runTest {
        val manager = FrontendDialogManager()

        val outcome = async {
            manager.showHttpAuth(host = "example.com", message = { "auth required" }, isAuthError = true)
        }
        advanceUntilIdle()

        val pending = manager.pendingDialog.value as FrontendDialog.HttpAuth
        assertTrue(pending.isAuthError)

        pending.onCancel()
        advanceUntilIdle()
        outcome.await()
    }
}
