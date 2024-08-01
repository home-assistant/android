package io.homeassistant.companion.android.common.data.integration.impl.entities

data class ActionRequest(
    val domain: String,
    val action: String,
    val actionData: HashMap<String, Any>
)
