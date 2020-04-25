package io.homeassistant.companion.android.wear.settings

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import io.homeassistant.companion.android.wear.R

class ProgressPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyle, defStyleRes) {

    init {
        layoutResource = R.layout.preference_progress
    }

}