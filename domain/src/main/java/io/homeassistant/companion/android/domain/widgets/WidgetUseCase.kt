package io.homeassistant.companion.android.domain.widgets

interface WidgetUseCase {

    suspend fun saveServiceCallData(
        appWidgetId: Int,
        domainStr: String,
        serviceStr: String,
        serviceData: String
    )

    suspend fun saveStaticEntityData(
        appWidgetId: Int,
        entityId: String,
        attributeId: String?
    )

    suspend fun loadDomain(appWidgetId: Int): String?
    suspend fun loadService(appWidgetId: Int): String?
    suspend fun loadServiceData(appWidgetId: Int): String?

    suspend fun loadEntityId(appWidgetId: Int): String?
    suspend fun loadAttributeId(appWidgetId: Int): String?

    suspend fun loadIcon(appWidgetId: Int): String?
    suspend fun loadLabel(appWidgetId: Int): String?

    suspend fun saveIcon(appWidgetId: Int, resName: String?)
    suspend fun saveLabel(appWidgetId: Int, data: String?)

    suspend fun deleteWidgetData(appWidgetId: Int)
}
