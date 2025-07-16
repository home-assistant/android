package io.homeassistant.companion.android.util.icondialog

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.DialogFragment
import com.mikepenz.iconics.typeface.IIcon
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import kotlin.math.min

class IconDialogFragment(callback: (IIcon) -> Unit) : DialogFragment() {

    companion object {
        const val TAG = "IconDialogFragment"
    }

    private val onSelect = callback

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).also {
            it.clipToPadding = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (view as ComposeView).setContent {
            HomeAssistantAppTheme {
                IconDialogContent(
                    onSelect = onSelect,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val params = dialog?.window?.attributes ?: return
        params.width = min(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 480f, resources.displayMetrics).toInt(),
        )
        params.height = min(
            (resources.displayMetrics.heightPixels * 0.9).toInt(),
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 500f, resources.displayMetrics).toInt(),
        )
        dialog?.window?.attributes = params
    }
}
