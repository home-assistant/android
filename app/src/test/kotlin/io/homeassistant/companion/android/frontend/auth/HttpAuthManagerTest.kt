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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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

    private fun unconfinedTest(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

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
        fun `Given no stored credentials when auth requested then shows dialog`() = unconfinedTest {
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
        fun `Given stored credentials when rapid reauth then shows dialog instead of auto-proceeding`() = unconfinedTest {
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

            assertInstanceOf(FrontendDialog.HttpAuth::class.java, dialogManager.pendingDialog.value)

            pendingHttpAuthDialog().onCancel()
            advanceUntilIdle()
            assertEquals(HttpAuthResult.Cancelled, result.await())
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
    }

    @Nested
    inner class CredentialPersistence {

        @Test
        fun `Given no stored credentials when user proceeds with remember then inserts into dao`() = unconfinedTest {
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
        fun `Given rapid reauth when user proceeds with remember then updates in dao`() = unconfinedTest {
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
        fun `Given user proceeds without remember then credentials are not persisted`() = unconfinedTest {
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
        fun `Given dialog shown when user cancels then handler is cancelled and result is Cancelled`() = unconfinedTest {
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
        fun `Given https resource when dialog shown then message contains https and required fields`() = unconfinedTest {
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
        fun `Given http resource when dialog shown then message contains http and not private warning`() = unconfinedTest {
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
