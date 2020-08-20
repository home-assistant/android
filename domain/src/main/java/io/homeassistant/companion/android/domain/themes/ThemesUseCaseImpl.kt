package io.homeassistant.companion.android.domain.themes

import javax.inject.Inject

class ThemesUseCaseImpl @Inject constructor(
    private val themesRepository: ThemesRepository
) : ThemesUseCase {
    override suspend fun getCurrentTheme(): String? {
        return themesRepository.getCurrentTheme()
    }

    override suspend fun saveTheme(theme: String?) {
        theme?.let { themesRepository.saveTheme(it) }
    }
}
