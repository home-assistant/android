package io.homeassistant.companion.android.data

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import javax.inject.Inject
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

class HomeAssistantRetrofit @Inject constructor(url: String) {

    val retrofit: Retrofit = Retrofit
        .Builder()
        .addConverterFactory(
            JacksonConverterFactory.create(
                ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
                    .registerKotlinModule()
            )
        )
        .client(
            OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
                .build()
        )
        .baseUrl(url)
        .build()
}
