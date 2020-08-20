package io.homeassistant.companion.android.domain.themes

interface ThemesRepository {
    suspend fun getCurrentTheme(): String?

    suspend fun saveTheme(theme: String)
}
