package io.homeassistant.companion.android.common.data.integration.impl

import io.homeassistant.companion.android.common.data.integration.ZoneAttributes
import io.homeassistant.companion.android.common.data.integration.impl.entities.CheckRateLimits
import io.homeassistant.companion.android.common.data.integration.impl.entities.EntityResponse
import io.homeassistant.companion.android.common.data.integration.impl.entities.IntegrationRequest
import io.homeassistant.companion.android.common.data.integration.impl.entities.RateLimitRequest
import io.homeassistant.companion.android.common.data.integration.impl.entities.RegisterDeviceRequest
import io.homeassistant.companion.android.common.data.integration.impl.entities.RegisterDeviceResponse
import okhttp3.HttpUrl
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Url

interface IntegrationService {

    @POST("/api/mobile_app/registrations")
    suspend fun registerDevice(
        @Header("Authorization") auth: String,
        @Body request: RegisterDeviceRequest
    ): RegisterDeviceResponse

    @GET("/api/states/{entityId}")
    suspend fun getState(
        @Header("Authorization") auth: String,
        @Path("entityId") entityId: String
    ): EntityResponse<Map<String, Any>>

    @POST
    suspend fun callWebhook(
        @Url url: HttpUrl,
        @Body request: IntegrationRequest
    ): Response<ResponseBody>

    @POST
    suspend fun getTemplate(
        @Url url: HttpUrl,
        @Body request: IntegrationRequest
    ): Map<String, String>

    @POST
    suspend fun getZones(
        @Url url: HttpUrl,
        @Body request: IntegrationRequest
    ): Array<EntityResponse<ZoneAttributes>>

    @POST
    suspend fun getRateLimit(
        @Url url: String,
        @Body request: RateLimitRequest
    ): CheckRateLimits

    @POST
    suspend fun updateSensors(
        @Url url: HttpUrl,
        @Body request: IntegrationRequest
    ): Map<String, Map<String, Any>>
}
