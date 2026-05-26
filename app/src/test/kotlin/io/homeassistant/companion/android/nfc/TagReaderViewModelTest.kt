package io.homeassistant.companion.android.nfc

import android.net.Uri
import app.cash.turbine.test
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class, ConsoleLogExtension::class)
class TagReaderViewModelTest {

    private val serverManager: ServerManager = mockk(relaxed = true)
    private val prefsRepository: PrefsRepository = mockk(relaxed = true)

    private fun createViewModel(): TagReaderViewModel = TagReaderViewModel(serverManager = serverManager, prefsRepository = prefsRepository)

    private fun uri(value: String): Uri = mockk(relaxed = true) {
        every { this@mockk.toString() } returns value
    }

    @Test
    fun `Given URL with no tag id and NFC origin, when onIntentReceived, then state becomes nfc Error`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            assertEquals(TagReaderUiState.Initial, awaitItem())
            viewModel.onIntentReceived(url = uri("https://example.com/not-a-tag"), isNfcTag = true)
            assertEquals(TagReaderUiState.Error(commonR.string.nfc_processing_tag_error), awaitItem())
        }
    }

    @Test
    fun `Given URL with no tag id and QR origin, when onIntentReceived, then state becomes qrcode Error`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            assertEquals(TagReaderUiState.Initial, awaitItem())
            viewModel.onIntentReceived(url = uri("https://example.com/not-a-tag"), isNfcTag = false)
            assertEquals(TagReaderUiState.Error(commonR.string.qrcode_processing_tag_error), awaitItem())
        }
    }

    @Test
    fun `Given no server registered, when onIntentReceived, then state becomes Error`() = runTest {
        coEvery { serverManager.isRegistered() } returns false

        val viewModel = createViewModel()
        viewModel.uiState.test {
            assertEquals(TagReaderUiState.Initial, awaitItem())
            viewModel.onIntentReceived(
                url = uri("https://www.home-assistant.io/tag/5f0ba733-172f-430d-a7f8-e4ad940c88d7"),
                isNfcTag = true,
            )
            assertEquals(TagReaderUiState.Error(commonR.string.nfc_processing_tag_error), awaitItem())
        }
    }

    @Test
    fun `Given tag id not in allowed list, when onIntentReceived, then state becomes ApprovingTag and scanTag is never called`() = runTest {
        val server1: Server = mockk(relaxed = true) { every { id } returns 1 }
        val repo1: IntegrationRepository = mockk(relaxed = true)

        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.servers() } returns listOf(server1)
        coEvery { serverManager.integrationRepository(1) } returns repo1
        coEvery { prefsRepository.getAllowedTags() } returns emptySet()

        val viewModel = createViewModel()
        viewModel.uiState.test {
            assertEquals(TagReaderUiState.Initial, awaitItem())
            viewModel.onIntentReceived(
                url = uri("https://www.home-assistant.io/tag/custom-tag-foo"),
                isNfcTag = true,
            )
            assertEquals(TagReaderUiState.ApprovingTag("custom-tag-foo"), awaitItem())
        }

        coVerify(exactly = 0) { repo1.scanTag(any()) }
    }

    @Test
    fun `Given tag id already in allowed list, when onIntentReceived, then approval is skipped and state goes Scanning then Done`() = runTest {
        val server1: Server = mockk(relaxed = true) { every { id } returns 1 }
        val repo1: IntegrationRepository = mockk(relaxed = true)

        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.servers() } returns listOf(server1)
        coEvery { serverManager.integrationRepository(1) } returns repo1
        coEvery { prefsRepository.getAllowedTags() } returns setOf("custom-tag-foo")

        val viewModel = createViewModel()
        viewModel.uiState.test {
            assertEquals(TagReaderUiState.Initial, awaitItem())
            viewModel.onIntentReceived(
                url = uri("https://www.home-assistant.io/tag/custom-tag-foo"),
                isNfcTag = true,
            )
            assertEquals(TagReaderUiState.Scanning, awaitItem())
            assertEquals(TagReaderUiState.Done, awaitItem())
        }

        coVerify(exactly = 1) { repo1.scanTag(mapOf("tag_id" to "custom-tag-foo")) }
        // The tag was already on the allow list — onIntentReceived must not re-add it.
        coVerify(exactly = 0) { prefsRepository.addAllowedTag(any()) }
    }

    @Test
    fun `Given ApprovingTag and two registered servers, when onAllowOnce, then state goes Scanning then Done and scanTag is called per server`() = runTest {
        val server1: Server = mockk(relaxed = true) { every { id } returns 1 }
        val server2: Server = mockk(relaxed = true) { every { id } returns 2 }
        val repo1: IntegrationRepository = mockk(relaxed = true)
        val repo2: IntegrationRepository = mockk(relaxed = true)

        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.servers() } returns listOf(server1, server2)
        coEvery { serverManager.integrationRepository(1) } returns repo1
        coEvery { serverManager.integrationRepository(2) } returns repo2

        val viewModel = createViewModel()
        viewModel.uiState.test {
            assertEquals(TagReaderUiState.Initial, awaitItem())
            viewModel.onIntentReceived(
                url = uri("https://www.home-assistant.io/tag/custom-tag-foo"),
                isNfcTag = true,
            )
            assertEquals(TagReaderUiState.ApprovingTag("custom-tag-foo"), awaitItem())

            viewModel.onAllowOnce()
            assertEquals(TagReaderUiState.Scanning, awaitItem())
            assertEquals(TagReaderUiState.Done, awaitItem())
        }

        coVerify(exactly = 1) { repo1.scanTag(mapOf("tag_id" to "custom-tag-foo")) }
        coVerify(exactly = 1) { repo2.scanTag(mapOf("tag_id" to "custom-tag-foo")) }
        coVerify(exactly = 0) { prefsRepository.addAllowedTag(any()) }
    }

    @Test
    fun `Given ApprovingTag, when onAllowAlways, then tag is persisted and state goes Scanning then Done and scanTag is called`() = runTest {
        val server1: Server = mockk(relaxed = true) { every { id } returns 1 }
        val repo1: IntegrationRepository = mockk(relaxed = true)

        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.servers() } returns listOf(server1)
        coEvery { serverManager.integrationRepository(1) } returns repo1

        val viewModel = createViewModel()
        viewModel.uiState.test {
            assertEquals(TagReaderUiState.Initial, awaitItem())
            viewModel.onIntentReceived(
                url = uri("https://www.home-assistant.io/tag/custom-tag-foo"),
                isNfcTag = true,
            )
            assertEquals(TagReaderUiState.ApprovingTag("custom-tag-foo"), awaitItem())

            viewModel.onAllowAlways()
            assertEquals(TagReaderUiState.Scanning, awaitItem())
            assertEquals(TagReaderUiState.Done, awaitItem())
        }

        coVerify(exactly = 1) { prefsRepository.addAllowedTag("custom-tag-foo") }
        coVerify(exactly = 1) { repo1.scanTag(mapOf("tag_id" to "custom-tag-foo")) }
    }

    @Test
    fun `Given Initial state, when onAllowAlways, then state stays Initial and tag is not persisted`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            assertEquals(TagReaderUiState.Initial, awaitItem())
            viewModel.onAllowAlways()
            expectNoEvents()
        }
        coVerify(exactly = 0) { prefsRepository.addAllowedTag(any()) }
        coVerify(exactly = 0) { serverManager.servers() }
    }

    @Test
    fun `Given two servers and scanTag throws on first, when onAllowOnce, then state still becomes Done and second server is still attempted`() = runTest {
        val server1: Server = mockk(relaxed = true) { every { id } returns 1 }
        val server2: Server = mockk(relaxed = true) { every { id } returns 2 }
        val repo1: IntegrationRepository = mockk()
        val repo2: IntegrationRepository = mockk(relaxed = true)

        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.servers() } returns listOf(server1, server2)
        coEvery { serverManager.integrationRepository(1) } returns repo1
        coEvery { serverManager.integrationRepository(2) } returns repo2
        coEvery { repo1.scanTag(any()) } throws java.io.IOException("boom")

        val viewModel = createViewModel()
        viewModel.uiState.test {
            assertEquals(TagReaderUiState.Initial, awaitItem())
            viewModel.onIntentReceived(
                url = uri("https://www.home-assistant.io/tag/custom-tag-foo"),
                isNfcTag = true,
            )
            assertEquals(TagReaderUiState.ApprovingTag("custom-tag-foo"), awaitItem())

            viewModel.onAllowOnce()
            assertEquals(TagReaderUiState.Scanning, awaitItem())
            assertEquals(TagReaderUiState.Done, awaitItem())
        }

        coVerify(exactly = 1) { repo2.scanTag(mapOf("tag_id" to "custom-tag-foo")) }
    }

    @Test
    fun `Given ApprovingTag, when onAllowOnce, then Scanning state lasts at least MIN_SCANNING_DURATION before Done`() = runTest {
        val server1: Server = mockk(relaxed = true) { every { id } returns 1 }
        val repo1: IntegrationRepository = mockk(relaxed = true)

        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.servers() } returns listOf(server1)
        coEvery { serverManager.integrationRepository(1) } returns repo1

        val testScope = this
        val viewModel = createViewModel()
        viewModel.uiState.test {
            assertEquals(TagReaderUiState.Initial, awaitItem())
            viewModel.onIntentReceived(
                url = uri("https://www.home-assistant.io/tag/custom-tag-foo"),
                isNfcTag = true,
            )
            assertEquals(TagReaderUiState.ApprovingTag("custom-tag-foo"), awaitItem())

            val scanningStart = testScope.currentTime
            viewModel.onAllowOnce()
            assertEquals(TagReaderUiState.Scanning, awaitItem())
            assertEquals(TagReaderUiState.Done, awaitItem())
            val elapsed = testScope.currentTime - scanningStart

            assertTrue(
                elapsed >= MIN_SCANNING_DURATION.inWholeMilliseconds,
                "Expected Scanning to last at least $MIN_SCANNING_DURATION, lasted ${elapsed}ms",
            )
        }
    }

    @Test
    fun `Given Initial state, when onAllowOnce, then state stays Initial and servers is never queried`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            assertEquals(TagReaderUiState.Initial, awaitItem())
            viewModel.onAllowOnce()
            expectNoEvents()
        }
        coVerify(exactly = 0) { serverManager.servers() }
    }

    @Test
    fun `Given Error state, when onErrorAcknowledged, then state becomes Done`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            assertEquals(TagReaderUiState.Initial, awaitItem())
            viewModel.onIntentReceived(url = uri("https://example.com/not-a-tag"), isNfcTag = true)
            assertEquals(TagReaderUiState.Error(commonR.string.nfc_processing_tag_error), awaitItem())

            viewModel.onErrorAcknowledged()
            assertEquals(TagReaderUiState.Done, awaitItem())
        }
    }

    @Test
    fun `Given ApprovingTag state, when onDismissed, then state becomes Done`() = runTest {
        coEvery { serverManager.isRegistered() } returns true

        val viewModel = createViewModel()
        viewModel.uiState.test {
            assertEquals(TagReaderUiState.Initial, awaitItem())
            viewModel.onIntentReceived(
                url = uri("https://www.home-assistant.io/tag/custom-tag-foo"),
                isNfcTag = true,
            )
            assertEquals(TagReaderUiState.ApprovingTag("custom-tag-foo"), awaitItem())

            viewModel.onDismissed()
            assertEquals(TagReaderUiState.Done, awaitItem())
        }
    }
}
