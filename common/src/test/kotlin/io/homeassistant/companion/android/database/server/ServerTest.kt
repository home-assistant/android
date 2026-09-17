package io.homeassistant.companion.android.database.server

import io.homeassistant.companion.android.datastore.ServerSession
import kotlin.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull

class ServerTest {

    private fun createServer(
        name: String = "Test Server",
        nameOverride: String? = null,
        version: String? = null,
        externalUrl: String = "https://example.com",
    ) = Server(
        id = 1,
        _name = name,
        nameOverride = nameOverride,
        _version = version,
        connection = ServerConnectionInfo(externalUrl = externalUrl),
        user = ServerUserInfo(),
    )

    @Nested
    inner class FriendlyName {

        @Test
        fun `Given nameOverride set then friendlyName returns nameOverride`() {
            val server = createServer(
                name = "Original Name",
                nameOverride = "Custom Name",
            )

            assertEquals("Custom Name", server.friendlyName)
        }

        @Test
        fun `Given no nameOverride and name set then friendlyName returns name`() {
            val server = createServer(
                name = "Server Name",
                nameOverride = null,
            )

            assertEquals("Server Name", server.friendlyName)
        }

        @Test
        fun `Given no nameOverride and blank name then friendlyName returns externalUrl`() {
            val server = createServer(
                name = "",
                nameOverride = null,
                externalUrl = "https://home.example.com",
            )

            assertEquals("https://home.example.com", server.friendlyName)
        }

        @Test
        fun `Given no nameOverride and whitespace-only name then friendlyName returns externalUrl`() {
            val server = createServer(
                name = "   ",
                nameOverride = null,
                externalUrl = "https://home.example.com",
            )

            assertEquals("https://home.example.com", server.friendlyName)
        }
    }

    @Nested
    inner class Version {

        @Test
        fun `Given valid version string then version returns HomeAssistantVersion`() {
            val server = createServer(version = "2025.12.1")

            val version = server.version
            assertEquals(2025, version?.year)
            assertEquals(12, version?.month)
            assertEquals(1, version?.release)
        }

        @Test
        fun `Given null version then version returns null`() {
            val server = createServer(version = null)

            assertNull(server.version)
        }

        @Test
        fun `Given invalid version string then version returns null`() {
            val server = createServer(version = "invalid")

            assertNull(server.version)
        }
    }

    @Nested
    inner class FromTemporaryServer {

        private fun temporaryServer(
            externalUrl: String = "https://home.example.com",
            allowInsecureConnection: Boolean?,
        ) = TemporaryServer(
            externalUrl = externalUrl,
            installId = "install789",
            session = ServerSession(
                accessToken = "access123",
                refreshToken = "refresh456",
                tokenExpiration = Instant.fromEpochSeconds(1234567890),
                tokenType = "Bearer",
            ),
            allowInsecureConnection = allowInsecureConnection,
        )

        @Test
        fun `Given TemporaryServer then creates Server with correct values`() {
            val temporaryServer = temporaryServer(allowInsecureConnection = false)

            val server = Server.fromTemporaryServer(temporaryServer)

            assertEquals("", server._name)
            assertEquals("https://home.example.com", server.connection.externalUrl)
            assertEquals(false, server.connection.allowInsecureConnection)
            // The tokens go to the session datastore, only the install stays on the row.
            assertEquals("install789", server.installId)
        }

        @Test
        fun `Given TemporaryServer with null allowInsecureConnection then Server preserves null`() {
            val temporaryServer = temporaryServer(
                externalUrl = "http://192.168.1.1:8123",
                allowInsecureConnection = null,
            )

            val server = Server.fromTemporaryServer(temporaryServer)

            assertNull(server.connection.allowInsecureConnection)
        }

        @Test
        fun `Given TemporaryServer then Server has default id of 0`() {
            val temporaryServer = temporaryServer(
                externalUrl = "https://example.com",
                allowInsecureConnection = null,
            )

            val server = Server.fromTemporaryServer(temporaryServer)

            assertEquals(0, server.id)
        }

        @Test
        fun `Given TemporaryServer then Server has empty user info`() {
            val temporaryServer = temporaryServer(
                externalUrl = "https://example.com",
                allowInsecureConnection = null,
            )

            val server = Server.fromTemporaryServer(temporaryServer)

            assertEquals(ServerUserInfo(), server.user)
        }
    }
}
