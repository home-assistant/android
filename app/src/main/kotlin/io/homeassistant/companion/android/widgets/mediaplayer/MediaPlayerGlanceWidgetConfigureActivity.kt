package io.homeassistant.companion.android.widgets.mediaplayer

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MediaPlayerGlanceWidgetConfigureActivity : MediaPlayerControlsWidgetConfigureActivity() {
    override val widgetClass: Class<*> = MediaPlayerGlanceWidget::class.java
}
