package io.homeassistant.companion.android.data.widgets

import io.homeassistant.companion.android.data.LocalStorage
import io.homeassistant.companion.android.domain.widgets.WidgetRepository
import javax.inject.Inject
import javax.inject.Named

class WidgetRepositoryImpl @Inject constructor(
    @Named("widget") private val localStorage: LocalStorage
) : WidgetRepository {

    override suspend fun saveStringPref(key: String, data: String?) {
        localStorage.putString(key, data)
    }

    override suspend fun loadStringPref(key: String): String? {
        return localStorage.getString(key)
    }
}
