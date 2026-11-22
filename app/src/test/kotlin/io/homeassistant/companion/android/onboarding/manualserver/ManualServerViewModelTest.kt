package io.homeassistant.companion.android.onboarding.manualserver

import app.cash.turbine.turbineScope
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

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
            val resolvedUrlFlow = viewModel.resolvedUrlFlow.testIn(backgroundScope)
            assertEquals("", serverUrlFlow.awaitItem())
            assertNull(resolvedUrlFlow.awaitItem())

            viewModel.onServerUrlChange(validUrl)

            assertEquals(
                ManualServerUrl("http://example.com/".toHttpUrl(), fallbackToPlainHttp = false),
                resolvedUrlFlow.awaitItem(),
            )
            assertEquals(validUrl, serverUrlFlow.awaitItem())
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "example.com",
            "example.com:8123",
            "192.168.1.5:8123",
            "homeassistant.local",
            "example.com/some/path",
        ],
    )
    fun `Given url without scheme when onServerUrlChange then https is assumed and http fallback allowed`(input: String) = runTest {
        turbineScope {
            val resolvedUrlFlow = viewModel.resolvedUrlFlow.testIn(backgroundScope)
            assertNull(resolvedUrlFlow.awaitItem())

            viewModel.onServerUrlChange(input)

            val resolved = resolvedUrlFlow.awaitItem() ?: error("Expected a resolved URL for $input")
            assertEquals("https", resolved.url.scheme)
            assertTrue(resolved.fallbackToPlainHttp)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://example.com", "https://example.com:8123"])
    fun `Given url with explicit scheme when onServerUrlChange then http fallback is not allowed`(input: String) = runTest {
        turbineScope {
            val resolvedUrlFlow = viewModel.resolvedUrlFlow.testIn(backgroundScope)
            assertNull(resolvedUrlFlow.awaitItem())

            viewModel.onServerUrlChange(input)

            val resolved = resolvedUrlFlow.awaitItem() ?: error("Expected a resolved URL for $input")
            assertFalse(resolved.fallbackToPlainHttp)
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "htt://example.com",
            "ftp://example.com",
            "http://homeassistant.local::8123",
            "http://",
        ],
    )
    fun `Given invalid url when onServerUrlChange then resolvedUrl stays null`(invalidUrl: String) = runTest {
        turbineScope {
            val serverUrlFlow = viewModel.serverUrlFlow.testIn(backgroundScope)
            val resolvedUrlFlow = viewModel.resolvedUrlFlow.testIn(backgroundScope)
            assertEquals("", serverUrlFlow.awaitItem())
            assertNull(resolvedUrlFlow.awaitItem())

            viewModel.onServerUrlChange(invalidUrl)

            resolvedUrlFlow.expectNoEvents()
            assertEquals(invalidUrl, serverUrlFlow.awaitItem())
        }
    }
}
