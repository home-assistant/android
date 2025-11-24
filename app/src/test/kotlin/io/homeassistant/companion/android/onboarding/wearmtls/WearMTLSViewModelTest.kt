package io.homeassistant.companion.android.onboarding.wearmtls

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.cash.turbine.test
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.KeyStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class)
class WearMTLSViewModelTest {
    private val mockContentResolver: ContentResolver = mockk()
    private val mockUri: Uri = mockk<Uri>()
    private lateinit var viewModel: WearMTLSViewModel

    private val mockKeyStore: KeyStore = mockk()

    private val testFileName = "test_cert.p12"

    @BeforeEach
    fun setUp() {
        mockkStatic(KeyStore::class)
        every { KeyStore.getInstance("PKCS12") } returns mockKeyStore
        val context = mockk<Context> {
            every { contentResolver } returns this@WearMTLSViewModelTest.mockContentResolver
        }
        viewModel = WearMTLSViewModel(context)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(KeyStore::class)
    }

    @Test
    fun `Given viewModel is initialized, when uiState is observed, then it should have default values`() {
        val initialState = viewModel.uiState.value
        assertNull(initialState.selectedUri)
        assertNull(initialState.selectedFileName)
        assertEquals("", initialState.currentPassword)
        assertFalse(initialState.isCertValidated)
        assertFalse(initialState.showError)
    }

    @Test
    fun `Given viewModel, when onUriSelected with null URI is called, then state updates and no error or validation occurs`() = runTest {
        viewModel.uiState.test {
            viewModel.onUriSelected(null)

            val state = awaitItem()
            assertNull(state.selectedUri)
            assertNull(state.selectedFileName)
            assertFalse(state.isCertValidated)
            assertFalse(state.showError)
        }
    }

    @Test
    fun `Given viewModel, when onUriSelected with a valid URI is called, then URI and filename are updated`() = runTest {
        mockContentResolverValidQueryFilename()

        viewModel.uiState.test {
            assertEquals(WearMTLSUiState(), awaitItem())

            viewModel.onUriSelected(mockUri)

            var state = awaitItem()
            assertEquals(mockUri, state.selectedUri)
            assertNull(state.selectedFileName)

            state = awaitItem()
            assertEquals(mockUri, state.selectedUri)
            assertEquals(testFileName, state.selectedFileName)
            assertFalse(state.isCertValidated)
            assertFalse(state.showError)
            expectNoEvents()
        }
    }

    @Test
    fun `Given URI with query returning null, when onUriSelected, then selectedFileName is null`() = runTest {
        every { mockContentResolver.query(mockUri, any(), null, null, null) } returns null

        viewModel.uiState.test {
            assertEquals(WearMTLSUiState(), awaitItem())

            viewModel.onUriSelected(mockUri)

            val state = awaitItem() // URI set, filename null
            assertEquals(mockUri, state.selectedUri)

            expectNoEvents()
        }
    }

    @Test
    fun `Given URI with cursor returning no rows, when onUriSelected, then selectedFileName is null`() = runTest {
        every { mockContentResolver.query(mockUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null) } returns mockk {
            every { moveToFirst() } returns false
            every { close() } just Runs
        }

        viewModel.uiState.test {
            assertEquals(WearMTLSUiState(), awaitItem())

            viewModel.onUriSelected(mockUri)

            val state = awaitItem() // URI set, filename null
            assertEquals(mockUri, state.selectedUri)

            expectNoEvents()
        }
    }

    @Test
    fun `Given URI with cursor throwing when getting filename, when onUriSelected, then selectedFileName is null`() = runTest {
        every { mockContentResolver.query(mockUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null) } returns mockk {
            every { moveToFirst() } returns true
            every { getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME) } throws IllegalArgumentException("No Column")
            every { close() } just Runs
        }

        viewModel.uiState.test {
            assertEquals(WearMTLSUiState(), awaitItem())

            viewModel.onUriSelected(mockUri)

            val state = awaitItem() // URI set, filename null
            assertEquals(mockUri, state.selectedUri)

            expectNoEvents()
        }
    }

    @Test
    fun `Given viewModel with selected URI, when onPasswordChanged is called with wrong password, then UI state is updated multiple times and error is shown`() = runTest {
        val newPassword = "newPassword"
        val mockInputStream = ByteArrayInputStream("data".toByteArray())
        every { mockContentResolver.openInputStream(mockUri) } returns mockInputStream
        every { mockKeyStore.load(any(), newPassword.toCharArray()) } throws Exception("validation fail")
        mockContentResolverValidQueryFilename()

        viewModel.uiState.test {
            awaitItem() // Initial state

            viewModel.onUriSelected(mockUri) // Prerequisite: URI must be selected
            awaitItem()
            awaitItem() // URI selected, then filename fetched

            viewModel.onPasswordChanged(newPassword)

            var state = awaitItem()
            assertEquals(newPassword, state.currentPassword)
            assertFalse(state.isCertValidated)
            assertFalse(state.showError)

            advanceTimeBy(passwordValidationDebounce)

            state = awaitItem()
            assertEquals(newPassword, state.currentPassword)
            assertFalse(state.isCertValidated)
            assertTrue(state.showError)
        }
    }

    @Test
    fun `Given URI and valid password, when validation succeeds, then isCertValidated true, no error`() = runTest {
        val password = "correctPassword"
        val mockInputStream = ByteArrayInputStream("cert_data".toByteArray())
        every { mockContentResolver.openInputStream(mockUri) } returns mockInputStream
        every { mockKeyStore.load(any(), password.toCharArray()) } just Runs // Success
        mockContentResolverValidQueryFilename()

        viewModel.uiState.test {
            assertEquals(WearMTLSUiState(), awaitItem())

            viewModel.onUriSelected(mockUri)
            awaitItem()
            awaitItem() // URI selected, then filename fetched

            viewModel.onPasswordChanged(password)
            awaitItem() // Password updated

            advanceTimeBy(passwordValidationDebounce)

            val finalState = awaitItem()
            assertEquals(password, finalState.currentPassword)
            assertTrue(finalState.isCertValidated)
            assertFalse(finalState.showError)
            verify { mockKeyStore.load(any<InputStream>(), password.toCharArray()) }
        }
    }

    @Test
    fun `Given URI and password, when openInputStream fails, then validation fails, showError true`() = runTest {
        val password = "anyPassword"
        every { mockContentResolver.openInputStream(mockUri) } throws Exception("InputStream error")
        mockContentResolverValidQueryFilename()

        viewModel.uiState.test {
            assertEquals(WearMTLSUiState(), awaitItem())

            viewModel.onUriSelected(mockUri)
            awaitItem()
            awaitItem() // URI selected, then filename fetched

            viewModel.onPasswordChanged(password)
            awaitItem()

            advanceTimeBy(passwordValidationDebounce)
            val finalState = awaitItem()
            assertFalse(finalState.isCertValidated)
            assertTrue(finalState.showError)
        }
    }

    @Test
    fun `Given viewModel, when onPasswordChanged is called multiple times rapidly, then only the last value should trigger validation`() = runTest {
        val mockInputStream = ByteArrayInputStream("data".toByteArray())
        every { mockContentResolver.openInputStream(mockUri) } returns mockInputStream

        every { mockKeyStore.load(any(), "p1".toCharArray()) } throws Exception("Should be cancelled")
        every { mockKeyStore.load(any(), "p2".toCharArray()) } throws Exception("Should be cancelled")
        every { mockKeyStore.load(any(), "finalPassword".toCharArray()) } just Runs // Success for final

        mockContentResolverValidQueryFilename()

        viewModel.uiState.test {
            viewModel.onUriSelected(mockUri)
            awaitItem()
            awaitItem() // URI selected, then filename fetched

            viewModel.onPasswordChanged("p1")
            var state = awaitItem()
            assertEquals("p1", state.currentPassword)
            assertFalse(state.showError)

            viewModel.onPasswordChanged("p2")
            state = expectMostRecentItem()
            assertFalse(state.showError)
            assertEquals("p2", state.currentPassword)

            viewModel.onPasswordChanged("finalPassword")
            val lastPassChangeState = awaitItem() // finalPassword set
            assertEquals("finalPassword", lastPassChangeState.currentPassword)
            assertFalse(lastPassChangeState.isCertValidated)

            advanceTimeBy(passwordValidationDebounce) // Allow final validation to occur

            val test = awaitItem()
            assertEquals("finalPassword", test.currentPassword)

            val finalState = awaitItem()
            assertEquals("finalPassword", finalState.currentPassword)
            assertTrue(finalState.isCertValidated) // Validated with "finalPassword"
            assertFalse(finalState.showError)

            expectNoEvents()

            verify(exactly = 0) {
                mockKeyStore.load(any(), "p1".toCharArray())
                mockKeyStore.load(any(), "p2".toCharArray())
            }
            verify(exactly = 1) { mockKeyStore.load(any(), "finalPassword".toCharArray()) }
        }
    }

    private fun mockContentResolverValidQueryFilename() {
        every { mockContentResolver.query(mockUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null) } returns mockk {
            every { moveToFirst() } returns true
            every { getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME) } returns 0
            every { getString(0) } returns testFileName
            every { close() } just Runs
        }
    }
}
