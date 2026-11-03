package io.homeassistant.companion.android.widgets.configure

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract shared by every widget configuration ViewModel, letting [WidgetConfigureScreen] drive the
 * common state collection and error-to-snackbar handling regardless of the concrete widget.
 */
internal interface WidgetConfigureViewModel<S : WidgetConfigureState> {
    val state: StateFlow<S>

    /** String resources to surface to the user as a snackbar. */
    val errors: Flow<Int>
}
