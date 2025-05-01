package io.homeassistant.companion.android.util

import android.content.Context
import org.unifiedpush.android.connector.UnifiedPush

fun UnifiedPush.tryRegisterCurrentOrDefaultDistributor(context: Context): Boolean {
    var result = false
    tryUseCurrentOrDefaultDistributor(context) { success ->
        if (success) {
            register(context)
        }
        result = success
    }
    return result
}