package io.homeassistant.companion.android.tiles

import android.graphics.Typeface
import android.text.style.AbsoluteSizeSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
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
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.prefs.impl.entities.TemplateTileConfig
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import timber.log.Timber

@AndroidEntryPoint
class TemplateTile : TileService() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var wearPrefsRepository: WearPrefsRepository

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> = serviceScope.future {
        if (requestParams.currentState.lastClickableId == MODIFIER_CLICK_REFRESH) {
            if (wearPrefsRepository.getWearHapticFeedback()) hapticClick(applicationContext)
        }

        val tileId = requestParams.tileId
        val templateTileConfig = getTemplateTileConfig(tileId)
        val freshness = when {
            templateTileConfig.refreshInterval <= 1 -> 0
            else -> templateTileConfig.refreshInterval
        }

        Tile.Builder()
            .setResourcesVersion("1")
            .setFreshnessIntervalMillis(freshness.toLong() * 1_000)
            .setTileTimeline(
                if (serverManager.isRegistered()) {
                    timeline(templateTileConfig)
                } else {
                    loggedOutTimeline(
                        this@TemplateTile,
                        requestParams,
                        commonR.string.template,
                        commonR.string.template_tile_log_in,
                    )
                },
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
                                .build(),
                        ).build(),
                )
                .build()
        }

    override fun onTileAddEvent(requestParams: EventBuilders.TileAddEvent): Unit = runBlocking {
        withContext(Dispatchers.IO) {
            /**
             * When the app is updated from an older version (which only supported a single Template Tile),
             * and the user is adding a new Template Tile, we can't tell for sure if it's the 1st or 2nd Tile.
             * Even though we may have the Template tile config stored in the prefs, it doesn't guarantee that
             *   the tile was actually added to the Tiles carousel.
             * The [WearPrefsRepositoryImpl::getTemplateTileAndSaveTileId] method will handle both of the following cases:
             * 1. There was no Tile added, but there was a Template tile config stored in the prefs.
             *    In this case, the stored config will be associated to the new tileId.
             * 2. There was a single Tile added, and there was a Template tile config stored in the prefs.
             *    If there was a Tile update since updating the app, the tileId will be already
             *    associated to the config, because it also calls [getTemplateTileAndSaveTileId].
             *    If there was no Tile update yet, the new Tile will "steal" the config from the existing Tile,
             *    and the old Tile will behave as it is the new Tile. This is needed because
             *    we don't know if it's the 1st or 2nd Tile.
             */
            wearPrefsRepository.getTemplateTileAndSaveTileId(requestParams.tileId)
        }
    }

    override fun onTileRemoveEvent(requestParams: EventBuilders.TileRemoveEvent): Unit = runBlocking {
        withContext(Dispatchers.IO) {
            wearPrefsRepository.removeTemplateTile(requestParams.tileId)
        }
    }

    override fun onTileEnterEvent(requestParams: EventBuilders.TileEnterEvent) {
        serviceScope.launch {
            val tileId = requestParams.tileId
            val templateTileConfig = getTemplateTileConfig(tileId)
            if (templateTileConfig.refreshInterval >= 1) {
                try {
                    getUpdater(this@TemplateTile).requestUpdate(TemplateTile::class.java)
                } catch (e: Exception) {
                    Timber.w(e, "Unable to request tile update on enter")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleans up the coroutine
        serviceJob.cancel()
    }

    private suspend fun timeline(templateTileConfig: TemplateTileConfig): Timeline {
        val renderedText = try {
            if (serverManager.isRegistered()) {
                serverManager.integrationRepository().renderTemplate(templateTileConfig.template, mapOf()).toString()
            } else {
                ""
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception while rendering template")
            // SerializationException suggests that template is not a String (= error)
            if (e.cause is SerializationException) {
                getString(commonR.string.template_error)
            } else {
                getString(commonR.string.template_render_error)
            }
        }

        return Timeline.fromLayoutElement(layout(renderedText))
    }

    private suspend fun getTemplateTileConfig(tileId: Int): TemplateTileConfig {
        return wearPrefsRepository.getTemplateTileAndSaveTileId(tileId)
    }

    fun layout(renderedText: String): LayoutElement = Box.Builder().apply {
        if (renderedText.isEmpty()) {
            addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(getString(commonR.string.template_tile_empty))
                    .setMaxLines(10)
                    .build(),
            )
        } else {
            addContent(
                parseHtml(renderedText),
            )
        }
        addContent(getRefreshButton())
        setModifiers(getRefreshModifiers())
    }
        .build()

    private fun parseHtml(renderedText: String): LayoutElementBuilders.Spannable {
        // Replace control char \r\n, \r, \n and also \r\n, \r, \n as text literals in strings to <br>
        val renderedSpanned =
            fromHtml(
                renderedText.replace("(\r\n|\r|\n)|(\\\\r\\\\n|\\\\r|\\\\n)".toRegex(), "<br>"),
                FROM_HTML_MODE_LEGACY,
            )
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
                                    .build(),
                            )
                            is ForegroundColorSpan -> setColor(
                                ColorBuilders.ColorProp.Builder(span.foregroundColor).build(),
                            )
                            is RelativeSizeSpan -> {
                                // https://developer.android.com/training/wearables/design/typography
                                val defaultSize = 16
                                setSize(
                                    DimensionBuilders.SpProp.Builder()
                                        .setValue(span.sizeChange * defaultSize)
                                        .build(),
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
                        .build(),
                )

                start = end
            }
        }
            .setMaxLines(10)
            .build()
    }
}
