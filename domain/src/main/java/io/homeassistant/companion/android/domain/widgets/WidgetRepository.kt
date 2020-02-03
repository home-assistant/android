package io.homeassistant.companion.android.domain.widgets

interface WidgetRepository {

    suspend fun saveServiceCallData(
        appWidgetId: Int,
        domainStr: String,
        serviceStr: String,
        serviceDataCount: Int,
        serviceDataArray: Array<String>,
        serviceFieldArray: Array<String>
    )

    suspend fun loadDomain(appWidgetId: Int): String?
    suspend fun loadService(appWidgetId: Int): String?
    suspend fun loadServiceData(appWidgetId: Int): HashMap<String, Any>?

    suspend fun loadIcon(appWidgetId: Int): String?
    suspend fun loadLabel(appWidgetId: Int): String?

    suspend fun saveIcon(appWidgetId: Int, resName: String?)
    suspend fun saveLabel(appWidgetId: Int, data: String?)

    suspend fun deleteWidgetData(appWidgetId: Int)
}
