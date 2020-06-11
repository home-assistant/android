package io.homeassistant.companion.android.wear.background

import com.google.android.gms.wearable.Node

class CapabilityResult(
    val result: Result,
    private val node: Node? = null
) {

    val deviceNode: Node
        get() = when (result) {
            Result.SUCCESS -> node!!
            Result.NOT_NEARBY -> throw IllegalStateException("No device found that is close enough to connect with!")
            Result.FAILURE -> throw IllegalStateException("No device found with Home Assistant installed!")
        }
}

enum class Result {
    SUCCESS, NOT_NEARBY, FAILURE
}
