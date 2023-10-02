package io.homeassistant.companion.android.util

import android.content.Context
import android.content.res.Resources
import io.homeassistant.companion.android.databinding.ActivityIntegrationBinding
import io.homeassistant.companion.android.databinding.ActivityManualSetupBinding

private const val FACTOR = 0.146467f // c = a * sqrt(2)

fun adjustInset(
    context: Context,
    integrationBinding: ActivityIntegrationBinding? = null,
    manualSetupBinding: ActivityManualSetupBinding? = null
) {
    if (context.resources.configuration.isScreenRound) {
        val inset = (FACTOR * Resources.getSystem().displayMetrics.widthPixels).toInt()
        val binding = integrationBinding ?: manualSetupBinding
        binding?.root?.setPadding(inset, inset, inset, inset)
    }
}
