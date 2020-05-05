package io.homeassistant.companion.android.wear.navigation

import android.content.Context
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.firebase.iid.FirebaseInstanceId
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.util.extensions.await
import io.homeassistant.companion.android.util.extensions.catch
import io.homeassistant.companion.android.wear.BuildConfig
import io.homeassistant.companion.android.wear.R
import io.homeassistant.companion.android.wear.util.extensions.requireDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class NavigationPresenterImpl @Inject constructor(
    private val context: Context,
    private val view: NavigationView,
    private val integrationUseCase: IntegrationUseCase
) : NavigationPresenter {

    private val mainScope = CoroutineScope(Dispatchers.Main)

    override fun onViewReady() {
        mainScope.launch {
            val updated = withContext(Dispatchers.IO) { updateDevice() }
            if (!updated) {
                view.displayError(R.string.error_with_registration_short)
            }
        }
    }

    override fun getPages(): List<NavigationItem> = arrayListOf(
        NavigationItem(getString(R.string.page_actions), requireDrawable(R.drawable.ic_home_assistant), NavigationPage.ACTIONS),
        NavigationItem(getString(R.string.page_settings), requireDrawable(R.drawable.ic_settings), NavigationPage.SETTINGS)
    )

    private fun getString(@StringRes resourceId: Int) = context.getString(resourceId)
    private fun requireDrawable(@DrawableRes resourceId: Int) = context.requireDrawable(resourceId)

    private suspend fun updateDevice(): Boolean {
        val token = catch { FirebaseInstanceId.getInstance().instanceId.await() } ?: return false
        val result = catch { integrationUseCase.updateRegistration(
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            manufacturer = Build.MANUFACTURER ?: "UNKNOWN",
            model = Build.MODEL ?: "UNKNOWN",
            osVersion = Build.VERSION.SDK_INT.toString(),
            pushToken = token.token
        )}
        return result != null
    }

    override fun finish() {
        mainScope.cancel()
    }
}