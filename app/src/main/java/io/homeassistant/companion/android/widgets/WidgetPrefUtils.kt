package io.homeassistant.companion.android.widgets

import android.content.Context

internal const val PREFS_NAME = "io.homeassistant.companion.android.widgets"
internal const val PREF_PREFIX_KEY = "widget_"
internal const val PREF_KEY_DOMAIN = "domain"
internal const val PREF_KEY_SERVICE = "service"
internal const val PREF_KEY_SERVICE_DATA = "serviceData"
internal const val PREF_KEY_LABEL = "label"

internal fun saveServiceCallData(
    context: Context,
    appWidgetId: Int,
    domainStr: String,
    serviceStr: String,
    serviceDataStr: String
) {
    saveStringPref(context, appWidgetId, PREF_KEY_DOMAIN, domainStr)
    saveStringPref(context, appWidgetId, PREF_KEY_SERVICE, serviceStr)
    saveStringPref(context, appWidgetId, PREF_KEY_SERVICE_DATA, serviceDataStr)
}

internal fun saveStringPref(context: Context, appWidgetId: Int, key: String, data: String?) {
    context.getSharedPreferences(PREFS_NAME, 0).edit()
        .putString(PREF_PREFIX_KEY + appWidgetId + key, data)
        .apply()
}

internal fun loadStringPref(context: Context, appWidgetId: Int, key: String): String? {
    return context.getSharedPreferences(PREFS_NAME, 0)
        .getString(PREF_PREFIX_KEY + appWidgetId + key, null)
}

internal fun deleteStringPref(context: Context, appWidgetId: Int, key: String) {
    saveStringPref(context, appWidgetId, key, null)
}
