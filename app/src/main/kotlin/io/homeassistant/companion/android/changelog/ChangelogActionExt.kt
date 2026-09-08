package io.homeassistant.companion.android.changelog

import android.content.Context
import io.homeassistant.companion.android.assist.AssistActivity
import io.homeassistant.companion.android.common.util.openUri
import io.homeassistant.companion.android.settings.SettingsActivity

/**
 * Performs this action of the changelog entry the user tapped.
 */
internal suspend fun ChangelogAction.perform(
    context: Context,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
) {
    when (this) {
        is ChangelogAction.OpenUrl -> context.openUri(url, onShowSnackbar)

        is ChangelogAction.OpenSettings -> context.startActivity(
            SettingsActivity.newInstance(context, deeplink),
        )

        is ChangelogAction.OpenWidgetConfig -> context.startActivity(widgetType.toConfigureIntent(context))

        ChangelogAction.OpenAssist -> context.startActivity(AssistActivity.newInstance(context))
    }
}
