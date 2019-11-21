package io.homeassistant.companion.android.data

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import javax.inject.Inject
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

class HomeAssistantRetrofit @Inject constructor(url: String) {

    val retrofit: Retrofit = Retrofit
        .Builder()
        .addConverterFactory(
            JacksonConverterFactory.create(
                ObjectMapper()
                    .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
                    .registerKotlinModule()
            )
        )
        .baseUrl(url)
        .build()
}
