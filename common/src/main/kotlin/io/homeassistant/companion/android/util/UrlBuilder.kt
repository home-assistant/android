package io.homeassistant.companion.android.util

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Singleton
class UrlBuilder @Inject constructor() {

    fun buildUrl(base: String, pathSegments: String, parameters: Map<String, String> = emptyMap()): String {
        val baseUrl = base.toHttpUrl()

        return baseUrl.run {
            HttpUrl.Builder()
                .scheme(scheme)
                .host(host)
                .port(port)
                .addPathSegments(pathSegments)
                .apply {
                    parameters.forEach { (key, value) ->
                        addEncodedQueryParameter(key, value)
                    }
                }
                .build()
                .toString()
        }
    }
}
