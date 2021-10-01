package io.homeassistant.companion.android.common.data.authentication.impl

import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowAuthentication
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowCreateEntry
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowInit
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowRequest
import io.homeassistant.companion.android.common.data.authentication.impl.entities.Token
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Url

interface AuthenticationService {

    companion object {
        const val CLIENT_ID = "https://home-assistant.io/android"
        const val GRANT_TYPE_CODE = "authorization_code"
        const val GRANT_TYPE_REFRESH = "refresh_token"
        const val REVOKE_ACTION = "revoke"
        val HANDLER = listOf("homeassistant", null)
        const val AUTHENTICATE_BASE_PATH = "auth/login_flow/"
        const val AUTH_CALLBACK = "homeassistant://auth-callback"
    }

    @FormUrlEncoded
    @POST("auth/token")
    suspend fun getToken(
        @Field("grant_type") grandType: String,
        @Field("code") code: String,
        @Field("client_id") clientId: String
    ): Token

    @FormUrlEncoded
    @POST("auth/token")
    suspend fun refreshToken(
        @Field("grant_type") grandType: String,
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String
    ): Response<Token>

    @FormUrlEncoded
    @POST("auth/token")
    suspend fun revokeToken(
        @Field("token") refreshToken: String,
        @Field("action") action: String
    )

    @POST("auth/login_flow")
    suspend fun initializeLogin(@Body body: LoginFlowRequest): LoginFlowInit

    @POST
    suspend fun authenticate(@Url url: String, @Body body: LoginFlowAuthentication): LoginFlowCreateEntry
}
