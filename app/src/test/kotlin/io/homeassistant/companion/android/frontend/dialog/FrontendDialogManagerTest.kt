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

    @Test
    fun `Given HTTP auth shown when user proceeds then suspend returns Proceed with credentials`() = runTest {
        val manager = FrontendDialogManager()

        val outcome = async {
            manager.showHttpAuth(host = "example.com", message = { "auth required" }, isAuthError = false)
        }
        advanceUntilIdle()

        val pending = manager.pendingDialog.value
        assertInstanceOf(FrontendDialog.HttpAuth::class.java, pending)
        (pending as FrontendDialog.HttpAuth).onProceed("alice", "s3cret", true)
        advanceUntilIdle()

        val resolved = outcome.await()
        assertInstanceOf(HttpAuthOutcome.Proceed::class.java, resolved)
        val proceed = resolved as HttpAuthOutcome.Proceed
        assertEquals("alice", proceed.username)
        assertEquals("s3cret", proceed.password)
        assertEquals(true, proceed.remember)
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
