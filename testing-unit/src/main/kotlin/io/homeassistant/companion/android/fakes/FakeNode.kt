package io.homeassistant.companion.android.fakes

import com.google.android.gms.wearable.Node

data class FakeNode(
    @JvmField
    val displayName: String,
    @JvmField
    val id: String,
    @JvmField
    val isNearby: Boolean,
) : Node {
    override fun getDisplayName(): String = displayName

    override fun getId(): String = id

    override fun isNearby(): Boolean = isNearby
}
