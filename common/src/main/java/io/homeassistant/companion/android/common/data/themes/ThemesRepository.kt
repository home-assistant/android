package io.homeassistant.companion.android.common.data.themes

interface ThemesRepository {
    suspend fun getCurrentTheme(): String?

    suspend fun saveTheme(theme: String)
}
