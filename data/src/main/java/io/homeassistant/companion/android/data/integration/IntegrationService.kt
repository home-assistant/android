package io.homeassistant.companion.android.data.integration

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface IntegrationService {

    @POST("/api/mobile_app/registrations")
    suspend fun registerDevice(
        @Header("Authorization") auth: String,
        @Body request: RegisterDeviceRequest
    ): RegisterDeviceResponse

}
