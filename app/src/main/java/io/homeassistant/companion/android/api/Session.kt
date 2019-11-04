package io.homeassistant.companion.android.api

import android.app.Application
import android.content.Context
import java.util.*


class Session private constructor(application: Application) {

    companion object {
        private const val PREF_URL = "url"
        private const val PREF_ACCESS_TOKEN = "access_token"
        private const val PREF_EXPIRED_DATE = "expires_date"
        private const val PREF_REFRESH_TOKEN = "refresh_token"
        private const val PREF_TOKEN_TYPE = "token_type"

        @Volatile
        private var INSTANCE: Session? = null

        fun init(application: Application) {
            INSTANCE = Session(application)
        }

        fun getInstance(): Session = INSTANCE ?: throw IllegalStateException("You should init the singleton first")
    }

    private val sharedPreferences = application.getSharedPreferences("session", Context.MODE_PRIVATE)

    var token: Token? = null
        private set
    var url: String? = null
        private set

    init {
        if (sharedPreferences.contains(PREF_URL) &&
            sharedPreferences.contains(PREF_ACCESS_TOKEN) && sharedPreferences.contains(PREF_EXPIRED_DATE) &&
            sharedPreferences.contains(PREF_REFRESH_TOKEN) && sharedPreferences.contains(PREF_TOKEN_TYPE)
        ) {
            url = sharedPreferences.getString(PREF_URL, null)!!
            token = Token(
                sharedPreferences.getString(PREF_ACCESS_TOKEN, "")!!,
                sharedPreferences.getLong(PREF_EXPIRED_DATE, 0),
                sharedPreferences.getString(PREF_REFRESH_TOKEN, null)!!,
                sharedPreferences.getString(PREF_TOKEN_TYPE, null)!!
            )
        }
    }

    fun registerSession(token: AuthorizationCode, url: String) {
        this.token = Token(token.accessToken, expiresInToTimestamp(token.expiresIn), token.refreshToken, token.tokenType).apply {
            saveSession(this)
        }
        this.url = url.apply {
            saveUrl(this)
        }
    }

    fun registerRefreshToken(refreshToken: RefreshToken, url: String) {
        token = (token?.copy(
            accessToken = refreshToken.accessToken,
            expiresTimestamp = expiresInToTimestamp(refreshToken.expiresIn),
            tokenType = refreshToken.tokenType
        ) ?: throw IllegalStateException("Unable "))
            .apply {
                saveSession(this)
                saveUrl(url)
            }
    }

    private fun saveSession(token: Token) {
        sharedPreferences.edit()
            .putString(PREF_ACCESS_TOKEN, token.accessToken)
            .putLong(PREF_EXPIRED_DATE, token.expiresTimestamp)
            .putString(PREF_REFRESH_TOKEN, token.refreshToken)
            .putString(PREF_TOKEN_TYPE, token.tokenType)
            .apply()
    }

    private fun saveUrl(url: String) {
        sharedPreferences.edit()
            .putString(PREF_URL, url)
            .apply()
    }

    private fun expiresInToTimestamp(expiresIn: Int) = Calendar.getInstance().timeInMillis / 1000 + expiresIn
}