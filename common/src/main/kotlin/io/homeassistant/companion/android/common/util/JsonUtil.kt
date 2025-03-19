package io.homeassistant.companion.android.common.util

import org.json.JSONArray

fun JSONArray.toStringList(): List<String> =
    List(length()) { i ->
        getString(i)
    }
