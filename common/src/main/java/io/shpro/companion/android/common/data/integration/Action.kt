package io.shpro.companion.android.common.data.integration

data class Action(
    val domain: String,
    val action: String,
    val actionData: ActionData
)
