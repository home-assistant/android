package io.homeassistant.companion.android.common.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent

/**
 * Neutralizes intent redirection on an [Intent] parsed from an untrusted `intent:` URI.
 *
 * A crafted `intent:` URI can embed an explicit component or selector. Android already
 * prevents an app from launching another app's non-exported components, so the only
 * redirection risk is a URI that explicitly targets one of *our own* non-exported
 * components to invoke it with attacker-chosen extras. When that is detected, the explicit
 * targeting is stripped so the intent can no longer reach the internal component.
 *
 * Launches of other apps and of our own exported components are left untouched, so
 * legitimate `intent:` deep links keep working.
 *
 * Prefer [Context.parseExternalIntentUri], which parses and sanitizes in a single call.
 *
 * A redirection here always signals either a tampered URI or a bug in our own code, since
 * we never legitimately target our internal components this way. It is therefore reported
 * through [FailFast].
 *
 * @return the same instance, mutated in place, for call chaining
 */
/*
 * Suppressing QueryPermissionsNeeded: this only resolves the intent to detect whether it targets one of
 * our own components, and an app can always see itself regardless of Android 11 package visibility, so no
 * <queries> declaration is required.
 */
@SuppressLint("QueryPermissionsNeeded")
fun Intent.stripSelfNonExportedTarget(context: Context): Intent = apply {
    val target = resolveActivityInfo(context.packageManager, 0)
    if (target != null && target.packageName == context.packageName && !target.exported) {
        FailFast.fail {
            "Blocked intent redirection to our own non-exported component ${component?.flattenToShortString()}"
        }
        component = null
        selector = null
    }
}
