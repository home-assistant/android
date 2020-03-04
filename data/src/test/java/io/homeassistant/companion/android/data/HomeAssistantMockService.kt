package io.homeassistant.companion.android.data

import io.homeassistant.companion.android.domain.url.UrlRepository
import java.io.IOException
import java.net.URL
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class HomeAssistantMockService<T>(private val c: Class<T>) {

    private val mockServer: MockWebServer = MockWebServer()
    private val homeAssistantRetrofit = HomeAssistantRetrofit(object : UrlRepository {
        override suspend fun getApiUrls(): Array<URL> {
            return arrayOf(getUrl()!!)
        }

        override suspend fun saveRegistrationUrls(
            cloudHookUrl: String?,
            remoteUiUrl: String?,
            webhookId: String
        ) {
        }

        override suspend fun getUrl(isInternal: Boolean?): URL? {
            return mockServer.url("/").toUrl()
        }

        override suspend fun saveUrl(url: String, isInternal: Boolean?) {
        }

        override suspend fun getHomeWifiSsids(): Set<String> {
            return emptySet()
        }

        override suspend fun saveHomeWifiSsids(ssid: Set<String>) {
        }
    }).retrofit

    fun get(): T {
        return homeAssistantRetrofit.create(c)
    }

    fun getMockServer() = mockServer

    fun enqueueResponse(code: Int, file: String? = null) {
        val mockResponse = MockResponse()
        if (file != null) {
            mockResponse.setBody(getJsonFromFile(file))
        }

        mockServer.enqueue(mockResponse.setResponseCode(code))
    }

    fun takeRequest() = mockServer.takeRequest()

    private fun getJsonFromFile(file: String): String {
        val inputStreamResponse = this.javaClass.classLoader.getResourceAsStream(file)!!
        val size: Int
        return try {
            size = inputStreamResponse.available()
            val buffer = ByteArray(size)
            inputStreamResponse.read(buffer)
            inputStreamResponse.close()
            String(buffer)
        } catch (e: IOException) {
            throw RuntimeException()
        }
    }
}
