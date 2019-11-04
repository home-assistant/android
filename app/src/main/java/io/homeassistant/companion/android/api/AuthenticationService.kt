package io.homeassistant.companion.android.api

import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST


interface AuthenticationService {

    companion object {
        const val CLIENT_ID = "https://home-assistant.io/android"
    }

    @FormUrlEncoded
    @POST("auth/token")
    fun getToken(
        @Field("grant_type") grandType: String,
        @Field("code") code: String,
        @Field("client_id") clientId: String
    ): Call<AuthorizationCode>

    @FormUrlEncoded
    @POST("auth/token")
    fun refreshToken(
        @Field("grant_type") grandType: String,
        @Field("refresh_token") token: String,
        @Field("client_id") clientId: String
    ): Call<RefreshToken>

    @FormUrlEncoded
    @POST("auth/token")
    fun revokeToken(
        @Field("refresh_token") token: String,
        @Field("action") action: String
    ): Call<Void>

}