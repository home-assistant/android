package io.homeassistant.companion.android.launcher

import org.junit.Test
import org.junit.jupiter.api.Assertions.assertFalse

class LauncherViewModelTest {

    @Test
    fun `Given viewModel creation when invoking shouldShowSplashScreen returns false`() {
        val viewModel = LauncherViewModel()
        assertFalse(viewModel.shouldShowSplashScreen())
    }
}
