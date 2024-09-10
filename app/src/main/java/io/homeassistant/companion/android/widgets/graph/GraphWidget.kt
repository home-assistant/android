package io.homeassistant.companion.android.widgets.graph

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
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
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.integration.friendlyState
import io.homeassistant.companion.android.common.data.widgets.GraphWidgetRepository
import io.homeassistant.companion.android.common.util.DateFormatter
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetEntity
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetHistoryEntity
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetWithHistories
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class GraphWidget : BaseWidgetProvider() {

    companion object {

        private const val TAG = "GraphWidget"

        internal const val EXTRA_SERVER_ID = "EXTRA_SERVER_ID"
        internal const val EXTRA_ENTITY_ID = "EXTRA_ENTITY_ID"
        internal const val EXTRA_LABEL = "EXTRA_LABEL"
        internal const val UNIT_OF_MEASUREMENT = "UNIT_OF_MEASUREMENT"
        internal const val EXTRA_TIME_RANGE = "EXTRA_TIME_RANGE"

        private data class ResolvedText(val text: CharSequence?, val exception: Boolean = false)
    }

    @Inject
    lateinit var repository: GraphWidgetRepository

    override fun getWidgetProvider(context: Context): ComponentName =
        ComponentName(context, GraphWidget::class.java)

    override suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int, suggestedEntity: Entity<Map<String, Any>>?): RemoteViews {
        val historicData = repository.getGraphWidgetWithHistories(appWidgetId)
        val widget = historicData?.graphWidget

        val intent = Intent(context, GraphWidget::class.java).apply {
            action = UPDATE_VIEW
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
                if (widget != null && (historicData.histories?.size ?: 0) >= 1) {
                    val serverId = widget.serverId
                    val entityId: String = widget.entityId
                    val label: String? = widget.label

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
                        serverId,
                        entityId,
                        suggestedEntity
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
                            historicData = historicData,
                            unitOfMeasurement = widget.unitOfMeasurement,
                            width = width,
                            height = height,
                            timeRange = widget.timeRange.toString()
                        ).chartBitmap
                    )
                    setViewVisibility(
                        R.id.chartImageView,
                        if (!resolvedText.exception) View.VISIBLE else View.GONE
                    )

                    setOnClickPendingIntent(
                        R.id.widgetTextLayout,
                        PendingIntent.getBroadcast(
                            context,
                            appWidgetId,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                } else {
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
        historicData.histories
            ?.sortedBy {
                it.lastChanged
            }?.forEachIndexed { index, history ->
                entries.add(Entry(index.toFloat(), history.state.toFloat()))
            }
        return entries
    }

    private fun createLineChart(context: Context, label: String, timeRange: String, unitOfMeasurement: String, historicData: GraphWidgetWithHistories, width: Int, height: Int): LineChart {
        val entriesFromHistoricData = createEntriesFromHistoricData(historicData = historicData)

        val lineChart = LineChart(context).apply {
            val dynTextColor = ContextCompat.getColor(context, commonR.color.colorWidgetButtonLabel)
            setBackgroundResource(commonR.color.colorWidgetButtonBackground)
            setDrawBorders(false)

            xAxis.apply {
                setDrawGridLines(true)
                position = XAxis.XAxisPosition.BOTTOM
                textColor = dynTextColor
                textSize = 12F
                granularity = 5F
                setAvoidFirstLastClipping(false)
                isAutoScaleMinMaxEnabled = true
                valueFormatter = historicData.histories?.let { TimeValueFormatter(it) }
            }

            axisLeft.apply {
                setDrawGridLines(true)
                textColor = dynTextColor
                textSize = 12F
                valueFormatter = UnitOfMeasurementValueFormatter(unitOfMeasurement)
            }

            axisRight.apply {
                setDrawGridLines(false)
                setDrawLabels(false)
            }

            legend.apply {
                isEnabled = true
                textColor = dynTextColor
                textSize = 12F
                verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                orientation = Legend.LegendOrientation.HORIZONTAL
                setDrawInside(false)
            }

            description = Description().apply {
                text = DateFormatter.formatHours(context, timeRange.toInt())
                textColor = dynTextColor
            }

            legend.isEnabled = true
            description.isEnabled = true
        }

        val mainGraphColor = ContextCompat.getColor(context, commonR.color.colorPrimary)

        val dataSet = LineDataSet(entriesFromHistoricData, label).apply {
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

    private class TimeValueFormatter(private val entriesFromHistoricData: List<GraphWidgetHistoryEntity>) : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            return DateFormatter.formatTimeAndDateCompat(entriesFromHistoricData[value.toInt()].lastChanged)
        }
    }

    private class UnitOfMeasurementValueFormatter(private val unitOfMeasurement: String) : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            return "${value}$unitOfMeasurement"
        }
    }

    override suspend fun getAllWidgetIdsWithEntities(context: Context): Map<Int, Pair<Int, List<String>>> =
        repository.getAllFlow()
            .first()
            .associate { it.id to (it.serverId to listOf(it.entityId)) }

    private suspend fun resolveTextToShow(
        serverId: Int,
        entityId: String?,
        suggestedEntity: Entity<Map<String, Any>>?
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

        return ResolvedText(entity?.friendlyName, entityCaughtException)
    }

    private suspend fun fetchHistory(appWidgetId: Int, serverId: Int, entityId: String, fromMillis: Long, toMillis: Long) {
        try {
            val historyEntities: List<GraphWidgetHistoryEntity> = serverManager.integrationRepository(serverId)
                .getHistory(
                    significantChangesOnly = false,
                    entityIds = listOf(entityId),
                    timestamp = fromMillis,
                    endTimeMillis = toMillis
                )?.firstOrNull()
                ?.filter { historyEntity ->
                    historyEntity.state.toFloatOrNull() != null
                }?.map { historyEntity ->
                    GraphWidgetHistoryEntity(
                        entityId = historyEntity.entityId,
                        graphWidgetId = appWidgetId,
                        state = historyEntity.state,
                        lastChanged = historyEntity.lastChanged.timeInMillis
                    )
                } ?: emptyList()

            repository.insertGraphWidgetHistory(historyEntities)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to fetch entity history", e)
        }
    }

    override fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        val serverId = extras.getInt(EXTRA_SERVER_ID)

        val entitySelection: String? = extras.getString(EXTRA_ENTITY_ID)
        val labelSelection: String? = extras.getString(EXTRA_LABEL)
        val unitOfMeasurement: String? = extras.getString(UNIT_OF_MEASUREMENT)

        val timeRange = extras.getInt(EXTRA_TIME_RANGE)

        if (entitySelection == null) {
            Log.e(TAG, "Missing entitySelection. Expected entity ($labelSelection) data but received null. Time range: $timeRange")
            return
        }

        widgetScope?.launch {
            Log.d(
                TAG,
                "Saving entity state config data:" + System.lineSeparator() +
                    "entity id: " + entitySelection + System.lineSeparator()
            )

            val timeRangeInMillis = getTimeRangeInMillis(timeRange)

            repository.add(
                GraphWidgetEntity(
                    id = appWidgetId,
                    serverId = serverId,
                    entityId = entitySelection,
                    label = labelSelection,
                    unitOfMeasurement = unitOfMeasurement.orEmpty(),
                    timeRange = timeRange,
                    tapAction = WidgetTapAction.REFRESH,
                    lastUpdate = timeRangeInMillis.first
                )
            )
            repository.get(appWidgetId)?.let {
                fetchHistory(
                    appWidgetId = appWidgetId,
                    serverId = serverId,
                    entityId = entitySelection,
                    fromMillis = timeRangeInMillis.second,
                    toMillis = timeRangeInMillis.first
                )
            }
            repository.deleteEntriesOlderThan(appWidgetId, timeRange)
            onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
        }
    }

    private fun getTimeRangeInMillis(timeRange: Int): Pair<Long, Long> {
        val currentTime = System.currentTimeMillis()
        return currentTime to currentTime - 60 * 60 * 1000 * timeRange
    }

    override suspend fun onEntityStateChanged(context: Context, appWidgetId: Int, entity: Entity<*>) {
        widgetScope?.launch {
            val graphEntity = repository.get(appWidgetId)

            if (graphEntity != null) {
                val timeRangeInMillis = getTimeRangeInMillis(graphEntity.timeRange)
                val exceedsAverage = repository.checkIfExceedsAverageInterval(appWidgetId, timeRangeInMillis.first)

                repository.deleteEntriesOlderThan(appWidgetId, graphEntity.timeRange)

                if (exceedsAverage) {
                    fetchHistory(
                        appWidgetId = appWidgetId,
                        serverId = repository.get(appWidgetId)?.serverId!!.toInt(),
                        entityId = entity.entityId,
                        fromMillis = timeRangeInMillis.second,
                        toMillis = timeRangeInMillis.first
                    )
                } else {
                    repository.insertGraphWidgetHistory(
                        listOf(
                            GraphWidgetHistoryEntity(
                                entityId = entity.entityId,
                                graphWidgetId = appWidgetId,
                                state = entity.friendlyState(context),
                                lastChanged = timeRangeInMillis.first
                            )
                        )
                    )

                    repository.updateWidgetLastUpdate(
                        appWidgetId,
                        timeRangeInMillis.first
                    )
                }
            }

            val views = getWidgetRemoteViews(context, appWidgetId, entity as Entity<Map<String, Any>>)
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        widgetScope?.launch {
            repository.deleteAll(appWidgetIds)
            appWidgetIds.forEach { removeSubscription(it) }
        }
    }
}
