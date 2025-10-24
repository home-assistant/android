package io.homeassistant.companion.android.compose

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import androidx.compose.ui.tooling.preview.Preview

/**
 * Multipreview annotation that represents various device sizes. Add this annotation to a composable
 * to render various devices.
 *
 * **Note**: We cannot use `device_id` in Preview since it doesn't work properly while used in screenshot testing
 */
@Preview(name = "phone", device = "spec:width=411.4dp,height=923.4dp", group = "phone") // Pixel 9
@Preview(
    name = "phone_landscape",
    device = "spec:width=411.4dp,height=923.4dp,orientation=landscape",
    group = "phone",
) // Pixel 9
@Preview(
    name = "small_phone",
    device = "spec:width=360dp,height=640dp,dpi=480,orientation=landscape",
    group = "phone",
) // Nexus 5
@Preview(
    name = "foldable",
    device = "spec:width=851.7dp,height=882.9dp,dpi=390,orientation=landscape",
    group = "phone",
) // Pixel 9 Pro fold
@Preview(
    name = "tablet",
    device = "spec:width=1280dp,height=800dp,dpi=320,orientation=portrait",
    group = "tablet",
    uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL,
) // Pixel tablet
@Preview(name = "tablet_landscape", device = "spec:width=1280dp,height=800dp,dpi=320", group = "tablet") // Pixel tablet
annotation class HAPreviews
