package io.homeassistant.companion.android.domain.widgets

import javax.inject.Inject

class WidgetUseCaseImpl @Inject constructor(
    private val widgetRepository: WidgetRepository
) : WidgetUseCase {

    override suspend fun saveServiceCallData(
        appWidgetId: Int,
        domainStr: String,
        serviceStr: String,
        entityIdStr: String?
    ) {
        widgetRepository.saveServiceCallData(
            appWidgetId,
            domainStr,
            serviceStr,
            entityIdStr
        )
    }

    override suspend fun loadDomain(appWidgetId: Int): String? {
        return widgetRepository.loadDomain(appWidgetId)
    }

    override suspend fun loadService(appWidgetId: Int): String? {
        return widgetRepository.loadService(appWidgetId)
    }

    override suspend fun loadEntityId(appWidgetId: Int): String? {
        return widgetRepository.loadServiceData(appWidgetId)
    }

    override suspend fun loadIcon(appWidgetId: Int): String? {
        return widgetRepository.loadIcon(appWidgetId)
    }

    override suspend fun loadLabel(appWidgetId: Int): String? {
        return widgetRepository.loadLabel(appWidgetId)
    }

    override suspend fun saveIcon(appWidgetId: Int, resName: String?) {
        widgetRepository.saveIcon(appWidgetId, resName)
    }

    override suspend fun saveLabel(appWidgetId: Int, data: String?) {
        widgetRepository.saveLabel(appWidgetId, data)
    }

    override suspend fun deleteWidgetData(appWidgetId: Int) {
        widgetRepository.deleteWidgetData(appWidgetId)
    }
}
