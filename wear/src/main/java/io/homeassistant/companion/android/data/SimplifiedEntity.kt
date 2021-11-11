package io.homeassistant.companion.android.data

data class SimplifiedEntity(
    var entityId: String,
    var friendlyName: String = entityId,
    var icon: String = ""
) {
    constructor(entityString: String) : this(
        entityString.split(",")[0],
        entityString.split(",")[1],
        entityString.split(",")[2]
    )

    val entityString: String
        get() = "$entityId,$friendlyName,$icon"
}
