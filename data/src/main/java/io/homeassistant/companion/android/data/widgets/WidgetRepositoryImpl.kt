package io.homeassistant.companion.android.data.widgets

import io.homeassistant.companion.android.data.LocalStorage
import io.homeassistant.companion.android.domain.widgets.WidgetRepository
import javax.inject.Inject
import javax.inject.Named

class WidgetRepositoryImpl @Inject constructor(
    @Named("widget") private val localStorage: LocalStorage
) : WidgetRepository {

    companion object {
        internal const val PREF_PREFIX_KEY = "widget_"
        internal const val PREF_KEY_DOMAIN = "domain"
        internal const val PREF_KEY_ENTITY = "entity"
        internal const val PREF_KEY_SERVICE = "service"
        internal const val PREF_KEY_ATTRIBUTE_ID = "attribute"
        internal const val PREF_KEY_SERVICE_DATA = "serviceData"
        internal const val PREF_KEY_ICON = "icon"
        internal const val PREF_KEY_LABEL = "label"
    }

    override suspend fun saveServiceCallData(
        appWidgetId: Int,
        domainStr: String,
        serviceStr: String,
        serviceDataStr: String
    ) {
        saveStringPref(
            PREF_PREFIX_KEY + PREF_KEY_DOMAIN + appWidgetId,
            domainStr
        )
        saveStringPref(
            PREF_PREFIX_KEY + PREF_KEY_SERVICE + appWidgetId,
            serviceStr
        )

        saveStringPref(
            PREF_PREFIX_KEY + PREF_KEY_SERVICE_DATA + appWidgetId,
            serviceDataStr
        )
    }

    override suspend fun saveEntityData(
        appWidgetId: Int,
        entityId: String,
        attributeId: String?
    ) {
        saveStringPref(
            PREF_PREFIX_KEY + PREF_KEY_ENTITY + appWidgetId,
            entityId
        )
        if (attributeId != null) saveStringPref(
            PREF_PREFIX_KEY + PREF_KEY_ATTRIBUTE_ID + appWidgetId,
            attributeId
        )
    }

    override suspend fun loadDomain(appWidgetId: Int): String? {
        return loadStringPref(PREF_PREFIX_KEY + PREF_KEY_DOMAIN + appWidgetId)
    }

    override suspend fun loadService(appWidgetId: Int): String? {
        return loadStringPref(PREF_PREFIX_KEY + PREF_KEY_SERVICE + appWidgetId)
    }

    override suspend fun loadEntityId(appWidgetId: Int): String? {
        return loadStringPref(PREF_PREFIX_KEY + PREF_KEY_ENTITY + appWidgetId)
    }

    override suspend fun loadAttributeId(appWidgetId: Int): String? {
        return loadStringPref(PREF_PREFIX_KEY + PREF_KEY_ATTRIBUTE_ID + appWidgetId)
    }

    override suspend fun loadServiceData(appWidgetId: Int): String? {
        return loadStringPref(PREF_PREFIX_KEY + PREF_KEY_SERVICE_DATA + appWidgetId)
    }

    override suspend fun loadIcon(appWidgetId: Int): String? {
        return loadStringPref(PREF_PREFIX_KEY + PREF_KEY_ICON + appWidgetId)
    }

    override suspend fun loadLabel(appWidgetId: Int): String? {
        return loadStringPref(PREF_PREFIX_KEY + PREF_KEY_LABEL + appWidgetId)
    }

    override suspend fun saveIcon(appWidgetId: Int, resName: String?) {
        saveStringPref(PREF_PREFIX_KEY + PREF_KEY_ICON + appWidgetId, resName)
    }

    override suspend fun saveLabel(appWidgetId: Int, data: String?) {
        saveStringPref(PREF_PREFIX_KEY + PREF_KEY_LABEL + appWidgetId, data)
    }

    override suspend fun deleteWidgetData(appWidgetId: Int) {
        saveStringPref(PREF_PREFIX_KEY + PREF_KEY_DOMAIN + appWidgetId, null)
        saveStringPref(PREF_PREFIX_KEY + PREF_KEY_SERVICE + appWidgetId, null)
        saveStringPref(PREF_PREFIX_KEY + PREF_KEY_SERVICE_DATA + appWidgetId, null)
        saveStringPref(PREF_PREFIX_KEY + PREF_KEY_ICON + appWidgetId, null)
        saveStringPref(PREF_PREFIX_KEY + PREF_KEY_LABEL + appWidgetId, null)
        saveStringPref(PREF_PREFIX_KEY + PREF_KEY_ENTITY + appWidgetId, null)
        saveStringPref(PREF_PREFIX_KEY + PREF_KEY_ATTRIBUTE_ID + appWidgetId, null)
    }

    private suspend fun saveStringPref(key: String, data: String?) {
        localStorage.putString(key, data)
    }

    private suspend fun saveBooleanPref(key: String, data: Boolean) {
        localStorage.putBoolean(key, data)
    }

    private suspend fun loadStringPref(key: String): String? {
        return localStorage.getString(key)
    }

    private suspend fun loadBooleanPref(key: String): Boolean {
        return localStorage.getBoolean(key)
    }
}
