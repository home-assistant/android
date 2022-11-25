package io.homeassistant.companion.android.common.data

import android.os.Build
import android.webkit.CookieManager
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.homeassistant.companion.android.common.BuildConfig
import io.homeassistant.companion.android.common.data.url.UrlRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class HomeAssistantApis @Inject constructor(
    private val urlRepository: UrlRepository,
    private val tlsHelper: TLSHelper
) {
    companion object {
        private const val LOCAL_HOST = "http://localhost/"
        private const val USER_AGENT = "User-Agent"
        val USER_AGENT_STRING = "Home Assistant/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})"

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
        builder.addNetworkInterceptor {
            it.proceed(
                it.request()
                    .newBuilder()
                    .header(USER_AGENT, USER_AGENT_STRING)
                    .build()
            )
        }

        builder.addInterceptor(
            RedirectInterceptor()
        )

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

        tlsHelper.setupOkHttpClientSSLSocketFactory(builder)

        return builder
    }

    val retrofit: Retrofit = Retrofit
        .Builder()
        .addConverterFactory(
            JacksonConverterFactory.create(
                ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                    .registerKotlinModule()
            )
        )
        .client(configureOkHttpClient(OkHttpClient.Builder()).build())
        .baseUrl(LOCAL_HOST)
        .build()

    val okHttpClient = configureOkHttpClient(OkHttpClient.Builder()).build()
}
