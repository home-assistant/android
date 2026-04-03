package io.homeassistant.companion.android.mediacontrol

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlRepository
import javax.inject.Inject

/**
 * Thin ViewModel whose sole purpose is to hold [MediaControlRepository] for DI injection into
 * composables that need to call [HaMediaSessionService.startIfConfigured].
 *
 * The [Context] is supplied by the call site (Activity/composable) rather than held here, keeping
 * this ViewModel free of Android context references.
 */
@HiltViewModel
internal class MediaControlStarterViewModel @Inject constructor(
    private val mediaControlRepository: MediaControlRepository,
) : ViewModel() {

    /**
     * Starts [HaMediaSessionService] if any media_player entities are configured.
     *
     * @param context A foreground context (e.g. from [androidx.compose.ui.platform.LocalContext])
     *   required by Android 15+ restrictions on starting mediaPlayback foreground services.
     */
    suspend fun startIfConfigured(context: Context) {
        HaMediaSessionService.startIfConfigured(context, mediaControlRepository)
    }
}
