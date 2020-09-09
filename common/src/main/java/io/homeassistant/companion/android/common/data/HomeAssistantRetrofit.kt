package io.homeassistant.companion.android.common.data

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.homeassistant.companion.android.common.BuildConfig
import io.homeassistant.companion.android.common.data.url.UrlRepository
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

class HomeAssistantRetrofit @Inject constructor(urlRepository: UrlRepository) {
    companion object {
        private const val LOCAL_HOST = "http://localhost/"
    }

    val retrofit: Retrofit

    init {
        val trustManagerFactory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as KeyStore?)
        val trustManagers = trustManagerFactory.trustManagers
        if (trustManagers.size != 1 || trustManagers[0] !is X509TrustManager) {
            throw IllegalStateException(
                "Unexpected default trust managers:" + trustManagers!!.contentToString()
            )
        }
        val trustManager = trustManagers[0]

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)
        val sslSocketFactory = sslContext.socketFactory

        retrofit = Retrofit
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
                    .sslSocketFactory(sslSocketFactory, trustManager as X509TrustManager)
                    .apply {
                        if (BuildConfig.DEBUG) {
                            addInterceptor(HttpLoggingInterceptor().apply {
                                level = HttpLoggingInterceptor.Level.BODY
                            })
                        }
                    }.addInterceptor {
                        return@addInterceptor if (it.request().url.toString()
                                .contains(LOCAL_HOST)
                        ) {
                            val newRequest = runBlocking {
                                it.request().newBuilder()
                                    .url(
                                        it.request().url.toString()
                                            .replace(LOCAL_HOST, urlRepository.getUrl().toString())
                                    )
                                    .build()
                            }
                            it.proceed(newRequest)
                        } else {
                            it.proceed(it.request())
                        }
                    }
                    .readTimeout(30L, TimeUnit.SECONDS)
                    .build()
            )
            .baseUrl(LOCAL_HOST)
            .build()
    }
}
