package io.homeassistant.companion.android.launch

import android.os.Build
import android.util.Log
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import javax.inject.Inject
import kotlinx.coroutines.launch

class LaunchPresenterImpl @Inject constructor(
    view: LaunchView,
    authenticationUseCase: AuthenticationUseCase,
    integrationUseCase: IntegrationUseCase
) : LaunchPresenterBase(view, authenticationUseCase, integrationUseCase) {
    override fun resyncRegistration() {
        mainScope.launch {
            try {
                integrationUseCase.updateRegistration(
                    "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    null,
                    Build.MANUFACTURER ?: "UNKNOWN",
                    Build.MODEL ?: "UNKNOWN",
                    Build.VERSION.SDK_INT.toString()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Issue updating Registration", e)
            }
        }
    }
}
