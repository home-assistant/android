package io.homeassistant.companion.android.common.data

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody

class RedirectInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (request.url.toString().contains("Home-Assistant-Companion-for-Android/releases/latest")) {
            //获取重定向的地址
            // https://github.com/nesror/Home-Assistant-Companion-for-Android/releases/tag/v2022-09-15
            val location = response.headers["location"]
            if (location != null) {
                val myBody: ResponseBody = location.toResponseBody()
                return response.newBuilder().body(myBody).build()
            }
            // https://github.com/nesror/Home-Assistant-Companion-for-Android/releases/download/v20220915/app-full-release.apk


        }
        return response
    }
}