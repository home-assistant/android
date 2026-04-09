package io.homeassistant.companion.android.util

import android.view.WindowManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Check if the state of the fragment is at least started,
 * meaning onStart has been called but the state has not reached onSavedInstanceState (yet).
 */
val Fragment.isStarted: Boolean
    get() = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

/**
 * Set the layout for a [BottomSheetDialogFragment] to a shared default for the app,
 * and expand it to full size by default instead of peek height only.
 *
 * Configures the behavior before the dialog is shown so the sheet starts directly
 * in the expanded state, avoiding a visible peek-then-expand animation.
 */
fun BottomSheetDialogFragment.setLayoutAndExpandedByDefault() {
    (dialog as? BottomSheetDialog)?.let { bottomSheetDialog ->
        bottomSheetDialog.behavior.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
        bottomSheetDialog.window?.apply {
            setDimAmount(0.03f)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }
}
