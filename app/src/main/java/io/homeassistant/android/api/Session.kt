package io.homeassistant.android.api

import android.app.Application
import android.content.Context


class Session private constructor(private val application: Application) {

    companion object {
        private const val PREF_URL = "url"
        private const val PREF_ACCESS_TOKEN = "access_token"
        private const val PREF_EXPIRED_IN = "expires_in"
        private const val PREF_REFRESH_TOKEN = "refresh_token"
        private const val PREF_TOKEN_TYPE = "token_type"

        @Volatile
        private var INSTANCE: Session? = null

        fun init(application: Application) {
            INSTANCE = Session(application)
        }

        fun getInstance(): Session = INSTANCE ?: throw IllegalStateException("You should init the singleton first")
    }

    var token: Token? = null
    var url: String? = null
    private val sharedPreferences = application.getSharedPreferences("session", Context.MODE_PRIVATE)

    init {
        if (sharedPreferences.contains(PREF_URL) &&
            sharedPreferences.contains(PREF_ACCESS_TOKEN) && sharedPreferences.contains(PREF_EXPIRED_IN) &&
            sharedPreferences.contains(PREF_REFRESH_TOKEN) && sharedPreferences.contains(PREF_TOKEN_TYPE)
        ) {
            url = sharedPreferences.getString(PREF_URL, null)
            token = Token(
                sharedPreferences.getString(PREF_ACCESS_TOKEN, "")!!,
                sharedPreferences.getInt(PREF_EXPIRED_IN, 0),
                sharedPreferences.getString(PREF_REFRESH_TOKEN, null)!!,
                sharedPreferences.getString(PREF_TOKEN_TYPE, null)!!
            )
        }
    }


    fun registerSession(token: Token, url: String) {
        this.token = token
        this.url = url

        sharedPreferences.edit()
            .putString(PREF_URL, url)
            .putString(PREF_ACCESS_TOKEN, token.accessToken)
            .putInt(PREF_EXPIRED_IN, token.expiresIn)
            .putString(PREF_REFRESH_TOKEN, token.refreshToken)
            .putString(PREF_TOKEN_TYPE, token.tokenType)
            .apply()
    }

}