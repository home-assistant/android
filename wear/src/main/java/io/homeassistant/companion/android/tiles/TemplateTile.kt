package io.homeassistant.companion.android.tiles

import android.graphics.Typeface
import android.text.style.AbsoluteSizeSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.Log
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import androidx.core.text.HtmlCompat.fromHtml
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.FONT_WEIGHT_BOLD
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.fasterxml.jackson.databind.JsonMappingException
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.future
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class TemplateTile : TileService() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var wearPrefsRepository: WearPrefsRepository

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> =
        serviceScope.future {
            if (requestParams.currentState.lastClickableId == MODIFIER_CLICK_REFRESH) {
                if (wearPrefsRepository.getWearHapticFeedback()) hapticClick(applicationContext)
            }

            Tile.Builder()
                .setResourcesVersion("1")
                .setFreshnessIntervalMillis(
                    wearPrefsRepository.getTemplateTileRefreshInterval().toLong() * 1000
                )
                .setTileTimeline(
                    if (serverManager.isRegistered()) {
                        timeline()
                    } else {
                        loggedOutTimeline(
                            this@TemplateTile,
                            requestParams,
                            commonR.string.template,
                            commonR.string.template_tile_log_in
                        )
                    }
                ).build()
        }

    override fun onTileResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> =
        serviceScope.future {
            Resources.Builder()
                .setVersion("1")
                .addIdToImageMapping(
                    RESOURCE_REFRESH,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_refresh)
                                .build()
                        ).build()
                )
                .build()
        }

    override fun onDestroy() {
        super.onDestroy()
        // Cleans up the coroutine
        serviceJob.cancel()
    }

    private suspend fun timeline(): Timeline {
        val template = wearPrefsRepository.getTemplateTileContent()
        val renderedText = try {
            if (serverManager.isRegistered()) {
                serverManager.integrationRepository().renderTemplate(template, mapOf()).toString()
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e("TemplateTile", "Exception while rendering template", e)
            // JsonMappingException suggests that template is not a String (= error)
            if (e.cause is JsonMappingException) {
                getString(commonR.string.template_error)
            } else {
                getString(commonR.string.template_render_error)
            }
        }

        return Timeline.fromLayoutElement(layout(renderedText))
    }

    fun layout(renderedText: String): LayoutElement = Box.Builder().apply {
        if (renderedText.isEmpty()) {
            addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(getString(commonR.string.template_tile_empty))
                    .setMaxLines(10)
                    .build()
            )
        } else {
            addContent(
                parseHtml(renderedText)
            )
        }
        addContent(getRefreshButton())
        setModifiers(getRefreshModifiers())
    }
        .build()

    private fun parseHtml(renderedText: String): LayoutElementBuilders.Spannable {
        // Replace control char \r\n, \r, \n and also \r\n, \r, \n as text literals in strings to <br>
        val renderedSpanned = fromHtml(renderedText.replace("(\r\n|\r|\n)|(\\\\r\\\\n|\\\\r|\\\\n)".toRegex(), "<br>"), FROM_HTML_MODE_LEGACY)
        return LayoutElementBuilders.Spannable.Builder().apply {
            var start = 0
            var end = 0
            while (end < renderedSpanned.length) {
                end = renderedSpanned.nextSpanTransition(end, renderedSpanned.length, CharacterStyle::class.java)

                val fontStyle = LayoutElementBuilders.FontStyle.Builder().apply {
                    renderedSpanned.getSpans(start, end, CharacterStyle::class.java).forEach { span ->
                        when (span) {
                            is AbsoluteSizeSpan -> setSize(
                                DimensionBuilders.SpProp.Builder()
                                    .setValue(span.size / applicationContext.resources.displayMetrics.scaledDensity)
                                    .build()
                            )
                            is ForegroundColorSpan -> setColor(
                                ColorBuilders.ColorProp.Builder(span.foregroundColor).build()
                            )
                            is RelativeSizeSpan -> {
                                val defaultSize = 16 // https://developer.android.com/training/wearables/design/typography
                                setSize(
                                    DimensionBuilders.SpProp.Builder()
                                        .setValue(span.sizeChange * defaultSize)
                                        .build()
                                )
                            }
                            is StyleSpan -> when (span.style) {
                                Typeface.BOLD -> setWeight(FONT_WEIGHT_BOLD)
                                Typeface.ITALIC -> setItalic(true)
                                Typeface.BOLD_ITALIC -> setWeight(FONT_WEIGHT_BOLD).setItalic(true)
                            }
                            is UnderlineSpan -> setUnderline(true)
                        }
                    }
                }.build()

                addSpan(
                    LayoutElementBuilders.SpanText.Builder()
                        .setText(renderedSpanned.substring(start, end))
                        .setFontStyle(fontStyle)
                        .build()
                )

                start = end
            }
        }
            .setMaxLines(10)
            .build()
    }
}
