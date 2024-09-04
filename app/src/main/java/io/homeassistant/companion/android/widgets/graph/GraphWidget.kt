package io.homeassistant.companion.android.widgets.graph

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.os.BundleCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.canSupportPrecision
import io.homeassistant.companion.android.common.data.integration.friendlyState
import io.homeassistant.companion.android.common.data.integration.onEntityPressedWithoutState
import io.homeassistant.companion.android.common.data.widgets.GraphWidgetRepository
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetEntity
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetHistoryEntity
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetWithHistories
import io.homeassistant.companion.android.util.getAttribute
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import io.homeassistant.companion.android.widgets.entity.EntityWidget.Companion.EXTRA_STATE_SEPARATOR
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class GraphWidget : BaseWidgetProvider() {

    companion object {

        private const val TAG = "GraphWidget"
        internal const val TOGGLE_ENTITY =
            "io.homeassistant.companion.android.widgets.entity.GraphWidget.TOGGLE_ENTITY"

        internal const val EXTRA_SERVER_ID = "EXTRA_SERVER_ID"
        internal const val EXTRA_ENTITY_ID = "EXTRA_ENTITY_ID"
        internal const val EXTRA_ATTRIBUTE_IDS = "EXTRA_ATTRIBUTE_IDS"
        internal const val EXTRA_LABEL = "EXTRA_LABEL"
        internal const val EXTRA_ATTRIBUTE_SEPARATOR = "EXTRA_ATTRIBUTE_SEPARATOR"
        internal const val EXTRA_TAP_ACTION = "EXTRA_TAP_ACTION"
        internal const val EXTRA_BACKGROUND_TYPE = "EXTRA_BACKGROUND_TYPE"
        internal const val EXTRA_TEXT_COLOR = "EXTRA_TEXT_COLOR"

        internal const val EXTRA_SAMPLING_MINUTES = "EXTRA_SAMPLING_MINUTES"
        internal const val EXTRA_TIME_RANGE = "EXTRA_TIME_RANGE"

        private data class ResolvedText(val text: CharSequence?, val exception: Boolean = false)
    }

    @Inject
    lateinit var graphWidgetRepository: GraphWidgetRepository

    override fun getWidgetProvider(context: Context): ComponentName =
        ComponentName(context, GraphWidget::class.java)

    override suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int, suggestedEntity: Entity<Map<String, Any>>?): RemoteViews {
        val historicData = graphWidgetRepository.getGraphWidgetWithHistories(appWidgetId)
        val widget = historicData?.graphWidget

        val intent = Intent(context, GraphWidget::class.java).apply {
            action = if (widget?.tapAction == WidgetTapAction.TOGGLE) TOGGLE_ENTITY else UPDATE_VIEW
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)

        // Convert dp to pixels for width and height
        val width = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            minWidth.toFloat(),
            context.resources.displayMetrics
        ).toInt()

        val height = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            minHeight.toFloat(),
            context.resources.displayMetrics
        ).toInt()

        val useDynamicColors = widget?.backgroundType == WidgetBackgroundType.DYNAMICCOLOR && DynamicColors.isDynamicColorAvailable()
        val views = RemoteViews(context.packageName, if (useDynamicColors) R.layout.widget_graph_wrapper_dynamiccolor else R.layout.widget_graph_wrapper_default)
            .apply {
                if (widget != null && (historicData.histories?.size ?: 0) >= 2) {
                    val serverId = widget.serverId
                    val entityId: String = widget.entityId
                    val attributeIds: String? = widget.attributeIds
                    val label: String? = widget.label
                    val stateSeparator: String = widget.stateSeparator
                    val attributeSeparator: String = widget.attributeSeparator

                    // Theming
                    if (widget.backgroundType == WidgetBackgroundType.TRANSPARENT) {
                        var textColor = context.getAttribute(R.attr.colorWidgetOnBackground, ContextCompat.getColor(context, commonR.color.colorWidgetButtonLabel))
                        widget.textColor?.let { textColor = it.toColorInt() }

                        setInt(R.id.widgetLayout, "setBackgroundColor", Color.TRANSPARENT)
                        setTextColor(R.id.widgetLabel, textColor)
                    }

                    // Content
                    setViewVisibility(
                        R.id.chartImageView,
                        View.VISIBLE
                    )
                    setViewVisibility(
                        R.id.widgetProgressBar,
                        View.GONE
                    )
                    setViewVisibility(
                        R.id.widgetStaticError,
                        View.GONE
                    )
                    val resolvedText = resolveTextToShow(
                        context,
                        serverId,
                        entityId,
                        suggestedEntity,
                        attributeIds,
                        stateSeparator,
                        attributeSeparator,
                        appWidgetId
                    )

                    setTextViewText(
                        R.id.widgetLabel,
                        label ?: entityId
                    )
                    setViewVisibility(
                        R.id.widgetStaticError,
                        if (resolvedText.exception) View.VISIBLE else View.GONE
                    )
                    setImageViewBitmap(
                        R.id.chartImageView,
                        createLineChart(
                            context = context,
                            label = label ?: entityId,
                            entries = createEntriesFromHistoricData(historicData = historicData),
                            width = width,
                            height = height
                        ).chartBitmap
                    )
                    setViewVisibility(
                        R.id.chartImageView,
                        if (!resolvedText.exception) View.VISIBLE else View.GONE
                    )
                } else if (widget != null && historicData.histories?.isNotEmpty() == false) {
                    // Content
                    setViewVisibility(
                        R.id.chartImageView,
                        View.GONE
                    )
                    setViewVisibility(
                        R.id.widgetProgressBar,
                        View.VISIBLE
                    )
                    setViewVisibility(
                        R.id.widgetStaticError,
                        View.GONE
                    )
                }

                setOnClickPendingIntent(
                    R.id.widgetTextLayout,
                    PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }

        return views
    }

    private fun createEntriesFromHistoricData(historicData: GraphWidgetWithHistories): List<Entry> {
        val entries = mutableListOf<Entry>()
        historicData.getOrderedHistories(
            startTime = System.currentTimeMillis() - (60 * 60 * 3000),
            endTime = System.currentTimeMillis()
        )?.forEachIndexed { index, history ->
            entries.add(Entry(index.toFloat() + 1, history.state.toFloat()))
        }
        return entries
    }

    private fun createLineChart(context: Context, label: String, entries: List<Entry>, width: Int, height: Int): LineChart {
        val lineChart = LineChart(context).apply {
            setBackgroundColor(Color.WHITE)

            setDrawBorders(false)

            xAxis.apply {
                setDrawGridLines(true)
                position = XAxis.XAxisPosition.BOTTOM
                textColor = Color.DKGRAY
                textSize = 12F
                granularity = 2F
                setAvoidFirstLastClipping(true)
                isAutoScaleMinMaxEnabled = true
            }

            axisLeft.apply {
                setDrawGridLines(true)
                textColor = Color.DKGRAY
                textSize = 12F
            }

            axisRight.apply {
                setDrawGridLines(false)
                setDrawLabels(false)
            }

            legend.apply {
                isEnabled = true
                textColor = Color.DKGRAY
                textSize = 12F
                verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                orientation = Legend.LegendOrientation.HORIZONTAL
                setDrawInside(false)
            }

            legend.isEnabled = true
            description.isEnabled = false
        }

        val mainGraphColor = ContextCompat.getColor(context, commonR.color.colorPrimary)

        val dataSet = LineDataSet(entries, label).apply {
            color = mainGraphColor
            lineWidth = 2F
            circleRadius = 1F
            setDrawCircleHole(false)
            setCircleColor(mainGraphColor)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawCircles(true)
            setDrawValues(false)
        }

        lineChart.data = LineData(dataSet)

        lineChart.layout(0, 0, width, height)

        return lineChart
    }

    private class TimeValueFormatter(private val timestamps: List<Long>) : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            val index = value.toInt()
            return if (index >= 0 && index < timestamps.size) {
                DateUtils.getRelativeTimeSpanString(
                    timestamps[index],
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                ).toString()
            } else {
                value.toString()
            }
        }
    }

    override suspend fun getAllWidgetIdsWithEntities(context: Context): Map<Int, Pair<Int, List<String>>> =
        graphWidgetRepository.getAllFlow()
            .first()
            .associate { it.id to (it.serverId to listOf(it.entityId)) }

    private suspend fun resolveTextToShow(
        context: Context,
        serverId: Int,
        entityId: String?,
        suggestedEntity: Entity<Map<String, Any>>?,
        attributeIds: String?,
        stateSeparator: String,
        attributeSeparator: String,
        appWidgetId: Int
    ): ResolvedText {
        var entity: Entity<Map<String, Any>>? = null
        var entityCaughtException = false
        try {
            entity = if (suggestedEntity != null && suggestedEntity.entityId == entityId) {
                suggestedEntity
            } else {
                entityId?.let { serverManager.integrationRepository(serverId).getEntity(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to fetch entity", e)
            entityCaughtException = true
        }
        val entityOptions = if (
            entity?.canSupportPrecision() == true &&
            serverManager.getServer(serverId)?.version?.isAtLeast(2023, 3) == true
        ) {
            serverManager.webSocketRepository(serverId).getEntityRegistryFor(entity.entityId)?.options
        } else {
            null
        }
        if (attributeIds == null) {
            graphWidgetRepository.updateWidgetLastUpdate(
                appWidgetId,
                entity?.friendlyState(context, entityOptions) ?: graphWidgetRepository.get(appWidgetId)?.lastUpdate ?: ""
            )
            return ResolvedText(graphWidgetRepository.get(appWidgetId)?.lastUpdate, entityCaughtException)
        }

        try {
            val fetchedAttributes = entity?.attributes as? Map<*, *> ?: mapOf<String, String>()
            val attributeValues =
                attributeIds.split(",").map { id -> fetchedAttributes[id]?.toString() }
            val lastUpdate =
                entity?.friendlyState(context, entityOptions).plus(if (attributeValues.isNotEmpty()) stateSeparator else "")
                    .plus(attributeValues.joinToString(attributeSeparator))
            graphWidgetRepository.updateWidgetLastUpdate(appWidgetId, lastUpdate)
            return ResolvedText(lastUpdate)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to fetch entity state and attributes", e)
        }
        return ResolvedText(graphWidgetRepository.get(appWidgetId)?.lastUpdate, true)
    }

    override fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        val serverId = extras.getInt(EXTRA_SERVER_ID)
        val entitySelection: String? = extras.getString(EXTRA_ENTITY_ID)
        val attributeSelection: ArrayList<String>? = extras.getStringArrayList(EXTRA_ATTRIBUTE_IDS)
        val labelSelection: String? = extras.getString(EXTRA_LABEL)
        val stateSeparatorSelection: String? = extras.getString(EXTRA_STATE_SEPARATOR)
        val attributeSeparatorSelection: String? = extras.getString(EXTRA_ATTRIBUTE_SEPARATOR)
        val tapActionSelection = BundleCompat.getSerializable(extras, EXTRA_TAP_ACTION, WidgetTapAction::class.java)
            ?: WidgetTapAction.REFRESH
        val backgroundTypeSelection = BundleCompat.getSerializable(extras, EXTRA_BACKGROUND_TYPE, WidgetBackgroundType::class.java)
            ?: WidgetBackgroundType.DAYNIGHT
        val textColorSelection: String? = extras.getString(EXTRA_TEXT_COLOR)
        val samplingTime = extras.getInt(EXTRA_SAMPLING_MINUTES)
        val timeRange = extras.getInt(EXTRA_TIME_RANGE)

        if (serverId == null || entitySelection == null) {
            Log.e(TAG, "Did not receive complete service call data")
            return
        }

        widgetScope?.launch {
            Log.d(
                TAG,
                "Saving entity state config data:" + System.lineSeparator() +
                    "entity id: " + entitySelection + System.lineSeparator() +
                    "attribute: " + (attributeSelection ?: "N/A")
            )
            graphWidgetRepository.add(
                GraphWidgetEntity(
                    id = appWidgetId,
                    serverId = serverId,
                    entityId = entitySelection,
                    attributeIds = attributeSelection?.joinToString(","),
                    label = labelSelection,
                    samplingTime = samplingTime,
                    timeRange = timeRange,
                    stateSeparator = stateSeparatorSelection ?: "",
                    attributeSeparator = attributeSeparatorSelection ?: "",
                    tapAction = tapActionSelection,
                    lastUpdate = graphWidgetRepository.get(appWidgetId)?.lastUpdate ?: "",
                    backgroundType = backgroundTypeSelection,
                    textColor = textColorSelection
                )
            )

            onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
        }
    }

    override suspend fun onEntityStateChanged(context: Context, appWidgetId: Int, entity: Entity<*>) {
        widgetScope?.launch {
            val graphEntity = entity as GraphWidgetEntity
            val currentTimeMillis = System.currentTimeMillis()

            // this should delete older entries based on timerange for example 24 hours is not in millis int
            val oneHourInMillis = currentTimeMillis - (60 * 60 * 1000 * entity.timeRange)

            graphWidgetRepository.deleteEntriesOlderThan(appWidgetId, oneHourInMillis)

            val samplingTimeInMillis = graphEntity.samplingTime * 60 * 1000

            graphWidgetRepository.insertGraphWidgetHistory(
                GraphWidgetHistoryEntity(
                    entityId = entity.entityId,
                    graphWidgetId = appWidgetId,
                    state = entity.friendlyState(context),
                    sentState = currentTimeMillis
                )
            )

            // Get views and update widget
            val views = getWidgetRemoteViews(context, appWidgetId, entity as Entity<Map<String, Any>>)
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
        }
    }

    private fun toggleEntity(context: Context, appWidgetId: Int) {
        widgetScope?.launch {
            // Show progress bar as feedback
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val loadingViews = RemoteViews(context.packageName, R.layout.widget_static)
            loadingViews.setViewVisibility(R.id.widgetProgressBar, View.VISIBLE)
            loadingViews.setViewVisibility(R.id.widgetTextLayout, View.GONE)
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, loadingViews)

            var success = false
            graphWidgetRepository.get(appWidgetId)?.let {
                try {
                    onEntityPressedWithoutState(
                        it.entityId,
                        serverManager.integrationRepository(it.serverId)
                    )
                    success = true
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to send toggle service call", e)
                }
            }

            if (!success) {
                Toast.makeText(context, commonR.string.action_failure, Toast.LENGTH_LONG).show()

                val views = getWidgetRemoteViews(context, appWidgetId)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } // else update will be triggered by websocket subscription
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        super.onReceive(context, intent)
        when (intent.action) {
            TOGGLE_ENTITY -> toggleEntity(context, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        widgetScope?.launch {
            graphWidgetRepository.deleteAll(appWidgetIds)
            appWidgetIds.forEach { removeSubscription(it) }
        }
    }
}
