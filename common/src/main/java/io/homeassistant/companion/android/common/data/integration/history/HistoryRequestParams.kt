package io.homeassistant.companion.android.common.data.integration.history

import okhttp3.HttpUrl

data class HistoryRequestParams(
    val timestamp: String? = null,
    val filterEntityIds: List<String>,
    val endTime: String? = null,
    val minimalResponse: String? = null,
    val noAttributes: String? = null,
    val significantChangesOnly: String? = null
) {

    companion object {
        const val FILTER_ENTITY_IDS = "filter_entity_id"
        const val END_TIME = "end_time"
        const val MINIMAL_RESPONSE = "minimal_response"
        const val NO_ATTRIBUTES = "no_attributes"
        const val SIGNIFICANT_CHANGES_ONLY = "significant_changes_only"
    }

    fun addToUrl(url: HttpUrl): HttpUrl {
        val builder = url.newBuilder()
        if (timestamp != null) {
            builder.addPathSegments("$timestamp")
        }
        builder.addQueryParameter(FILTER_ENTITY_IDS, filterEntityIds.joinToString(separator = ","))
        if (endTime != null) {
            builder.addQueryParameter(END_TIME, endTime)
        }
        if (minimalResponse != null) {
            builder.addQueryParameter(MINIMAL_RESPONSE, minimalResponse)
        }
        if (noAttributes != null) {
            builder.addQueryParameter(NO_ATTRIBUTES, noAttributes)
        }
        if (significantChangesOnly != null) {
            builder.addQueryParameter(SIGNIFICANT_CHANGES_ONLY, significantChangesOnly)
        }
        return builder.build()
    }
}
