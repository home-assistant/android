package io.homeassistant.companion.android.sensors.healthconnect

import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ConsoleLogExtension::class)
class HealthConnectChangesTokenStoreTest {

    private val storage = mockk<LocalStorage>(relaxed = true)
    private val store = HealthConnectChangesTokenStore(storage)

    @Test
    fun `get reads using prefixed key per data type`() = runTest {
        coEvery { storage.getString("changes_token::weight") } returns "tok-w"

        val result = store.get(HealthConnectDataType.Weight)

        assertEquals("tok-w", result)
    }

    @Test
    fun `blank token returns null so callers mint a fresh one`() = runTest {
        coEvery { storage.getString(any()) } returns ""

        assertNull(store.get(HealthConnectDataType.Weight))
    }

    @Test
    fun `put writes through the prefixed key`() = runTest {
        store.put(HealthConnectDataType.Steps, "tok-s")

        coVerify { storage.putString("changes_token::steps", "tok-s") }
    }

    @Test
    fun `clear removes only the targeted data type`() = runTest {
        store.clear(HealthConnectDataType.Hydration)

        coVerify(exactly = 1) { storage.remove("changes_token::hydration") }
    }

    @Test
    fun `clearAll removes every known data type`() = runTest {
        store.clearAll()

        HealthConnectDataType.all.forEach {
            coVerify { storage.remove("changes_token::${it.key}") }
        }
    }
}
