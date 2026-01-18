package io.homeassistant.companion.android.common.data.connectivity

import io.homeassistant.companion.android.common.R as commonR
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import java.net.URL
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ConnectivityCheckRepositoryImplTest {
    private lateinit var checker: ConnectivityChecker
    private lateinit var repository: ConnectivityCheckRepositoryImpl

    @BeforeEach
    fun setup() {
        checker = mockk()
        repository = ConnectivityCheckRepositoryImpl(checker)
    }

    @ParameterizedTest
    @ValueSource(strings = ["not a valid url", "://invalid", "ht!tp://bad.url", ""])
    fun `Given invalid URL when running checks then DNS check fails with invalid URL error`(invalidUrl: String) = runTest {
        // When
        val states = repository.runChecks(invalidUrl).toList()

        // Then
        assertEquals(2, states.size)
        assertEquals(ConnectivityCheckResult.Pending, states[0].dnsResolution)

        val finalState = states[1]
        assertTrue(finalState.dnsResolution is ConnectivityCheckResult.Failure)
        assertEquals(
            commonR.string.connection_check_error_invalid_url,
            (finalState.dnsResolution as ConnectivityCheckResult.Failure).messageResId,
        )

        // Checker should not be called for invalid URLs
        coVerify(exactly = 0) { checker.dns(any()) }

        // Remaining checks should be marked as skipped
        listOf(
            finalState.portReachability,
            finalState.tlsCertificate,
            finalState.serverConnection,
            finalState.homeAssistantVerification,
        ).forEach { result ->
            assertTrue(result is ConnectivityCheckResult.Failure)
            assertEquals(
                commonR.string.connection_check_skipped,
                (result as ConnectivityCheckResult.Failure).messageResId,
            )
        }
    }

    @Test
    fun `Given HTTP URL when running checks then default port 80 is used and TLS check is bypassed`() = runTest {
        // Given
        val testUrl = "http://example.com"
        coEvery { checker.dns("example.com") } returns ConnectivityCheckResult.Success(commonR.string.connection_check_dns, "192.0.2.1")
        coEvery { checker.port("example.com", 80) } returns ConnectivityCheckResult.Failure(
            commonR.string.connection_check_error_port,
        )
        coEvery { checker.server(testUrl) } returns ConnectivityCheckResult.Failure(
            commonR.string.connection_check_error_server,
        )

        // When
        val states = repository.runChecks(testUrl).toList()

        // Then
        val finalState = states.last()

        // DNS should succeed
        assertTrue(finalState.dnsResolution is ConnectivityCheckResult.Success)

        // Port should fail with correct error message
        assertTrue(finalState.portReachability is ConnectivityCheckResult.Failure)
        val portError = finalState.portReachability as ConnectivityCheckResult.Failure
        assertEquals(commonR.string.connection_check_error_port, portError.messageResId)

        // TLS should be not applicable for HTTP
        assertTrue(finalState.tlsCertificate is ConnectivityCheckResult.NotApplicable)

        // State management
        assertTrue(finalState.isComplete)
        assertTrue(finalState.hasFailure)

        // Verify default HTTP port 80 was used
        coVerify { checker.port("example.com", 80) }
        // Verify TLS was NOT called for HTTP
        coVerify(exactly = 0) { checker.tls(any()) }
    }

    @Test
    fun `Given flow emits states when running checks then initial state is emitted first`() = runTest {
        // Given
        val testUrl = "https://example.com"
        coEvery { checker.dns("example.com") } returns ConnectivityCheckResult.Success(commonR.string.connection_check_dns, "192.0.2.1")
        coEvery { checker.port("example.com", 443) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_port, "443")
        coEvery { checker.tls(testUrl) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_tls_success)
        coEvery { checker.server(testUrl) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_server_success)
        coEvery { checker.homeAssistant(testUrl) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_home_assistant_success)

        // When
        val states = repository.runChecks(testUrl).toList()

        // Then
        assertTrue(states.isNotEmpty())

        // First state should have all pending
        val initialState = states.first()
        assertEquals(ConnectivityCheckResult.Pending, initialState.dnsResolution)
        assertEquals(ConnectivityCheckResult.Pending, initialState.portReachability)
        assertEquals(ConnectivityCheckResult.Pending, initialState.tlsCertificate)
        assertEquals(ConnectivityCheckResult.Pending, initialState.serverConnection)
        assertEquals(ConnectivityCheckResult.Pending, initialState.homeAssistantVerification)
    }

    @Test
    fun `Given flow emits states when running checks then InProgress states are emitted`() = runTest {
        // Given
        val testUrl = "https://example.com"
        coEvery { checker.dns("example.com") } returns ConnectivityCheckResult.Success(commonR.string.connection_check_dns, "192.0.2.1")
        coEvery { checker.port("example.com", 443) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_port, "443")
        coEvery { checker.tls(testUrl) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_tls_success)
        coEvery { checker.server(testUrl) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_server_success)
        coEvery { checker.homeAssistant(testUrl) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_home_assistant_success)

        // When
        val states = repository.runChecks(testUrl).toList()

        // Then
        // Verify DNS check goes to InProgress
        val dnsInProgressState = states.firstOrNull { it.dnsResolution is ConnectivityCheckResult.InProgress }
        assertNotNull(dnsInProgressState)

        // Verify port check goes to InProgress
        val portInProgressState = states.firstOrNull { it.portReachability is ConnectivityCheckResult.InProgress }
        assertNotNull(portInProgressState)

        // Verify TLS check goes to InProgress
        val tlsInProgressState = states.firstOrNull { it.tlsCertificate is ConnectivityCheckResult.InProgress }
        assertNotNull(tlsInProgressState)

        // Verify server connection check goes to InProgress
        val serverInProgressState = states.firstOrNull { it.serverConnection is ConnectivityCheckResult.InProgress }
        assertNotNull(serverInProgressState)

        // Verify Home Assistant verification check goes to InProgress
        val haInProgressState = states.firstOrNull { it.homeAssistantVerification is ConnectivityCheckResult.InProgress }
        assertNotNull(haInProgressState)
    }

    @Test
    fun `Given URL with custom port when parsed then custom port is extracted`() = runTest {
        // Given
        val testUrl = "https://example.com:8123"
        coEvery { checker.dns("example.com") } returns ConnectivityCheckResult.Success(commonR.string.connection_check_dns, "192.0.2.1")
        coEvery { checker.port("example.com", 8123) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_port, "8123")
        coEvery { checker.tls(testUrl) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_tls_success)
        coEvery { checker.server(testUrl) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_server_success)
        coEvery { checker.homeAssistant(testUrl) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_home_assistant_success)

        // When
        val states = repository.runChecks(testUrl).toList()

        // Then
        val finalState = states.last()
        assertTrue(finalState.isComplete)

        // Verify custom port 8123 was used
        coVerify { checker.port("example.com", 8123) }
    }

    @Test
    fun `Given HTTPS URL without port when running checks then default port 443 is used`() = runTest {
        // Given
        val testUrl = "https://example.com"
        coEvery { checker.dns("example.com") } returns ConnectivityCheckResult.Success(commonR.string.connection_check_dns, "192.0.2.1")
        coEvery { checker.port("example.com", 443) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_port, "443")
        coEvery { checker.tls(testUrl) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_tls_success)
        coEvery { checker.server(testUrl) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_server_success)
        coEvery { checker.homeAssistant(testUrl) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_home_assistant_success)

        // When
        val states = repository.runChecks(testUrl).toList()

        // Then
        val finalState = states.last()
        assertTrue(finalState.isComplete)

        // Verify default HTTPS port 443 was used
        coVerify(exactly = 1) { checker.port("example.com", 443) }
    }

    @Test
    fun `Given successful DNS resolution when running checks then IP addresses are included in success details`() = runTest {
        // Given
        val testUrl = "https://example.com"
        val ipAddresses = "192.0.2.1, 192.0.2.2"
        coEvery { checker.dns("example.com") } returns ConnectivityCheckResult.Success(commonR.string.connection_check_dns, ipAddresses)
        coEvery { checker.port("example.com", 443) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_port, "443")
        coEvery { checker.tls(testUrl) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_tls_success)
        coEvery { checker.server(testUrl) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_server_success)
        coEvery { checker.homeAssistant(testUrl) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_home_assistant_success)

        // When
        val states = repository.runChecks(testUrl).toList()

        // Then
        // Find state where DNS succeeded
        val dnsSuccessState = states.firstOrNull { it.dnsResolution is ConnectivityCheckResult.Success }

        // DNS should succeed
        assertNotNull(dnsSuccessState, "DNS resolution should succeed")

        val success = dnsSuccessState!!.dnsResolution as ConnectivityCheckResult.Success
        // Should contain IP address information
        assertNotNull(success.details)
        assertEquals(ipAddresses, success.details)
    }

    @Test
    fun `Given any check fails when checking state then hasFailure returns true`() = runTest {
        // Given
        val testUrl = "https://example.com"
        coEvery { checker.dns("example.com") } returns ConnectivityCheckResult.Success(commonR.string.connection_check_dns, "192.0.2.1")
        coEvery { checker.port("example.com", 443) } returns ConnectivityCheckResult.Failure(
            commonR.string.connection_check_error_port,
        )
        coEvery { checker.tls(testUrl) } returns ConnectivityCheckResult.Failure(
            commonR.string.connection_check_error_tls,
        )
        coEvery { checker.server(testUrl) } returns ConnectivityCheckResult.Failure(
            commonR.string.connection_check_error_server,
        )

        // When
        val states = repository.runChecks(testUrl).toList()

        // Then
        val finalState = states.last()

        // Should have at least one failure
        assertTrue(finalState.hasFailure)
    }

    @Test
    fun `Given URL with path and query params when running checks then checks execute correctly`() = runTest {
        // Given
        val testUrl = "https://example.com:8123/api/websocket?token=abc123"
        coEvery { checker.dns("example.com") } returns ConnectivityCheckResult.Success(commonR.string.connection_check_dns, "192.0.2.1")
        coEvery { checker.port("example.com", 8123) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_port, "8123")
        coEvery { checker.tls(testUrl) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_tls_success)
        coEvery { checker.server(testUrl) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_server_success)
        coEvery { checker.homeAssistant(testUrl) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_home_assistant_success)

        // When
        val states = repository.runChecks(testUrl).toList()

        // Then
        val finalState = states.last()

        // DNS should succeed
        assertTrue(finalState.dnsResolution is ConnectivityCheckResult.Success)

        // Should complete all checks
        assertTrue(finalState.isComplete)

        // Verify the full URL was passed to TLS, server and HA checks
        coVerify { checker.tls(testUrl) }
        coVerify { checker.server(testUrl) }
        coVerify { checker.homeAssistant(testUrl) }
    }

    @Test
    fun `Given DNS resolution fails when running checks then remaining checks are skipped`() = runTest {
        // Given
        val testUrl = "https://example.com"
        coEvery { checker.dns("example.com") } returns ConnectivityCheckResult.Failure(
            commonR.string.connection_check_error_dns,
        )

        // When
        val states = repository.runChecks(testUrl).toList()

        // Then
        val finalState = states.last()

        // DNS should fail with correct error
        assertTrue(finalState.dnsResolution is ConnectivityCheckResult.Failure)
        assertEquals(
            commonR.string.connection_check_error_dns,
            (finalState.dnsResolution as ConnectivityCheckResult.Failure).messageResId,
        )

        // Remaining checks should be marked as skipped (Failure with skip message)
        listOf(
            finalState.portReachability,
            finalState.tlsCertificate,
            finalState.serverConnection,
            finalState.homeAssistantVerification,
        ).forEach { result ->
            assertTrue(result is ConnectivityCheckResult.Failure)
            assertEquals(
                commonR.string.connection_check_skipped,
                (result as ConnectivityCheckResult.Failure).messageResId,
            )
        }

        // State management
        assertTrue(finalState.isComplete)
        assertTrue(finalState.hasFailure)

        // Verify only DNS was called, remaining checks were skipped
        coVerify(exactly = 1) { checker.dns("example.com") }
        coVerify(exactly = 0) { checker.port(any(), any()) }
        coVerify(exactly = 0) { checker.tls(any()) }
        coVerify(exactly = 0) { checker.server(any()) }
        coVerify(exactly = 0) { checker.homeAssistant(any()) }
    }

    @Test
    fun `Given all checks succeed when running checks then states are emitted in order`() = runTest {
        // Given
        val testUrl = "https://example.com"
        val hostname = URL(testUrl).host
        val dnsSuccess = ConnectivityCheckResult.Success(commonR.string.connection_check_dns, "192.0.2.1")
        val portSuccess = ConnectivityCheckResult.Success(commonR.string.connection_check_port, "443")
        val tlsSuccess = ConnectivityCheckResult.Success(commonR.string.connection_check_tls_success)
        val serverSuccess = ConnectivityCheckResult.Success(commonR.string.connection_check_server_success)
        val haSuccess = ConnectivityCheckResult.Success(commonR.string.connection_check_home_assistant_success)
        coEvery { checker.dns(hostname) } returns dnsSuccess
        coEvery { checker.port(hostname, 443) } returns portSuccess
        coEvery { checker.tls(testUrl) } returns tlsSuccess
        coEvery { checker.server(testUrl) } returns serverSuccess
        coEvery { checker.homeAssistant(testUrl) } returns haSuccess

        // When
        val states = repository.runChecks(testUrl).toList()

        // Then
        val pending = ConnectivityCheckState()
        val inProgress = ConnectivityCheckResult.InProgress
        val p = pending
        val expectedStates = listOf(
            p,
            p.with(dns = inProgress),
            p.with(dns = dnsSuccess),
            p.with(dns = dnsSuccess, port = inProgress),
            p.with(dns = dnsSuccess, port = portSuccess),
            p.with(dns = dnsSuccess, port = portSuccess, tls = inProgress),
            p.with(dns = dnsSuccess, port = portSuccess, tls = tlsSuccess),
            p.with(dns = dnsSuccess, port = portSuccess, tls = tlsSuccess, server = inProgress),
            p.with(dns = dnsSuccess, port = portSuccess, tls = tlsSuccess, server = serverSuccess),
            p.with(dns = dnsSuccess, port = portSuccess, tls = tlsSuccess, server = serverSuccess, ha = inProgress),
            p.with(dns = dnsSuccess, port = portSuccess, tls = tlsSuccess, server = serverSuccess, ha = haSuccess),
        )

        assertEquals(expectedStates, states)
        assertTrue(states.last().isComplete)
        assertFalse(states.last().hasFailure)

        coVerifyOrder {
            checker.dns(hostname)
            checker.port(hostname, 443)
            checker.tls(testUrl)
            checker.server(testUrl)
            checker.homeAssistant(testUrl)
        }
    }

    private fun ConnectivityCheckState.with(
        dns: ConnectivityCheckResult = dnsResolution,
        port: ConnectivityCheckResult = portReachability,
        tls: ConnectivityCheckResult = tlsCertificate,
        server: ConnectivityCheckResult = serverConnection,
        ha: ConnectivityCheckResult = homeAssistantVerification,
    ) = copy(
        dnsResolution = dns,
        portReachability = port,
        tlsCertificate = tls,
        serverConnection = server,
        homeAssistantVerification = ha,
    )

    @Test
    fun `Given server is not Home Assistant when running checks then HA verification fails`() = runTest {
        // Given
        val testUrl = "https://example.com"
        coEvery { checker.dns("example.com") } returns ConnectivityCheckResult.Success(commonR.string.connection_check_dns, "192.0.2.1")
        coEvery { checker.port("example.com", 443) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_port, "443")
        coEvery { checker.tls(testUrl) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_tls_success)
        coEvery { checker.server(testUrl) } returns ConnectivityCheckResult.Success(commonR.string.connection_check_server_success)
        coEvery { checker.homeAssistant(testUrl) } returns ConnectivityCheckResult.Failure(
            commonR.string.connection_check_error_not_home_assistant,
        )

        // When
        val states = repository.runChecks(testUrl).toList()

        // Then
        val finalState = states.last()

        // All checks up to HA should succeed
        assertTrue(finalState.dnsResolution is ConnectivityCheckResult.Success)
        assertTrue(finalState.portReachability is ConnectivityCheckResult.Success)
        assertTrue(finalState.tlsCertificate is ConnectivityCheckResult.Success)
        assertTrue(finalState.serverConnection is ConnectivityCheckResult.Success)

        // HA verification should fail
        assertTrue(finalState.homeAssistantVerification is ConnectivityCheckResult.Failure)
        assertEquals(
            commonR.string.connection_check_error_not_home_assistant,
            (finalState.homeAssistantVerification as ConnectivityCheckResult.Failure).messageResId,
        )

        // State management
        assertTrue(finalState.isComplete)
        assertTrue(finalState.hasFailure)
    }
}
