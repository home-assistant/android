package io.homeassistant.companion.android.database.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

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
        session = ServerSessionInfo(),
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

        @Test
        fun `Given TemporaryServer then creates Server with correct values`() {
            val temporaryServer = TemporaryServer(
                externalUrl = "https://home.example.com",
                session = ServerSessionInfo(
                    accessToken = "access123",
                    refreshToken = "refresh456",
                    tokenExpiration = 1234567890L,
                    tokenType = "Bearer",
                    installId = "install789",
                ),
                allowInsecureConnection = false,
            )

            val server = Server.fromTemporaryServer(temporaryServer)

            assertEquals("", server._name)
            assertEquals("https://home.example.com", server.connection.externalUrl)
            assertEquals(false, server.connection.allowInsecureConnection)
            assertEquals("access123", server.session.accessToken)
            assertEquals("refresh456", server.session.refreshToken)
            assertEquals(1234567890L, server.session.tokenExpiration)
            assertEquals("Bearer", server.session.tokenType)
            assertEquals("install789", server.session.installId)
        }

        @Test
        fun `Given TemporaryServer with null allowInsecureConnection then Server preserves null`() {
            val temporaryServer = TemporaryServer(
                externalUrl = "http://192.168.1.1:8123",
                session = ServerSessionInfo(),
                allowInsecureConnection = null,
            )

            val server = Server.fromTemporaryServer(temporaryServer)

            assertNull(server.connection.allowInsecureConnection)
        }

        @Test
        fun `Given TemporaryServer then Server has default id of 0`() {
            val temporaryServer = TemporaryServer(
                externalUrl = "https://example.com",
                session = ServerSessionInfo(),
                allowInsecureConnection = null,
            )

            val server = Server.fromTemporaryServer(temporaryServer)

            assertEquals(0, server.id)
        }

        @Test
        fun `Given TemporaryServer then Server has empty user info`() {
            val temporaryServer = TemporaryServer(
                externalUrl = "https://example.com",
                session = ServerSessionInfo(),
                allowInsecureConnection = null,
            )

            val server = Server.fromTemporaryServer(temporaryServer)

            assertEquals(ServerUserInfo(), server.user)
        }
    }
}
