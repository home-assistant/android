package io.homeassistant.companion.android.themes.icons

import android.content.res.AssetManager
import androidx.annotation.WorkerThread
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial

private data class IconMetaFile(
    @JsonProperty("mdi_version")
    val version: String,
    val meta: List<IconMeta>
)

/**
 * Metadata representing a community icon from Material Design Icons.
 */
data class IconMeta(
    /** Name of the icon, such as account-alert */
    val name: String,
    /** Character codepoint, such as F01C9 */
    val codepoint: String,
    /** ID of the icon in the `com.maltaisn:icondialog` library */
    @JsonProperty("dialoglib_id")
    val dialoglibId: Int,
    /** Tags used to categorize the icon */
    val tags: List<String>,
    /** Alternate names for the icon, such as user-alert */
    val aliases: List<String>,
) {
    fun getIcon(): IIcon {
        val iconicsKey = "${CommunityMaterial.mappingPrefix}_${name.replace('-', '_')}"
        return CommunityMaterial.getIcon(iconicsKey)
    }

    companion object {
        /**
         * Loads list of all known icon meta values.
         */
        @WorkerThread
        fun loadAll(assets: AssetManager): List<IconMeta> {
            val inputStream = assets.open("icon_meta.json")

            val mapper = jacksonObjectMapper()
            val file = mapper.readValue<IconMetaFile>(inputStream)
            return file.meta
        }
    }
}
