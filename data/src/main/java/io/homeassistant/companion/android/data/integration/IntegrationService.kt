package io.homeassistant.companion.android.data.integration

import okhttp3.HttpUrl
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface IntegrationService {

    @POST("/api/mobile_app/registrations")
    suspend fun registerDevice(
        @Header("Authorization") auth: String,
        @Body request: RegisterDeviceRequest
    ): RegisterDeviceResponse

    @POST
    suspend fun updateLocation(
        @Url url: HttpUrl,
        @Body request: IntegrationRequest
    ): Response<ResponseBody>
}
