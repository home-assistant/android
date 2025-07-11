package io.homeassistant.companion.android.compose

import androidx.compose.ui.tooling.preview.Preview

/**
 * Multipreview annotation that represents various device sizes. Add this annotation to a composable
 * to render various devices.
 */
@Preview(name = "phone", device = "spec:width=360dp,height=640dp", group = "phone")
@Preview(name = "phone_landscape", device = "spec:width=411dp,height=891dp,orientation=landscape", group = "phone")
@Preview(name = "foldable", device = "spec:width=673dp,height=841dp", group = "phone")
@Preview(name = "tablet", device = "spec:width=1280dp,height=800dp", group = "tablet")
@Preview(name = "tablet", device = "spec:width=1280dp,height=800dp,orientation=landscape", group = "tablet")
annotation class HAPreviews
