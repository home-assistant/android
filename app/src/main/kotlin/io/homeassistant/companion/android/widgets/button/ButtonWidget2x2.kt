package io.homeassistant.companion.android.widgets.button

import dagger.hilt.android.AndroidEntryPoint

/**
 * 2x2 variant of the Button widget for the Samsung Z Flip cover screen
 * This class is a simple wrapper that delegates all logic to ButtonWidget.kt
 * and exists only for Android manifest and compatibility purposes.
 */
@AndroidEntryPoint
class ButtonWidget2x2 : ButtonWidget()
