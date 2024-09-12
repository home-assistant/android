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
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.friendlyState
import io.homeassistant.companion.android.common.data.repositories.GraphWidgetRepository
import io.homeassistant.companion.android.common.util.DateFormatter
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetEntity
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetHistoryEntity
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetWithHistories
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GraphWidget : BaseWidgetProvider<GraphWidgetRepository>() {

    companion object {

        private const val TAG = "GraphWidget"

        internal const val EXTRA_SERVER_ID = "EXTRA_SERVER_ID"
        internal const val EXTRA_ENTITY_ID = "EXTRA_ENTITY_ID"
        internal const val EXTRA_LABEL = "EXTRA_LABEL"
        internal const val UNIT_OF_MEASUREMENT = "UNIT_OF_MEASUREMENT"
        internal const val EXTRA_TIME_RANGE = "EXTRA_TIME_RANGE"
        internal const val EXTRA_SMOOTH_GRAPHS = "EXTRA_SMOOTH_GRAPHS"
    }

    @Inject
    lateinit var repository: GraphWidgetRepository

    override fun widgetRepository(): GraphWidgetRepository = repository

    override fun getWidgetProvider(context: Context): ComponentName =
        ComponentName(context, GraphWidget::class.java)

    override fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        val serverId = extras.getInt(EXTRA_SERVER_ID)

        val entitySelection: String = extras.getString(EXTRA_ENTITY_ID).orEmpty()
        val labelSelection: String = extras.getString(EXTRA_LABEL).orEmpty()
        val unitOfMeasurement: String = extras.getString(UNIT_OF_MEASUREMENT).orEmpty()

        val smoothGraphs: Boolean = extras.getBoolean(EXTRA_SMOOTH_GRAPHS, false)

        val timeRange = extras.getInt(EXTRA_TIME_RANGE)

        if (entitySelection.isEmpty()) {
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

            if (!repository.exist(appWidgetId)) {
                repository.add(
                    GraphWidgetEntity(
                        id = appWidgetId,
                        serverId = serverId,
                        entityId = entitySelection,
                        label = labelSelection,
                        unitOfMeasurement = unitOfMeasurement,
                        timeRange = timeRange,
                        smoothGraphs = smoothGraphs,
                        lastUpdate = timeRangeInMillis.first
                    )
                )
            } else {
                repository.deleteEntries(appWidgetId)
                repository.updateWidgetData(
                    appWidgetId = appWidgetId,
                    unitOfMeasurement = unitOfMeasurement,
                    entityId = entitySelection,
                    labelText = labelSelection,
                    timeRange = timeRange,
                    smoothGraphs = smoothGraphs,
                    lastUpdate = timeRangeInMillis.first
                )
            }

            repository.get(appWidgetId)?.let {
                fetchHistory(appWidgetId = appWidgetId, forceFetch = true) {
                    onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
                    return@fetchHistory
                }
            }
        }
    }

    override suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int, suggestedEntity: Entity<Map<String, Any>>?): RemoteViews {
        val historicData = repository.getGraphWidgetWithHistories(appWidgetId)
        val widgetEntity = historicData?.graphWidget
        fetchHistory(appWidgetId = appWidgetId) {
            onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
            return@fetchHistory
        }
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

        val useDynamicColors = widgetEntity?.backgroundType == WidgetBackgroundType.DYNAMICCOLOR && DynamicColors.isDynamicColorAvailable()
        val views = RemoteViews(context.packageName, if (useDynamicColors) R.layout.widget_graph_wrapper_dynamiccolor else R.layout.widget_graph_wrapper_default)
            .apply {
                if (widgetEntity != null && (historicData.histories?.size ?: 0) > 1) {
                    val entityId: String = widgetEntity.entityId
                    val label: String? = widgetEntity.label

                    val cantFetchEntity = cantFetchEntity(widgetEntity.serverId, widgetEntity.entityId)

                    setViewVisibility(
                        R.id.widgetProgressBar,
                        View.GONE
                    )
                    setTextViewText(
                        R.id.widgetLabel,
                        label ?: entityId
                    )
                    setViewVisibility(
                        R.id.widgetGraphError,
                        if (cantFetchEntity) View.VISIBLE else View.GONE
                    )
                    setImageViewBitmap(
                        R.id.chartImageView,
                        createLineChart(
                            context = context,
                            label = label ?: entityId,
                            historicData = historicData,
                            unitOfMeasurement = widgetEntity.unitOfMeasurement,
                            width = width,
                            height = height,
                            timeRange = widgetEntity.timeRange.toString(),
                            smoothGraphs = widgetEntity.smoothGraphs
                        ).chartBitmap
                    )
                    setViewVisibility(
                        R.id.chartImageView,
                        View.VISIBLE
                    )
                    setOnClickPendingIntent(
                        R.id.chartImageView,
                        PendingIntent.getBroadcast(
                            context,
                            appWidgetId,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                } else {
                    setViewVisibility(
                        R.id.chartImageView,
                        View.GONE
                    )
                    setViewVisibility(
                        R.id.widgetProgressBar,
                        View.VISIBLE
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
                }
            }

        return views
    }

    override suspend fun onEntityStateChanged(context: Context, appWidgetId: Int, entity: Entity<*>) {
        widgetScope?.launch {
            val graphEntity = repository.get(appWidgetId)

            if (graphEntity != null) {
                repository.deleteEntriesOlderThan(appWidgetId, graphEntity.timeRange)
                repository.insertGraphWidgetHistory(
                    listOf(
                        GraphWidgetHistoryEntity(
                            entityId = entity.entityId,
                            graphWidgetId = appWidgetId,
                            state = entity.friendlyState(context),
                            lastChanged = entity.lastChanged.timeInMillis
                        )
                    )
                )

                repository.updateWidgetData(
                    appWidgetId = appWidgetId,
                    lastUpdate = entity.lastChanged.timeInMillis
                )
            }
        }
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

    private fun createLineChart(context: Context, label: String, timeRange: String, unitOfMeasurement: String, historicData: GraphWidgetWithHistories, width: Int, height: Int, smoothGraphs: Boolean): LineChart {
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
                granularity = 4F
                setAvoidFirstLastClipping(false)
                isAutoScaleMinMaxEnabled = true
                valueFormatter = historicData.histories?.let { TimeValueFormatter(it) }
            }

            axisLeft.apply {
                setDrawGridLines(true)
                textColor = dynTextColor
                granularity = 4F
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
            mode = if (smoothGraphs) {
                LineDataSet.Mode.CUBIC_BEZIER
            } else {
                LineDataSet.Mode.STEPPED
            }
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
            return "${value.toBigDecimal().toDouble()}$unitOfMeasurement"
        }
    }

    private suspend fun fetchHistory(appWidgetId: Int, forceFetch: Boolean = false, onHistoryFetched: () -> Unit) {
        val graphWidget = repository.get(appWidgetId)
        if (graphWidget != null) {
            val timeRangeInMillis = getTimeRangeInMillis(graphWidget.timeRange)

            // Check if it's necessary to fetch the history
            val exceedsAverage = forceFetch || repository.checkIfExceedsAverageInterval(appWidgetId)

            if (exceedsAverage) {
                Log.d(TAG, "History fetch for widget $appWidgetId")
                try {
                    val historyEntities: List<GraphWidgetHistoryEntity> = serverManager.integrationRepository(graphWidget.serverId)
                        .getHistory(
                            significantChangesOnly = true,
                            entityIds = listOf(graphWidget.entityId),
                            timestamp = timeRangeInMillis.second,
                            endTimeMillis = timeRangeInMillis.first
                        )?.firstOrNull()
                        ?.filter { it.state.toFloatOrNull() != null }
                        ?.map { historyEntity ->
                            GraphWidgetHistoryEntity(
                                entityId = historyEntity.entityId,
                                graphWidgetId = appWidgetId,
                                state = historyEntity.state,
                                lastChanged = historyEntity.lastChanged.timeInMillis
                            )
                        } ?: emptyList()

                    if (historyEntities.isNotEmpty()) {
                        repository.deleteEntries(appWidgetId)
                        repository.insertGraphWidgetHistory(historyEntities)
                        Log.d(TAG, "History fetched for widget $appWidgetId")
                        onHistoryFetched()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to fetch entity history", e)
                }
            } else {
                Log.d(TAG, "History fetch not necessary for widget $appWidgetId")
            }
        }
    }

    private suspend fun cantFetchEntity(serverId: Int, entityId: String): Boolean {
        try {
            return serverManager.integrationRepository(serverId).getEntity(entityId) == null
        } catch (e: Exception) {
            Log.e(TAG, "Unable to fetch entity", e)
            return true
        }
    }

    private fun getTimeRangeInMillis(timeRange: Int): Pair<Long, Long> {
        val currentTime = System.currentTimeMillis()
        return currentTime to currentTime - 60 * 60 * 1000 * timeRange
    }
}
