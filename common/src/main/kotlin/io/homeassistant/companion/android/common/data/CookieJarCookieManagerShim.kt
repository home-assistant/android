package io.homeassistant.companion.android.common.data

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

// Use cookies collected by webkit in the okhttp client.
//
// This can be useful for HomeAssistant setups that require cookies
// for authentication.
//
// By default okhttp doesn't handle cookies at all -- so here we use
// the builtin webkit CookieManager to handle policy and
// persistence. Any cookies that are set during the onboarding/login
// workflow are persisted and can be used for future API calls.

class CookieJarCookieManagerShim : CookieJar {

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookies: String = CookieManager.getInstance()
            ?.getCookie(url.toString()) ?: return emptyList()
        return cookies.split("; ").mapNotNull {
            Cookie.parse(url, it)
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val manager: CookieManager = CookieManager.getInstance() ?: return
        for (cookie in cookies) {
            manager.setCookie(url.toString(), cookie.toString(), null)
        }
    }
}
