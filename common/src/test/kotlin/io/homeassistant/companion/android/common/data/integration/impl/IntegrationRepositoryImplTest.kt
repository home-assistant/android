package io.homeassistant.companion.android.common.data.integration.impl

import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.net.URL
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class IntegrationRepositoryImplTest {

    private val integrationService = mockk<IntegrationService>()
    private val serverManager = mockk<ServerManager>()
    private val serverID = 42
    private val server = mockk<Server>()
    private val serverConnection = mockk<ServerConnectionInfo>()
    private val localStorage = mockk<LocalStorage>()

    private lateinit var repository: IntegrationRepository

    @BeforeEach
    fun setUp() {
        coEvery { serverManager.getServer(serverID) } returns server
        every { server.connection } returns serverConnection
        every { serverConnection.getApiUrls() } returns listOf(URL("http://homeassistant:8123"))

        repository = IntegrationRepositoryImpl(integrationService, serverManager, serverID, localStorage, "", "", "", "")
    }

    @Test
    fun `Given an empty response when invoking renderTemplate then it returns an empty string`() = runTest {
        val expectedResult = ""
        coEvery { integrationService.getTemplate(any(), any()) } returns JsonObject(mapOf("template" to JsonPrimitive(expectedResult)))

        val result = repository.renderTemplate("whatever", emptyMap())

        assertEquals(expectedResult, result)
    }

    @Test
    fun `Given a valid response with a number when invoking renderTemplate then it returns a valid string`() = runTest {
        val expectedResult = 42
        coEvery { integrationService.getTemplate(any(), any()) } returns JsonObject(mapOf("template" to JsonPrimitive(expectedResult)))

        val result = repository.renderTemplate("whatever", emptyMap())

        assertEquals(expectedResult.toString(), result)
    }

    @Test
    fun `Given a valid response with a string when invoking renderTemplate then it returns a valid string`() = runTest {
        val expectedResult = "hello world"
        coEvery { integrationService.getTemplate(any(), any()) } returns JsonObject(mapOf("template" to JsonPrimitive(expectedResult)))

        val result = repository.renderTemplate("whatever", emptyMap())

        assertEquals(expectedResult, result)
    }

    @Test
    fun `Given a valid response with a boolean when invoking renderTemplate then it returns a valid string`() = runTest {
        val expectedResult = true
        coEvery { integrationService.getTemplate(any(), any()) } returns JsonObject(mapOf("template" to JsonPrimitive(expectedResult)))

        val result = repository.renderTemplate("whatever", emptyMap())

        assertEquals(expectedResult.toString(), result)
    }

    @Test
    fun `Given a valid response with a list when invoking renderTemplate then it returns a valid string`() = runTest {
        val expectedResult = listOf(true, false)
        coEvery { integrationService.getTemplate(any(), any()) } returns JsonObject(mapOf("template" to JsonArray(expectedResult.map { JsonPrimitive(it) })))

        val result = repository.renderTemplate("whatever", emptyMap())

        assertEquals("[true,false]", result)
    }
}
