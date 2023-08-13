package io.homeassistant.companion.android.common.data.prefs.impl.entities

import com.fasterxml.jackson.annotation.JsonProperty
import io.homeassistant.companion.android.common.data.integration.impl.entities.Template
import org.json.JSONObject

const val FIELD_TEMPLATE = "template"
const val FIELD_REFRESH_INTERVAL = "refresh_interval"

data class TemplateTileConfig(
    @JsonProperty(value = FIELD_TEMPLATE)
    val template: String,
    @JsonProperty(value= FIELD_REFRESH_INTERVAL)
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

    fun withTemplate(template: String): TemplateTileConfig {
        return TemplateTileConfig(template, refreshInterval)
    }

    fun withRefreshInterval(refreshInterval: Int): TemplateTileConfig {
        return TemplateTileConfig(template, refreshInterval)
    }
}
