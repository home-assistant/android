package io.homeassistant.companion.android.tiles

import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.style.AbsoluteSizeSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.Log
import androidx.core.content.getSystemService
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import androidx.core.text.HtmlCompat.fromHtml
import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.ColorBuilders
import androidx.wear.tiles.DimensionBuilders
import androidx.wear.tiles.DimensionBuilders.dp
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.LayoutElementBuilders.Box
import androidx.wear.tiles.LayoutElementBuilders.FONT_WEIGHT_BOLD
import androidx.wear.tiles.LayoutElementBuilders.Layout
import androidx.wear.tiles.LayoutElementBuilders.LayoutElement
import androidx.wear.tiles.ModifiersBuilders
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.ResourceBuilders.Resources
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders.Timeline
import androidx.wear.tiles.TimelineBuilders.TimelineEntry
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
            val state = requestParams.state
            if (state != null && state.lastClickableId == "refresh") {
                if (wearPrefsRepository.getWearHapticFeedback()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val vibratorManager = applicationContext.getSystemService<VibratorManager>()
                        val vibrator = vibratorManager?.defaultVibrator
                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    } else {
                        val vibrator = applicationContext.getSystemService<Vibrator>()
                        vibrator?.vibrate(200)
                    }
                }
            }

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

            Tile.Builder()
                .setResourcesVersion("1")
                .setFreshnessIntervalMillis(
                    wearPrefsRepository.getTemplateTileRefreshInterval().toLong() * 1000
                )
                .setTimeline(
                    Timeline.Builder().addTimelineEntry(
                        TimelineEntry.Builder().setLayout(
                            Layout.Builder().setRoot(
                                layout(renderedText)
                            ).build()
                        ).build()
                    ).build()
                ).build()
        }

    override fun onResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> =
        serviceScope.future {
            Resources.Builder()
                .setVersion("1")
                .addIdToImageMapping(
                    "refresh",
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
        addContent(
            LayoutElementBuilders.Arc.Builder()
                .setAnchorAngle(
                    DimensionBuilders.DegreesProp.Builder()
                        .setValue(180f)
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.ArcAdapter.Builder()
                        .setContent(
                            LayoutElementBuilders.Image.Builder()
                                .setResourceId("refresh")
                                .setWidth(dp(24f))
                                .setHeight(dp(24f))
                                .setModifiers(getRefreshModifiers())
                                .build()
                        )
                        .setRotateContents(false)
                        .build()
                )
                .build()
        )
        setModifiers(getRefreshModifiers())
    }
        .build()

    private fun getRefreshModifiers(): ModifiersBuilders.Modifiers {
        return ModifiersBuilders.Modifiers.Builder()
            .setClickable(
                ModifiersBuilders.Clickable.Builder()
                    .setOnClick(
                        ActionBuilders.LoadAction.Builder().build()
                    )
                    .setId("refresh")
                    .build()
            )
            .build()
    }

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
                                ColorBuilders.ColorProp.Builder()
                                    .setArgb(span.foregroundColor)
                                    .build()
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
