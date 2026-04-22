package io.homeassistant.companion.android.frontend.auth

import android.webkit.HttpAuthHandler
import io.homeassistant.companion.android.database.authentication.Authentication
import io.homeassistant.companion.android.database.authentication.AuthenticationDao
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
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalTime::class)
@ExtendWith(ConsoleLogExtension::class)
class HttpAuthManagerTest {

    private val authenticationDao: AuthenticationDao = mockk(relaxed = true)
    private val clock = FakeClock()
    private val manager = HttpAuthManager(authenticationDao = authenticationDao, clock = clock)

    private val handler: HttpAuthHandler = mockk(relaxed = true)

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

            assertInstanceOf(HttpAuthResult.AutoProceeded::class.java, result)
            verify { handler.proceed("user", "pass") }
        }

        @Test
        fun `Given no stored credentials when auth requested then shows dialog`() = runTest {
            coEvery { authenticationDao.get(any()) } returns null

            val result = manager.handleAuthRequest(
                handler = handler,
                host = "example.com",
                resource = "https://example.com/",
                realm = "testrealm",
            )

            assertInstanceOf(HttpAuthResult.ShowDialog::class.java, result)
            verify(exactly = 0) { handler.proceed(any(), any()) }
        }
    }

    @Nested
    inner class RapidReauth {

        @Test
        fun `Given stored credentials when rapid reauth then shows dialog with auth error`() = runTest {
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

            // Second request within 500ms: rapid reauth detected
            clock.currentInstant += 200.milliseconds

            val result = manager.handleAuthRequest(
                handler = handler,
                host = "example.com",
                resource = "https://example.com/",
                realm = "testrealm",
            )

            val dialog = assertInstanceOf(HttpAuthResult.ShowDialog::class.java, result)
            assertTrue(dialog.isAuthError)
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

            assertInstanceOf(HttpAuthResult.AutoProceeded::class.java, result)
        }
    }

    @Nested
    inner class CredentialPersistence {

        @Test
        fun `Given no stored credentials when onProceed with remember then inserts into dao`() = runTest {
            coEvery { authenticationDao.get(any()) } returns null

            val result = manager.handleAuthRequest(
                handler = handler,
                host = "example.com",
                resource = "https://example.com/",
                realm = "realm",
            ) as HttpAuthResult.ShowDialog

            result.onProceed("user", "pass", true)

            coVerify { authenticationDao.insert(Authentication("https://example.com/realm", "user", "pass")) }
            coVerify(exactly = 0) { authenticationDao.update(any()) }
        }

        @Test
        fun `Given rapid reauth when onProceed with remember then updates in dao`() = runTest {
            val storedAuth = Authentication("https://example.com/realm", "user", "wrong")
            coEvery { authenticationDao.get(any()) } returns storedAuth

            // First request: auto-proceeds
            manager.handleAuthRequest(handler = handler, host = "example.com", resource = "https://example.com/", realm = "realm")

            // Second request within 500ms: rapid reauth
            clock.currentInstant += 200.milliseconds

            val result = manager.handleAuthRequest(
                handler = handler,
                host = "example.com",
                resource = "https://example.com/",
                realm = "realm",
            ) as HttpAuthResult.ShowDialog

            result.onProceed("user", "newpass", true)

            coVerify { authenticationDao.update(Authentication("https://example.com/realm", "user", "newpass")) }
            coVerify(exactly = 0) { authenticationDao.insert(any()) }
        }

        @Test
        fun `Given onProceed without remember then credentials are not persisted`() = runTest {
            coEvery { authenticationDao.get(any()) } returns null

            val result = manager.handleAuthRequest(
                handler = handler,
                host = "example.com",
                resource = "https://example.com/",
                realm = "realm",
            ) as HttpAuthResult.ShowDialog

            result.onProceed("user", "pass", false)

            coVerify(exactly = 0) { authenticationDao.insert(any()) }
            coVerify(exactly = 0) { authenticationDao.update(any()) }
        }

        @Test
        fun `Given dialog shown when onCancel called then handler is cancelled`() = runTest {
            coEvery { authenticationDao.get(any()) } returns null

            val result = manager.handleAuthRequest(
                handler = handler,
                host = "example.com",
                resource = "https://example.com/",
                realm = "realm",
            ) as HttpAuthResult.ShowDialog

            result.onCancel()

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

            val result = manager.handleAuthRequest(
                handler = handler,
                host = "example.com",
                resource = "https://example.com/",
                realm = "realm",
            ) as HttpAuthResult.ShowDialog

            val message = result.message(context)
            assertTrue(message.startsWith("https://"))
            assertTrue(message.contains("requires a username and password."))
        }

        @Test
        fun `Given http resource when dialog shown then message contains http and not private warning`() = runTest {
            coEvery { authenticationDao.get(any()) } returns null

            val result = manager.handleAuthRequest(
                handler = handler,
                host = "example.com",
                resource = "http://example.com/",
                realm = "realm",
            ) as HttpAuthResult.ShowDialog

            val message = result.message(context)
            assertTrue(message.startsWith("http://"))
            assertTrue(message.contains("Your connection to this site is not private."))
        }
    }
}
