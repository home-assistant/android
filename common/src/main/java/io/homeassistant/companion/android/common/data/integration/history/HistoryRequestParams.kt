package io.homeassistant.companion.android.common.data.integration.history

import okhttp3.HttpUrl

data class HistoryRequestParams(
    val timestamp: String? = null,
    val filterEntityIds: List<String>,
    val endTime: String? = null,
    val minimalResponse: Boolean = false,
    val noAttributes: Boolean = false,
    val significantChangesOnly: Boolean = false
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
        if (minimalResponse) {
            builder.addQueryParameter(MINIMAL_RESPONSE, null)
        }
        if (noAttributes) {
            builder.addQueryParameter(NO_ATTRIBUTES, null)
        }
        if (significantChangesOnly) {
            builder.addQueryParameter(SIGNIFICANT_CHANGES_ONLY, null)
        }
        return builder.build()
    }
}
