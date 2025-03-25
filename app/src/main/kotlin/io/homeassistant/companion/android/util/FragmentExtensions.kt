package io.homeassistant.companion.android.util

import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.google.android.material.R
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
 * Set the layout for a BottomSheetDialogFragment to a shared default for the app,
 * and expand it to full size by default instead of peek height only.
 */
fun BottomSheetDialogFragment.setLayoutAndExpandedByDefault() {
    dialog?.setOnShowListener {
        val dialog = it as BottomSheetDialog
        dialog.window?.setDimAmount(0.03f)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val bottomSheet = dialog.findViewById<View>(R.id.design_bottom_sheet) as FrameLayout
        val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }
}
