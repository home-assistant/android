package io.homeassistant.companion.android.common.data.authentication.impl

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private const val SERVER_ID = 1
private const val LOCK_ENABLED_KEY = "${SERVER_ID}_biometric_enabled"

class AuthenticationRepositoryImplTest {

    private val keyChangesFlow = MutableSharedFlow<String>()
    private val mapperSlot = slot<suspend () -> Any>()
    private val localStorage = mockk<LocalStorage> {
        every { observeChanges(*anyVararg<String>()) } returns keyChangesFlow
        coEvery {
            observeChanges(*anyVararg<String>(), mapper = capture(mapperSlot))
        } answers {
            merge(keyChangesFlow, flowOf("")).map { mapperSlot.captured.invoke() }
        }
    }

    private lateinit var repository: AuthenticationRepositoryImpl

    @BeforeEach
    fun setup() {
        repository = AuthenticationRepositoryImpl(
            authenticationService = mockk(relaxed = true),
            serverManager = mockk<ServerManager>(relaxed = true),
            serverId = SERVER_ID,
            localStorage = localStorage,
            installId = "install-id",
        )
    }

    @Nested
    inner class LockEnabledRawFlow {

        @Test
        fun `Given collecting flow then the current setting is emitted immediately`() = runTest {
            coEvery { localStorage.getBoolean(LOCK_ENABLED_KEY) } returns true

            repository.lockEnabledRawFlow().test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given collecting flow when the lock setting changes then the updated value is emitted`() = runTest {
            coEvery { localStorage.getBoolean(LOCK_ENABLED_KEY) } returns true

            repository.lockEnabledRawFlow().test {
                assertTrue(awaitItem())

                coEvery { localStorage.getBoolean(LOCK_ENABLED_KEY) } returns false
                keyChangesFlow.emit(LOCK_ENABLED_KEY)

                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
