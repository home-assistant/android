package io.homeassistant.companion.android.onboarding.manualserver

import android.webkit.URLUtil
import app.cash.turbine.turbineScope
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherJUnit5Extension::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ManualServerViewModelTest {

    private lateinit var viewModel: ManualServerViewModel

    @BeforeEach
    fun setup() {
        viewModel = ManualServerViewModel()
    }

    @Test
    fun `Given valid url when onServerUrlChange then flows are updated accordingly`() = runTest {
        val validUrl = "http://example.com"

        turbineScope {
            val serverUrlFlow = viewModel.serverUrlFlow.testIn(backgroundScope)
            val isServerUrlValidFlow = viewModel.isServerUrlValidFlow.testIn(backgroundScope)
            assertEquals("", serverUrlFlow.awaitItem())
            assertFalse(isServerUrlValidFlow.awaitItem())

            mockUrlUtilIsValidUrl(true)

            viewModel.onServerUrlChange(validUrl)

            assertTrue(isServerUrlValidFlow.awaitItem())
            assertEquals(validUrl, serverUrlFlow.awaitItem())
        }
    }

    @Test
    fun `Given invalid url when onServerUrlChange then flows are updated accordingly`() = runTest {
        val invalidUrl = "htt://example.com"

        turbineScope {
            val serverUrlFlow = viewModel.serverUrlFlow.testIn(backgroundScope)
            val isServerUrlValidFlow = viewModel.isServerUrlValidFlow.testIn(backgroundScope)
            assertEquals("", serverUrlFlow.awaitItem())
            assertFalse(isServerUrlValidFlow.awaitItem())

            mockUrlUtilIsValidUrl(false)

            viewModel.onServerUrlChange(invalidUrl)

            isServerUrlValidFlow.expectNoEvents()
            assertEquals(invalidUrl, serverUrlFlow.awaitItem())
        }
    }

    private fun mockUrlUtilIsValidUrl(isValid: Boolean) {
        mockkStatic(URLUtil::class)
        every { URLUtil.isValidUrl(any()) } returns isValid
    }
}
