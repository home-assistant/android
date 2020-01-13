package io.homeassistant.companion.android.domain.widgets

interface WidgetRepository {

    suspend fun saveStringPref(key: String, data: String?)

    suspend fun loadStringPref(key: String): String?
}
