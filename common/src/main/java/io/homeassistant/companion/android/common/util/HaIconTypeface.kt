package io.homeassistant.companion.android.common.util

import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.ITypeface
import io.homeassistant.companion.android.common.R
import java.util.LinkedList

@Suppress("EnumEntryName")
object HaIconTypeface : ITypeface {

    override val fontRes: Int
        get() = R.font.ha_icon

    override val characters: Map<String, Char> by lazy {
        Icon.values().associate { it.name to it.character }
    }

    override val mappingPrefix: String
        get() = "mdi"

    override val fontName: String
        get() = "HA icon patch"

    override val version: String
        get() = "0.1"

    override val iconCount: Int
        get() = characters.size

    override val icons: List<String>
        get() = characters.keys.toCollection(LinkedList())

    override val author: String
        get() = "HomeAssistant"

    override val url: String
        get() = "https://github.com/home-assistant/assets"

    override val description: String
        get() = "A single glyph font to patch the MDI icon library with the updated HomeAssistant icon."

    override val license: String
        get() = "CC"

    override val licenseUrl: String
        get() = "https://github.com/home-assistant/assets/blob/master/LICENSE.md"

    override fun getIcon(key: String): IIcon = Icon.valueOf(key)

    enum class Icon constructor(override val character: Char) : IIcon {
        mdi_home_assistant('\uae3b');

        override val typeface: ITypeface by lazy { HaIconTypeface }
    }
}
