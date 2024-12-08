package io.shpro.companion.android.common.data.integration.impl.entities

data class ActionRequest(
    val domain: String,
    val service: String,
    val serviceData: HashMap<String, Any>
)
