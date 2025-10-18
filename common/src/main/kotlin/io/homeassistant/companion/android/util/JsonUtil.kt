package io.homeassistant.companion.android.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun String.toJsonObject(): JsonObject? = runCatching {
    Json.parseToJsonElement(this).jsonObject
}.getOrNull()

fun JsonObject.getString(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

fun JsonObject?.getStringOrDefault(key: String, default: String = ""): String = this?.let { jsonObject ->
    jsonObject[key]?.jsonPrimitive?.contentOrNull
} ?: default

fun JsonObject.getBoolean(key: String): Boolean? = this[key]?.jsonPrimitive?.boolean
fun JsonObject?.getBooleanOrDefault(key: String, default: Boolean = false): Boolean = this?.let { jsonObject ->
    jsonObject[key]?.jsonPrimitive?.boolean
} ?: default
