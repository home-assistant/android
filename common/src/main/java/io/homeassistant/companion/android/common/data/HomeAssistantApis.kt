package io.homeassistant.companion.android.common.data

import android.os.Build
import android.webkit.CookieManager
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.homeassistant.companion.android.common.BuildConfig
import io.homeassistant.companion.android.common.data.url.UrlRepository
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class HomeAssistantApis @Inject constructor(private val urlRepository: UrlRepository) {
    companion object {
        private const val LOCAL_HOST = "http://localhost/"
        private const val USER_AGENT = "User-Agent"
        private const val USER_AGENT_STRING = "HomeAssistant/Android"

        private val CALL_TIMEOUT = 30L
        private val READ_TIMEOUT = 30L
    }

    private fun configureOkHttpClient(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
        }
        builder.addInterceptor {
            return@addInterceptor if (it.request().url.toString().contains(LOCAL_HOST)) {
                val newRequest = runBlocking {
                    it.request().newBuilder()
                        .url(
                            it.request().url.toString()
                                .replace(LOCAL_HOST, urlRepository.getUrl().toString())
                        )
                        .header(
                            USER_AGENT,
                            "$USER_AGENT_STRING ${Build.MODEL} ${BuildConfig.VERSION_NAME}"
                        )
                        .build()
                }
                it.proceed(newRequest)
            } else {
                it.proceed(it.request())
            }
        }
        // Only deal with cookies when on non wear device and for now I don't have a better
        // way to determine if we are really on wear os....
        // TODO: Please fix me.
        var cookieManager: CookieManager? = null
        try {
            cookieManager = CookieManager.getInstance()
        } catch (e: Exception) {
            // Noop
        }
        if (cookieManager != null) {
            builder.cookieJar(CookieJarCookieManagerShim())
        }
        builder.callTimeout(CALL_TIMEOUT, TimeUnit.SECONDS)
        builder.readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)

        return builder
    }

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
        .client(configureOkHttpClient(OkHttpClient.Builder()).build())
        .baseUrl(LOCAL_HOST)
        .build()

    val okHttpClient = configureOkHttpClient(OkHttpClient.Builder()).build()
}
