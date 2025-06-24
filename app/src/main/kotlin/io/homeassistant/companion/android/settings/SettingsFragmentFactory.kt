package io.homeassistant.companion.android.settings

import android.annotation.SuppressLint
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import io.homeassistant.companion.android.settings.language.LanguagesProvider
import javax.inject.Inject

class SettingsFragmentFactory @Inject constructor(
    private val settingsPresenter: SettingsPresenter,
    private val languagesProvider: LanguagesProvider,
) : FragmentFactory() {
    @SuppressLint("NewApi")
    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        return when (className) {
            SettingsFragment::class.java.name -> SettingsFragment(settingsPresenter, languagesProvider)
            else -> super.instantiate(classLoader, className)
        }
    }
}
