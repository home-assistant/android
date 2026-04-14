package io.homeassistant.companion.android.mediacontrol

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Thin ViewModel whose sole purpose is to call [HaMediaSessionService.start] from a composable
 * while keeping the call site free of direct service references.
 *
 * The [Context] is supplied by the call site (Activity/composable) rather than held here, keeping
 * this ViewModel free of Android context references.
 */
@HiltViewModel
internal class MediaControlStarterViewModel @Inject constructor() : ViewModel() {

    /**
     * Starts [HaMediaSessionService]. If no entities are configured the service will stop itself
     * immediately; otherwise it begins observing and reconciling sessions.
     *
     * @param context A foreground context (e.g. from [androidx.compose.ui.platform.LocalContext])
     *   required by Android 15+ restrictions on starting mediaPlayback foreground services.
     */
    fun startIfConfigured(context: Context) {
        HaMediaSessionService.start(context)
    }
}
