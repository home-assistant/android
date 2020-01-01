package io.homeassistant.companion.android.data.integration

import io.homeassistant.companion.android.domain.integration.ZoneAttributes
import okhttp3.HttpUrl
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface IntegrationService {

    @GET("/api/discovery_info")
    suspend fun discoveryInfo(
        @Header("Authorization") auth: String
    ): DiscoveryInfoResponse

    @POST("/api/mobile_app/registrations")
    suspend fun registerDevice(
        @Header("Authorization") auth: String,
        @Body request: RegisterDeviceRequest
    ): RegisterDeviceResponse

    @POST
    suspend fun updateRegistration(
        @Url url: HttpUrl,
        @Body request: IntegrationRequest
    ): Response<ResponseBody>

    @POST
    suspend fun updateLocation(
        @Url url: HttpUrl,
        @Body request: IntegrationRequest
    ): Response<ResponseBody>

    @POST
    suspend fun callService(
        @Url url: HttpUrl,
        @Body request: IntegrationRequest
    ): Response<ResponseBody>

    @POST
    suspend fun getZones(
        @Url url: HttpUrl,
        @Body request: IntegrationRequest
    ): Array<EntityResponse<ZoneAttributes>>

    @POST
    suspend fun getConfig(
        @Url url: HttpUrl,
        @Body request: IntegrationRequest
    ): GetConfigResponse
}
