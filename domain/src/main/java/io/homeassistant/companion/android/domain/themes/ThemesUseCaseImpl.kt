package io.homeassistant.companion.android.domain.themes

import javax.inject.Inject

class ThemesUseCaseImpl @Inject constructor(
    private val themesRepository: ThemesRepository
) : ThemesUseCase {
    override suspend fun getCurrentTheme(): String? {
        val theme = themesRepository.getCurrentTheme()
        return if (theme.isNullOrEmpty()) "light"
        else theme
    }

    override suspend fun saveTheme(theme: String?) {
        theme?.let { themesRepository.saveTheme(it) }
    }
}
