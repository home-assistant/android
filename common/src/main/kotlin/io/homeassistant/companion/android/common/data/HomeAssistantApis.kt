package io.homeassistant.companion.android.common.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.CookieManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.BuildConfig
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.di.OkHttpConfigurator
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class HomeAssistantApis @Inject constructor(
    private val tlsHelper: TLSHelper,
    @ApplicationContext private val appContext: Context,
    private val configurators: Set<@JvmSuppressWildcards OkHttpConfigurator>,
) {
    companion object {
        private const val LOCAL_HOST = "http://localhost/"
        const val USER_AGENT = "User-Agent"
        private val ANDROID_DETAILS = "Android ${Build.VERSION.RELEASE}; ${Build.MODEL}"
        val USER_AGENT_STRING = "Home Assistant/${BuildConfig.VERSION_NAME} ($ANDROID_DETAILS)"

        private const val CALL_TIMEOUT = 30L
        private const val READ_TIMEOUT = 30L
    }

    private fun configureOkHttpClient(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                },
            )
        }
        builder.addNetworkInterceptor {
            it.proceed(
                it.request()
                    .newBuilder()
                    .header(USER_AGENT, USER_AGENT_STRING)
                    .build(),
            )
        }

        val isWear = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
        if (!isWear) {
            var cookieManager: CookieManager? = null
            try {
                cookieManager = CookieManager.getInstance()
            } catch (e: Exception) {
                // Noop
            }
            if (cookieManager != null) {
                builder.cookieJar(CookieJarCookieManagerShim())
            }
        }

        builder.callTimeout(CALL_TIMEOUT, TimeUnit.SECONDS)
        builder.readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)

        tlsHelper.setupOkHttpClientSSLSocketFactory(builder)

        configurators.forEach {
            it(builder)
        }

        return builder
    }

    val retrofit: Retrofit = Retrofit
        .Builder()
        .addConverterFactory(
            kotlinJsonMapper.asConverterFactory(
                "application/json; charset=UTF-8".toMediaType(),
            ),
        )
        .client(configureOkHttpClient(OkHttpClient.Builder()).build())
        .baseUrl(LOCAL_HOST)
        .build()

    val okHttpClient = configureOkHttpClient(OkHttpClient.Builder()).build()
}
