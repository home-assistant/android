package io.homeassistant.companion.android.domain.widgets

import javax.inject.Inject

class WidgetUseCaseImpl @Inject constructor(
    private val widgetRepository: WidgetRepository
) : WidgetUseCase {

    companion object {
        internal const val PREF_PREFIX_KEY = "widget_"
        internal const val PREF_KEY_DOMAIN = "domain"
        internal const val PREF_KEY_SERVICE = "service"
        internal const val PREF_KEY_SERVICE_DATA = "serviceData"
        internal const val PREF_KEY_LABEL = "label"
    }

    override suspend fun saveServiceCallData(
        appWidgetId: Int,
        domainStr: String,
        serviceStr: String,
        serviceDataStr: String
    ) {
        widgetRepository.saveStringPref(
            PREF_PREFIX_KEY + PREF_KEY_DOMAIN + appWidgetId,
            domainStr
        )
        widgetRepository.saveStringPref(
            PREF_PREFIX_KEY + PREF_KEY_SERVICE + appWidgetId,
            serviceStr
        )
        widgetRepository.saveStringPref(
            PREF_PREFIX_KEY + PREF_KEY_SERVICE_DATA + appWidgetId,
            serviceDataStr
        )
    }

    override suspend fun loadDomain(appWidgetId: Int): String? {
        return widgetRepository.loadStringPref(PREF_PREFIX_KEY + PREF_KEY_DOMAIN + appWidgetId)
    }

    override suspend fun loadService(appWidgetId: Int): String? {
        return widgetRepository.loadStringPref(PREF_PREFIX_KEY + PREF_KEY_SERVICE + appWidgetId)
    }

    override suspend fun loadServiceData(appWidgetId: Int): String? {
        return widgetRepository.loadStringPref(PREF_PREFIX_KEY + PREF_KEY_SERVICE_DATA + appWidgetId)
    }

    override suspend fun loadLabel(appWidgetId: Int): String? {
        return widgetRepository.loadStringPref(PREF_PREFIX_KEY + PREF_KEY_LABEL + appWidgetId)
    }

    override suspend fun saveLabel(appWidgetId: Int, data: String?) {
        widgetRepository.saveStringPref(PREF_PREFIX_KEY + PREF_KEY_LABEL + appWidgetId, data)
    }

    override suspend fun deleteWidgetData(appWidgetId: Int) {
        widgetRepository.saveStringPref(PREF_PREFIX_KEY + PREF_KEY_DOMAIN + appWidgetId, null)
        widgetRepository.saveStringPref(PREF_PREFIX_KEY + PREF_KEY_SERVICE + appWidgetId, null)
        widgetRepository.saveStringPref(PREF_PREFIX_KEY + PREF_KEY_SERVICE_DATA + appWidgetId, null)
        widgetRepository.saveStringPref(PREF_PREFIX_KEY + PREF_KEY_LABEL + appWidgetId, null)
    }
}
