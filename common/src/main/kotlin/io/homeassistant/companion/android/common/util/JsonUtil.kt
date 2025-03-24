package io.homeassistant.companion.android.common.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.json.JSONArray

fun JSONArray.toStringList(): List<String> =
    List(length()) { i ->
        getString(i)
    }

/**
 * Jackson ObjectMapper to use while interacting with the API of Home Assistant Core.
 */
internal fun jacksonObjectMapperForHACore() = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
