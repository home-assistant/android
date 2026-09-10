package io.homeassistant.companion.android.common.util

import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.MdiIcon

/** Prefix of MDI icon names as used by Home Assistant, for instance "mdi:account-alert". */
const val MDI_PREFIX = "mdi:"

/** The name in the prefixed form used by Home Assistant, for instance "mdi:account-alert". */
val MdiIcon.mdiName: String
    get() = "$MDI_PREFIX$name"

/**
 * Resolves an icon name in the prefixed form used by Home Assistant (ie "mdi:account-alert") to
 * its icon, or null when the name is unknown or was removed from MDI.
 */
fun Mdi.fromHaName(haName: String): MdiIcon? = fromMdiName(haName.removePrefix(MDI_PREFIX))
