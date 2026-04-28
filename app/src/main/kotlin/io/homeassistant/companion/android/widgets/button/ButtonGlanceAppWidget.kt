package io.homeassistant.companion.android.widgets.button

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.text.Text
import io.homeassistant.companion.android.widgets.BaseGlanceEntityWidgetReceiver

class ButtonGlanceAppWidget: GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Text("Hello, pearl")
        }
    }
}
