package io.homeassistant.companion.android.domain.themes

interface ThemesUseCase {

    suspend fun getCurrentTheme(): String?

    suspend fun saveTheme(theme: String?)
}
