package io.homeassistant.companion.android.data.integration

import io.homeassistant.companion.android.data.integration.entities.DiscoveryInfoResponse
import io.homeassistant.companion.android.data.integration.entities.DomainResponse
import io.homeassistant.companion.android.data.integration.entities.EntityResponse
import io.homeassistant.companion.android.data.integration.entities.GetConfigResponse
import io.homeassistant.companion.android.data.integration.entities.IntegrationRequest
import io.homeassistant.companion.android.data.integration.entities.RegisterDeviceRequest
import io.homeassistant.companion.android.data.integration.entities.RegisterDeviceResponse
import io.homeassistant.companion.android.domain.integration.Panel
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

    @GET("/api/services")
    suspend fun getServices(
        @Header("Authorization") auth: String
    ): Array<DomainResponse>

    @GET("/api/states")
    suspend fun getStates(
        @Header("Authorization") auth: String
    ): Array<EntityResponse<Any>>

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
    suspend fun fireEvent(
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

    @POST
    suspend fun getPanels(
        @Url url: HttpUrl,
        @Body request: IntegrationRequest
    ): Array<Panel>

    @POST
    suspend fun registerSensor(
        @Url url: HttpUrl,
        @Body request: IntegrationRequest
    ): Response<ResponseBody>

    @POST
    suspend fun updateSensors(
        @Url url: HttpUrl,
        @Body request: IntegrationRequest
    ): Map<String, Map<String, Any>>
}
