package io.homeassistant.companion.android.data

import java.io.IOException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class HomeAssistantMockService<T>(private val c: Class<T>) {

    private val mockServer: MockWebServer = MockWebServer()
    private val homeAssistantRetrofit =
        HomeAssistantRetrofit(mockServer.url("/").toString()).retrofit

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
