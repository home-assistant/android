package io.homeassistant.companion.android.common.data.authentication.impl

import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowAuthentication
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowForm
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowMfaCode
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowRequest
import io.homeassistant.companion.android.common.data.authentication.impl.entities.Token
import okhttp3.HttpUrl
import okhttp3.ResponseBody
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
    @POST
    suspend fun getToken(
        @Url url: HttpUrl,
        @Field("grant_type") grandType: String,
        @Field("code") code: String,
        @Field("client_id") clientId: String
    ): Token

    @FormUrlEncoded
    @POST
    suspend fun refreshToken(
        @Url url: HttpUrl,
        @Field("grant_type") grandType: String,
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String
    ): Response<Token>

    @FormUrlEncoded
    @POST
    suspend fun revokeToken(
        @Url url: HttpUrl,
        @Field("token") refreshToken: String,
        @Field("action") action: String
    )

    @POST
    suspend fun initializeLogin(@Url url: String, @Body body: LoginFlowRequest): LoginFlowForm

    @POST
    suspend fun authenticatePassword(@Url url: HttpUrl, @Body body: LoginFlowAuthentication): Response<ResponseBody>

    @POST
    suspend fun authenticateMfa(@Url url: HttpUrl, @Body body: LoginFlowMfaCode): Response<ResponseBody>
}
