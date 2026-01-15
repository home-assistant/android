package io.homeassistant.companion.android.database.server

import android.os.Parcelable
import androidx.room.ColumnInfo
import kotlinx.parcelize.Parcelize

@Parcelize
data class ServerSessionInfo(
    @ColumnInfo(name = "access_token")
    val accessToken: String? = null,
    @ColumnInfo(name = "refresh_token")
    val refreshToken: String? = null,
    @ColumnInfo(name = "token_expiration")
    val tokenExpiration: Long? = null,
    @ColumnInfo(name = "token_type")
    val tokenType: String? = null,
    @ColumnInfo(name = "install_id")
    val installId: String? = null,
) : Parcelable {
    fun isComplete() = accessToken != null &&
        refreshToken != null &&
        tokenExpiration != null &&
        tokenType != null &&
        installId != null

    fun isExpired() = expiresIn()?.let { it < 0 } ?: true

    fun expiresIn() = tokenExpiration?.let { tokenExpiration - System.currentTimeMillis() / 1000 }
}
