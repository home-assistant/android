package io.homeassistant.companion.android.common.data.prefs.impl.entities

import org.json.JSONObject

const val FIELD_TEMPLATE = "template"
const val FIELD_REFRESH_INTERVAL = "refresh_interval"

data class TemplateTileConfig(
    val template: String,
    val refreshInterval: Int
) {
    constructor(jsonObject: JSONObject) : this(
        jsonObject.getString(FIELD_TEMPLATE),
        jsonObject.getInt(FIELD_REFRESH_INTERVAL)
    )

    fun toJSONObject(): JSONObject {
        return JSONObject(
            mapOf(
                FIELD_TEMPLATE to template,
                FIELD_REFRESH_INTERVAL to refreshInterval
            )
        )
    }
}
