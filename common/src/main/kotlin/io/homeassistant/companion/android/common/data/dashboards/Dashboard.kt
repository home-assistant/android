package io.homeassistant.companion.android.common.data.dashboards

import androidx.annotation.StringRes

/** A dashboard of a Home Assistant server, opened at `/<urlPath>` in the frontend. */
data class Dashboard(val urlPath: String, val title: DashboardTitle)

/** The name to show for a [Dashboard]. */
sealed interface DashboardTitle {
    /** A title the user gave to their own dashboard. */
    data class Text(val value: String) : DashboardTitle

    /** The translated name of a dashboard built into Home Assistant. */
    data class Resource(@StringRes val resId: Int) : DashboardTitle
}
