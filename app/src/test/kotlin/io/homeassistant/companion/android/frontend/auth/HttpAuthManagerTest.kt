package io.homeassistant.companion.android.frontend.auth

import android.webkit.HttpAuthHandler
import io.homeassistant.companion.android.database.authentication.Authentication
import io.homeassistant.companion.android.database.authentication.AuthenticationDao
import io.homeassistant.companion.android.frontend.dialog.FrontendDialog
import io.homeassistant.companion.android.frontend.dialog.FrontendDialogManager
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.FakeClock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
@ExtendWith(ConsoleLogExtension::class)
class HttpAuthManagerTest {

    private val authenticationDao: AuthenticationDao = mockk(relaxed = true)
    private val clock = FakeClock()
    private val dialogManager = FrontendDialogManager()
    private val manager = HttpAuthManager(
        authenticationDao = authenticationDao,
        clock = clock,
        dialogManager = dialogManager,
    )

    private val handler: HttpAuthHandler = mockk(relaxed = true)

    private fun pendingHttpAuthDialog(): FrontendDialog.HttpAuth {
        return dialogManager.pendingDialog.value as FrontendDialog.HttpAuth
    }

    @Nested
    inner class AutoProceed {

        @Test
        fun `Given stored credentials when auth requested then auto-proceeds`() = runTest {
            coEvery { authenticationDao.get("https://example.com/testrealm") } returns Authentication(
                host = "https://example.com/testrealm",
                username = "user",
                password = "pass",
            )

            val result = manager.handleAuthRequest(
                handler = handler,
                host = "example.com",
                resource = "https://example.com/",
                realm = "testrealm",
            )

            assertEquals(HttpAuthResult.AutoProceeded, result)
            verify { handler.proceed("user", "pass") }
        }

        @Test
        fun `Given no stored credentials when auth requested then shows dialog`() = runTest {
            coEvery { authenticationDao.get(any()) } returns null

            val result = async {
                manager.handleAuthRequest(
                    handler = handler,
                    host = "example.com",
                    resource = "https://example.com/",
                    realm = "testrealm",
                )
            }
            advanceUntilIdle()

            assertInstanceOf(FrontendDialog.HttpAuth::class.java, dialogManager.pendingDialog.value)
            verify(exactly = 0) { handler.proceed(any(), any()) }

            // Tidy up so the suspend returns and async doesn't leak
            pendingHttpAuthDialog().onCancel()
            advanceUntilIdle()
            assertEquals(HttpAuthResult.Cancelled, result.await())
        }
    }

    @Nested
    inner class RapidReauth {

        @Test
        fun `Given stored credentials when rapid reauth then shows dialog instead of auto-proceeding`() = runTest {
            val storedAuth = Authentication(
                host = "https://example.com/testrealm",
                username = "user",
                password = "wrong",
            )
            coEvery { authenticationDao.get(any()) } returns storedAuth

            // First request: auto-proceeds
            manager.handleAuthRequest(
                handler = handler,
                host = "example.com",
                resource = "https://example.com/",
                realm = "testrealm",
            )

            // Second request within 500ms: rapid reauth detected, dialog is shown
            clock.currentInstant += 200.milliseconds

            val result = async {
                manager.handleAuthRequest(
                    handler = handler,
                    host = "example.com",
                    resource = "https://example.com/",
                    realm = "testrealm",
                )
            }
            advanceUntilIdle()

            val pending = dialogManager.pendingDialog.value
            assertInstanceOf(FrontendDialog.HttpAuth::class.java, pending)
            assertTrue((pending as FrontendDialog.HttpAuth).isAuthError)

            pendingHttpAuthDialog().onCancel()
            advanceUntilIdle()
            assertEquals(HttpAuthResult.Cancelled, result.await())
        }

        @Test
        fun `Given no stored credentials when auth requested then dialog is not in auth-error state`() = runTest {
            coEvery { authenticationDao.get(any()) } returns null

            val result = async {
                manager.handleAuthRequest(
                    handler = handler,
                    host = "example.com",
                    resource = "https://example.com/",
                    realm = "realm",
                )
            }
            advanceUntilIdle()

            assertFalse(pendingHttpAuthDialog().isAuthError)

            pendingHttpAuthDialog().onCancel()
            advanceUntilIdle()
            result.await()
        }

        @Test
        fun `Given user proceeds without remember when rapid reauth then dialog shows auth-error`() = runTest {
            // No stored credentials: dialog shown for the first request.
            coEvery { authenticationDao.get(any()) } returns null

            val firstRequest = async {
                manager.handleAuthRequest(
                    handler = handler,
                    host = "example.com",
                    resource = "https://example.com/",
                    realm = "realm",
                )
            }
            advanceUntilIdle()

            // User submits wrong credentials without ticking remember
            pendingHttpAuthDialog().onProceed("user", "wrong", false)
            advanceUntilIdle()
            assertEquals(HttpAuthResult.Proceeded, firstRequest.await())

            // Server bounces it back within the threshold — still no stored creds because remember was false.
            clock.currentInstant += 200.milliseconds

            val secondRequest = async {
                manager.handleAuthRequest(
                    handler = handler,
                    host = "example.com",
                    resource = "https://example.com/",
                    realm = "realm",
                )
            }
            advanceUntilIdle()

            // Dialog reappears with the rejected indicator.
            assertTrue(pendingHttpAuthDialog().isAuthError)

            pendingHttpAuthDialog().onCancel()
            advanceUntilIdle()
            assertEquals(HttpAuthResult.Cancelled, secondRequest.await())
        }

        @Test
        fun `Given proceed on resource A when resource B challenges right after then dialog is not in auth-error state`() = runTest {
            coEvery { authenticationDao.get(any()) } returns null

            // Resource A: user proceeds with credentials
            val firstRequest = async {
                manager.handleAuthRequest(
                    handler = handler,
                    host = "a.example.com",
                    resource = "https://a.example.com/",
                    realm = "realm",
                )
            }
            advanceUntilIdle()
            pendingHttpAuthDialog().onProceed("user", "pass", false)
            advanceUntilIdle()
            assertEquals(HttpAuthResult.Proceeded, firstRequest.await())

            // Resource B challenges within the threshold — different hostKey, so not a rapid reauth.
            clock.currentInstant += 100.milliseconds

            val secondRequest = async {
                manager.handleAuthRequest(
                    handler = handler,
                    host = "b.example.com",
                    resource = "https://b.example.com/",
                    realm = "realm",
                )
            }
            advanceUntilIdle()

            assertFalse(pendingHttpAuthDialog().isAuthError)

            pendingHttpAuthDialog().onCancel()
            advanceUntilIdle()
            secondRequest.await()
        }

        @Test
        fun `Given stored credentials when auth requested after threshold then auto-proceeds`() = runTest {
            val storedAuth = Authentication(
                host = "https://example.com/testrealm",
                username = "user",
                password = "pass",
            )
            coEvery { authenticationDao.get(any()) } returns storedAuth

            // First request: auto-proceeds
            manager.handleAuthRequest(
                handler = handler,
                host = "example.com",
                resource = "https://example.com/",
                realm = "testrealm",
            )

            // Second request after 500ms: not rapid reauth
            clock.currentInstant += 1.seconds

            val result = manager.handleAuthRequest(
                handler = handler,
                host = "example.com",
                resource = "https://example.com/",
                realm = "testrealm",
            )

            assertEquals(HttpAuthResult.AutoProceeded, result)
        }

        @Test
        fun `Given user proceeds without remember when same key challenges after threshold then dialog still shows auth-error`() = runTest {
            // WebView caches Basic Auth in-session, so any re-challenge for a hostKey we already
            // proceeded on is almost certainly the server rejecting those credentials even past
            // the rapid-reauth window. Only the host-key match (not timing) gates this indicator.
            coEvery { authenticationDao.get(any()) } returns null

            val firstRequest = async {
                manager.handleAuthRequest(
                    handler = handler,
                    host = "example.com",
                    resource = "https://example.com/",
                    realm = "realm",
                )
            }
            advanceUntilIdle()
            pendingHttpAuthDialog().onProceed("user", "wrong", false)
            advanceUntilIdle()
            firstRequest.await()

            clock.currentInstant += 10.seconds

            val secondRequest = async {
                manager.handleAuthRequest(
                    handler = handler,
                    host = "example.com",
                    resource = "https://example.com/",
                    realm = "realm",
                )
            }
            advanceUntilIdle()

            assertTrue(pendingHttpAuthDialog().isAuthError)

            pendingHttpAuthDialog().onCancel()
            advanceUntilIdle()
            secondRequest.await()
        }

        @Test
        fun `Given user cancels when same key challenges right after then dialog is not in auth-error state`() = runTest {
            // Cancelling must not record a proceed; otherwise the next challenge on the same key
            // would falsely show the rejection notice.
            coEvery { authenticationDao.get(any()) } returns null

            val firstRequest = async {
                manager.handleAuthRequest(
                    handler = handler,
                    host = "example.com",
                    resource = "https://example.com/",
                    realm = "realm",
                )
            }
            advanceUntilIdle()
            pendingHttpAuthDialog().onCancel()
            advanceUntilIdle()
            assertEquals(HttpAuthResult.Cancelled, firstRequest.await())

            // Same key, well within threshold — should still be a fresh dialog.
            clock.currentInstant += 100.milliseconds

            val secondRequest = async {
                manager.handleAuthRequest(
                    handler = handler,
                    host = "example.com",
                    resource = "https://example.com/",
                    realm = "realm",
                )
            }
            advanceUntilIdle()

            assertFalse(pendingHttpAuthDialog().isAuthError)

            pendingHttpAuthDialog().onCancel()
            advanceUntilIdle()
            secondRequest.await()
        }
    }

    @Nested
    inner class CredentialPersistence {

        @Test
        fun `Given no stored credentials when user proceeds with remember then inserts into dao`() = runTest {
            coEvery { authenticationDao.get(any()) } returns null

            val result = async {
                manager.handleAuthRequest(
                    handler = handler,
                    host = "example.com",
                    resource = "https://example.com/",
                    realm = "realm",
                )
            }
            advanceUntilIdle()
            pendingHttpAuthDialog().onProceed("user", "pass", true)
            advanceUntilIdle()

            assertEquals(HttpAuthResult.Proceeded, result.await())
            verify { handler.proceed("user", "pass") }
            coVerify { authenticationDao.insert(Authentication("https://example.com/realm", "user", "pass")) }
            coVerify(exactly = 0) { authenticationDao.update(any()) }
        }

        @Test
        fun `Given no stored creds and prior manual proceed when rapid reauth with remember then inserts into dao`() = runTest {
            // No stored creds because the first proceed was without remember. The rapid-reauth
            // retry that ticks remember must INSERT falling back to update would crash on the
            // primary-key mismatch since nothing is in the table yet.
            coEvery { authenticationDao.get(any()) } returns null

            val firstRequest = async {
                manager.handleAuthRequest(
                    handler = handler,
                    host = "example.com",
                    resource = "https://example.com/",
                    realm = "realm",
                )
            }
            advanceUntilIdle()
            pendingHttpAuthDialog().onProceed("user", "wrong", false)
            advanceUntilIdle()
            firstRequest.await()

            // Server bounces it back within the threshold; user retries with remember=true.
            clock.currentInstant += 20.milliseconds

            val secondRequest = async {
                manager.handleAuthRequest(
                    handler = handler,
                    host = "example.com",
                    resource = "https://example.com/",
                    realm = "realm",
                )
            }
            advanceUntilIdle()
            pendingHttpAuthDialog().onProceed("user", "right", true)
            advanceUntilIdle()

            assertEquals(HttpAuthResult.Proceeded, secondRequest.await())
            coVerify { authenticationDao.insert(Authentication("https://example.com/realm", "user", "right")) }
            coVerify(exactly = 0) { authenticationDao.update(any()) }
        }

        @Test
        fun `Given rapid reauth when user proceeds with remember then updates in dao`() = runTest {
            val storedAuth = Authentication("https://example.com/realm", "user", "wrong")
            coEvery { authenticationDao.get(any()) } returns storedAuth

            // First request: auto-proceeds
            manager.handleAuthRequest(handler = handler, host = "example.com", resource = "https://example.com/", realm = "realm")

            // Second request within 500ms: rapid reauth
            clock.currentInstant += 200.milliseconds

            val result = async {
                manager.handleAuthRequest(
                    handler = handler,
                    host = "example.com",
                    resource = "https://example.com/",
                    realm = "realm",
                )
            }
            advanceUntilIdle()
            pendingHttpAuthDialog().onProceed("user", "newpass", true)
            advanceUntilIdle()

            assertEquals(HttpAuthResult.Proceeded, result.await())
            coVerify { authenticationDao.update(Authentication("https://example.com/realm", "user", "newpass")) }
            coVerify(exactly = 0) { authenticationDao.insert(any()) }
        }

        @Test
        fun `Given user proceeds without remember then credentials are not persisted`() = runTest {
            coEvery { authenticationDao.get(any()) } returns null

            val result = async {
                manager.handleAuthRequest(
                    handler = handler,
                    host = "example.com",
                    resource = "https://example.com/",
                    realm = "realm",
                )
            }
            advanceUntilIdle()
            pendingHttpAuthDialog().onProceed("user", "pass", false)
            advanceUntilIdle()

            assertEquals(HttpAuthResult.Proceeded, result.await())
            coVerify(exactly = 0) { authenticationDao.insert(any()) }
            coVerify(exactly = 0) { authenticationDao.update(any()) }
        }

        @Test
        fun `Given rapid reauth when user proceeds without remember then nothing is persisted`() = runTest {
            // Stored creds were rejected; user fixes them in their head but doesn't tick remember.
            // Persistence must stay untouched — neither update (despite isRapidReauth) nor insert.
            val storedAuth = Authentication("https://example.com/realm", "user", "wrong")
            coEvery { authenticationDao.get(any()) } returns storedAuth

            // First request: auto-proceeds with the stored (wrong) credentials.
            manager.handleAuthRequest(
                handler = handler,
                host = "example.com",
                resource = "https://example.com/",
                realm = "realm",
            )

            // Second request within 500ms: rapid reauth, dialog shown.
            clock.currentInstant += 200.milliseconds

            val result = async {
                manager.handleAuthRequest(
                    handler = handler,
                    host = "example.com",
                    resource = "https://example.com/",
                    realm = "realm",
                )
            }
            advanceUntilIdle()
            pendingHttpAuthDialog().onProceed("user", "newpass", false)
            advanceUntilIdle()

            assertEquals(HttpAuthResult.Proceeded, result.await())
            coVerify(exactly = 0) { authenticationDao.insert(any()) }
            coVerify(exactly = 0) { authenticationDao.update(any()) }
        }

        @Test
        fun `Given dialog shown when user cancels then handler is cancelled and result is Cancelled`() = runTest {
            coEvery { authenticationDao.get(any()) } returns null

            val result = async {
                manager.handleAuthRequest(
                    handler = handler,
                    host = "example.com",
                    resource = "https://example.com/",
                    realm = "realm",
                )
            }
            advanceUntilIdle()
            pendingHttpAuthDialog().onCancel()
            advanceUntilIdle()

            assertEquals(HttpAuthResult.Cancelled, result.await())
            verify { handler.cancel() }
        }
    }

    @Nested
    inner class MessageFormatting {

        private val context: android.content.Context = mockk {
            every { getString(any()) } answers {
                when (firstArg<Int>()) {
                    io.homeassistant.companion.android.common.R.string.required_fields -> "requires a username and password."
                    io.homeassistant.companion.android.common.R.string.not_private -> "Your connection to this site is not private."
                    else -> ""
                }
            }
        }

        @Test
        fun `Given https resource when dialog shown then message contains https and required fields`() = runTest {
            coEvery { authenticationDao.get(any()) } returns null

            val result = async {
                manager.handleAuthRequest(
                    handler = handler,
                    host = "example.com",
                    resource = "https://example.com/",
                    realm = "realm",
                )
            }
            advanceUntilIdle()

            val message = pendingHttpAuthDialog().message(context)
            assertTrue(message.startsWith("https://"))
            assertTrue(message.contains("requires a username and password."))

            pendingHttpAuthDialog().onCancel()
            advanceUntilIdle()
            result.await()
        }

        @Test
        fun `Given http resource when dialog shown then message contains http and not private warning`() = runTest {
            coEvery { authenticationDao.get(any()) } returns null

            val result = async {
                manager.handleAuthRequest(
                    handler = handler,
                    host = "example.com",
                    resource = "http://example.com/",
                    realm = "realm",
                )
            }
            advanceUntilIdle()

            val message = pendingHttpAuthDialog().message(context)
            assertTrue(message.startsWith("http://"))
            assertTrue(message.contains("Your connection to this site is not private."))

            pendingHttpAuthDialog().onCancel()
            advanceUntilIdle()
            result.await()
        }
    }
}
