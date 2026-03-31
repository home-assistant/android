package io.homeassistant.companion.android.data

data class SimplifiedEntity(val entityId: String, val friendlyName: String = entityId, val icon: String = "") {
    constructor(entityString: String) : this(
        entityString.split(",")[0],
        entityString.split(",")[1],
        entityString.split(",")[2],
    )

    val domain: String
        get() = entityId.split(".")[0]

    val entityString: String
        get() = "$entityId,$friendlyName,$icon"
}
