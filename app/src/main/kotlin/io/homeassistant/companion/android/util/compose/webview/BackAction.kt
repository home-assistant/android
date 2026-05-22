package io.homeassistant.companion.android.util.compose.webview

import android.net.Uri
import android.webkit.WebView
import androidx.core.net.toUri
import io.homeassistant.companion.android.util.hasNonRootPath
import io.homeassistant.companion.android.util.hasSameOrigin

/**
 * Determines the appropriate back action based on the [WebView]'s back/forward list
 * and the current loaded URL.
 *
 * The resolution logic:
 * 1. If the previous back-stack entry is a same-origin HTTP URL, returns
 *    [BackAction.GoBack] so the user can navigate back normally.
 * 2. If there is a previous back-stack entry that is not same-origin HTTP and
 *    the current URL has a non-root path, returns [BackAction.NavigateToRoot]
 *    so the user is taken to the home page first.
 * 3. Otherwise returns [BackAction.None] — the caller decides what to do
 *    (e.g. exit the activity, pop the navigation stack, or let the system
 *    handle back to show the predictive-back animation).
 *
 * @param webView the WebView whose back/forward list is inspected
 * @param loadedUrl the current URL shown in the WebView (as tracked by the caller,
 *        not necessarily [WebView.getUrl] which can be `about:blank` during loads)
 */
fun resolveBackAction(webView: WebView, loadedUrl: Uri?): BackAction {
    val previousUrl = if (webView.canGoBack()) {
        val backForwardList = webView.copyBackForwardList()
        val previousIndex = backForwardList.currentIndex - 1
        if (previousIndex >= 0) {
            backForwardList.getItemAtIndex(previousIndex).url.toUri()
        } else {
            null
        }
    } else {
        null
    }
    return resolveBackAction(previousUrl, loadedUrl)
}

private fun resolveBackAction(previousUrl: Uri?, loadedUrl: Uri?): BackAction {
    if (previousUrl != null &&
        loadedUrl != null &&
        previousUrl.scheme?.startsWith("http") == true &&
        previousUrl.hasSameOrigin(loadedUrl)
    ) {
        return BackAction.GoBack
    }

    if (previousUrl != null && loadedUrl != null && loadedUrl.hasNonRootPath()) {
        val rootUrl = loadedUrl.buildUpon()
            .path("/")
            .clearQuery()
            .appendQueryParameter("external_auth", "1")
            .fragment(null)
            .build()
        return BackAction.NavigateToRoot(rootUrl)
    }

    return BackAction.None
}

/**
 * Represents the action to take when the user presses back in a WebView.
 */
sealed interface BackAction {
    /** Navigate back in the WebView history. */
    data object GoBack : BackAction

    /** Clear history and navigate to the root URL of the current server. */
    data class NavigateToRoot(val rootUrl: Uri) : BackAction

    /** No more back navigation possible — the caller decides what to do. */
    data object None : BackAction
}
