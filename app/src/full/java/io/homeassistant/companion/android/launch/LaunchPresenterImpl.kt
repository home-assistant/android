package io.homeassistant.companion.android.launch

import android.os.Build
import android.util.Log
import com.google.firebase.iid.FirebaseInstanceId
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import java.lang.Exception
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
                        Build.VERSION.SDK_INT.toString(),
                        pushToken = FirebaseInstanceId.getInstance().instanceId.await().token
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Issue updating Registration", e)
                }
            }
    }
}
