package io.homeassistant.companion.android.database.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class ServerConnectionInfoTest {

    @Nested
    inner class HasHomeNetworkSetup {

        @Test
        fun `Given no home network configuration then hasHomeNetworkSetup is false`() {
            val connection = ServerConnectionInfo(
                externalUrl = "https://example.com",
                internalSsids = emptyList(),
                internalVpn = null,
                internalEthernet = null,
            )

            assertFalse(connection.hasHomeNetworkSetup)
        }

        @Test
        fun `Given internalSsids configured then hasHomeNetworkSetup is true`() {
            val connection = ServerConnectionInfo(
                externalUrl = "https://example.com",
                internalSsids = listOf("HomeWiFi"),
            )

            assertTrue(connection.hasHomeNetworkSetup)
        }

        @Test
        fun `Given internalVpn enabled then hasHomeNetworkSetup is true`() {
            val connection = ServerConnectionInfo(
                externalUrl = "https://example.com",
                internalVpn = true,
            )

            assertTrue(connection.hasHomeNetworkSetup)
        }

        @Test
        fun `Given no internalSsids and internalVpn disabled then hasHomeNetworkSetup is false`() {
            val connection = ServerConnectionInfo(
                externalUrl = "https://example.com",
                internalVpn = false,
            )

            assertFalse(connection.hasHomeNetworkSetup)
        }

        @Test
        fun `Given internalEthernet enabled then hasHomeNetworkSetup is true`() {
            val connection = ServerConnectionInfo(
                externalUrl = "https://example.com",
                internalEthernet = true,
            )

            assertTrue(connection.hasHomeNetworkSetup)
        }

        @Test
        fun `Given no internalSsids and internalVpn null and internalEthernet disabled then hasHomeNetworkSetup is false`() {
            val connection = ServerConnectionInfo(
                externalUrl = "https://example.com",
                internalEthernet = false,
            )

            assertFalse(connection.hasHomeNetworkSetup)
        }

        @Test
        fun `Given multiple home network options configured then hasHomeNetworkSetup is true`() {
            val connection = ServerConnectionInfo(
                externalUrl = "https://example.com",
                internalSsids = listOf("HomeWiFi"),
                internalVpn = true,
                internalEthernet = true,
            )

            assertTrue(connection.hasHomeNetworkSetup)
        }
    }

    @Nested
    inner class HasAtLeastOneUrl {

        @Test
        fun `Given valid externalUrl then hasAtLeastOneUrl is true`() {
            val connection = ServerConnectionInfo(
                externalUrl = "https://example.com",
            )

            assertTrue(connection.hasAtLeastOneUrl)
        }

        @Test
        fun `Given invalid externalUrl but valid internalUrl then hasAtLeastOneUrl is true`() {
            val connection = ServerConnectionInfo(
                externalUrl = "not-a-valid-url",
                internalUrl = "https://192.168.1.1:8123",
            )

            assertTrue(connection.hasAtLeastOneUrl)
        }

        @Test
        fun `Given invalid externalUrl but valid cloudUrl then hasAtLeastOneUrl is true`() {
            val connection = ServerConnectionInfo(
                externalUrl = "not-a-valid-url",
                cloudUrl = "https://cloud.example.com",
            )

            assertTrue(connection.hasAtLeastOneUrl)
        }

        @Test
        fun `Given invalid externalUrl but valid cloudhookUrl then hasAtLeastOneUrl is true`() {
            val connection = ServerConnectionInfo(
                externalUrl = "not-a-valid-url",
                cloudhookUrl = "https://hooks.nabu.casa/abc123",
            )

            assertTrue(connection.hasAtLeastOneUrl)
        }

        @Test
        fun `Given all invalid URLs then hasAtLeastOneUrl is false`() {
            val connection = ServerConnectionInfo(
                externalUrl = "not-a-valid-url",
                internalUrl = "also-invalid",
                cloudUrl = "still-invalid",
                cloudhookUrl = "nope",
            )

            assertFalse(connection.hasAtLeastOneUrl)
        }
    }

    @Nested
    inner class IsRegistered {

        @ParameterizedTest(name = "isRegistered is false: externalUrl={0}, webhookId={1}")
        @CsvSource(
            "https://example.com, null",
            "https://example.com, '   '",
            "not-a-valid-url, webhook123",
        )
        fun `Given invalid registration then isRegistered is false`(
            externalUrl: String,
            webhookId: String?,
        ) {
            val connection = ServerConnectionInfo(
                externalUrl = externalUrl,
                webhookId = webhookId?.takeIf { it != "null" },
            )

            assertFalse(connection.isRegistered)
        }

        @Test
        fun `Given webhookId and valid externalUrl then isRegistered is true`() {
            val connection = ServerConnectionInfo(
                externalUrl = "https://example.com",
                webhookId = "webhook123",
            )

            assertTrue(connection.isRegistered)
        }

        @Test
        fun `Given webhookId and valid internalUrl then isRegistered is true`() {
            val connection = ServerConnectionInfo(
                externalUrl = "invalid",
                internalUrl = "https://192.168.1.1:8123",
                webhookId = "webhook123",
            )

            assertTrue(connection.isRegistered)
        }

        @Test
        fun `Given webhookId and valid cloudhookUrl then isRegistered is true`() {
            val connection = ServerConnectionInfo(
                externalUrl = "invalid",
                cloudhookUrl = "https://hooks.nabu.casa/abc123",
                webhookId = "webhook123",
            )

            assertTrue(connection.isRegistered)
        }
    }

    @Nested
    inner class HasPlainTextUrl {

        @ParameterizedTest(name = "hasPlainTextUrl is true: external={0}, internal={1}, cloud={2}")
        @CsvSource(
            "http://example.com, null, null",
            "https://example.com, http://192.168.1.1:8123, null",
            "https://example.com, null, http://cloud.example.com",
        )
        fun `Given HTTP URL then hasPlainTextUrl is true`(
            externalUrl: String,
            internalUrl: String?,
            cloudUrl: String?,
        ) {
            val connection = ServerConnectionInfo(
                externalUrl = externalUrl,
                internalUrl = internalUrl?.takeIf { it != "null" },
                cloudUrl = cloudUrl?.takeIf { it != "null" },
            )

            assertTrue(connection.hasPlainTextUrl)
        }

        @ParameterizedTest(name = "hasPlainTextUrl is false: external={0}, internal={1}, cloud={2}")
        @CsvSource(
            "https://example.com, https://192.168.1.1:8123, https://cloud.example.com",
            "https://example.com, null, null",
        )
        fun `Given all HTTPS URLs then hasPlainTextUrl is false`(
            externalUrl: String,
            internalUrl: String?,
            cloudUrl: String?,
        ) {
            val connection = ServerConnectionInfo(
                externalUrl = externalUrl,
                internalUrl = internalUrl?.takeIf { it != "null" },
                cloudUrl = cloudUrl?.takeIf { it != "null" },
            )

            assertFalse(connection.hasPlainTextUrl)
        }
    }

    @Nested
    inner class CanUseCloud {

        @Test
        fun `Given no cloudUrl then canUseCloud is false`() {
            val connection = ServerConnectionInfo(
                externalUrl = "https://example.com",
                cloudUrl = null,
            )

            assertFalse(connection.canUseCloud)
        }

        @Test
        fun `Given blank cloudUrl then canUseCloud is false`() {
            val connection = ServerConnectionInfo(
                externalUrl = "https://example.com",
                cloudUrl = "   ",
            )

            assertFalse(connection.canUseCloud)
        }

        @Test
        fun `Given valid cloudUrl then canUseCloud is true`() {
            val connection = ServerConnectionInfo(
                externalUrl = "https://example.com",
                cloudUrl = "https://cloud.example.com",
            )

            assertTrue(connection.canUseCloud)
        }
    }

    @Nested
    inner class IsKnownUrl {
        private val connection = ServerConnectionInfo(
            externalUrl = "https://external.example.com",
            internalUrl = "http://192.168.1.1:8123",
            cloudUrl = "https://cloud.example.com",
        )

        @ParameterizedTest
        @ValueSource(
            strings = [
                "https://external.example.com/api/states",
                "http://192.168.1.1:8123/api/webhook/abc",
                "https://cloud.example.com/some/path",
            ],
        )
        fun `Given URL matching known URL then isKnownUrl returns true`(url: String) {
            assertTrue(connection.isKnownUrl(url))
        }

        @ParameterizedTest
        @ValueSource(strings = ["https://unknown.example.com/api", "not-a-valid-url"])
        fun `Given URL not matching then isKnownUrl returns false`(url: String) {
            assertFalse(connection.isKnownUrl(url))
        }
    }

    @Nested
    inner class InternalSsidTypeConverterTest {

        private val converter = InternalSsidTypeConverter()

        @Test
        fun `Given empty list then fromListToString returns empty array`() {
            assertEquals("[]", converter.fromListToString(emptyList()))
        }

        @Test
        fun `Given list with values then fromListToString returns JSON array`() {
            val result = converter.fromListToString(listOf("WiFi1", "WiFi2"))

            assertEquals("[\"WiFi1\",\"WiFi2\"]", result)
        }

        @ParameterizedTest
        @ValueSource(strings = ["    ", "[]"])
        fun `Given empty array string then fromStringToList returns empty list`(value: String) {
            assertEquals(emptyList<String>(), converter.fromStringToList(value))
        }

        @Test
        fun `Given JSON array string then fromStringToList returns list`() {
            val result = converter.fromStringToList("[\"WiFi1\",\"WiFi2\"]")

            assertEquals(listOf("WiFi1", "WiFi2"), result)
        }

        @Test
        fun `Given invalid JSON then fromStringToList returns empty list`() {
            assertEquals(emptyList<String>(), converter.fromStringToList("not valid json"))
        }

        @Test
        fun `Given round trip conversion then values are preserved`() {
            val original = listOf("Home WiFi", "Office Network")
            val serialized = converter.fromListToString(original)
            val deserialized = converter.fromStringToList(serialized)

            assertEquals(original, deserialized)
        }
    }
}
