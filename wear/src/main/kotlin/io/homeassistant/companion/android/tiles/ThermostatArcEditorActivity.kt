package io.homeassistant.companion.android.tiles

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.wear.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.Button
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.awaitLoadedOrNull
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.wear.ThermostatTileDao
import io.homeassistant.companion.android.theme.WearAppTheme
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Launched from a tap-only ProtoLayout tile; this Compose screen owns the actual drag gesture. */
@AndroidEntryPoint
class ThermostatArcEditorActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TILE_ID = "thermostat_tile_id"
    }

    @Inject lateinit var tileDao: ThermostatTileDao
    @Inject lateinit var entities: EntitiesForDisplayManager
    @Inject lateinit var coordinator: ThermostatCommandCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tileId = intent.getIntExtra(EXTRA_TILE_ID, -1)
        if (tileId == -1) {
            finish()
            return
        }
        lifecycleScope.launch {
            val tile = tileDao.get(tileId)
            val entityId = tile?.entityId
            if (entityId.isNullOrEmpty()) {
                finish()
                return@launch
            }
            val entity = entities.snapshot(ServerManager.SERVER_ID_ACTIVE, listOf(entityId))
                .awaitLoadedOrNull()?.entity(entityId)
            val climate = entity?.climateControls
            if (climate == null || entity.rawState in listOf("unavailable", "unknown", "off")) {
                finish()
                return@launch
            }
            val min = climate.minTemperature ?: 7f
            val max = climate.maxTemperature ?: 35f
            val step = climate.targetTemperatureStep?.takeIf { it > 0 } ?: 1f
            val range = entity.rawState == "heat_cool" && climate.supportsTargetTemperatureRange &&
                climate.targetTemperatureLow != null && climate.targetTemperatureHigh != null
            val low = climate.targetTemperatureLow ?: min
            val high = climate.targetTemperatureHigh ?: max
            val single = climate.targetTemperature ?: (min + max) / 2
            setContent {
                WearAppTheme {
                    ArcEditorScreen(
                        minimum = min,
                        maximum = max,
                        step = step,
                        range = range,
                        initialLow = low,
                        initialHigh = high,
                        initialSingle = single,
                        unit = climate.temperatureUnit ?: "°",
                        onApply = { newLow, newHigh, newSingle ->
                            coordinator.submitTemperature(tileId, entityId, range, newLow, newHigh, newSingle)
                            finish() // Never block navigation on network or thermostat latency.
                        },
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArcEditorScreen(
    minimum: Float,
    maximum: Float,
    step: Float,
    range: Boolean,
    initialLow: Float,
    initialHigh: Float,
    initialSingle: Float,
    unit: String,
    onApply: (Float?, Float?, Float?) -> Unit,
    onBack: () -> Unit,
) {
    var low by remember { mutableFloatStateOf(initialLow) }
    var high by remember { mutableFloatStateOf(initialHigh) }
    var single by remember { mutableFloatStateOf(initialSingle) }
    var highSelected by remember { mutableStateOf(false) }
    var dragged by remember { mutableStateOf(false) }
    val heatColor = Color(0xFFF87171)
    val coolColor = Color(0xFF60A5FA)

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(minimum, maximum, step, range) {
                detectDragGestures(
                    onDragStart = { touch ->
                        dragged = false
                        val fraction = ThermostatTemperatureLogic.fractionFromTopArc(
                            touch.x, touch.y, size.width / 2f, size.height / 2f,
                        )
                        highSelected = range && ThermostatTemperatureLogic.chooseHighMarker(
                            fraction,
                            ThermostatTemperatureLogic.normalized(low, minimum, maximum),
                            ThermostatTemperatureLogic.normalized(high, minimum, maximum),
                        )
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragged = true
                        val fraction = ThermostatTemperatureLogic.fractionFromTopArc(
                            change.position.x, change.position.y, size.width / 2f, size.height / 2f,
                        )
                        val proposed = minimum + fraction * (maximum - minimum)
                        if (range) {
                            val revised = ThermostatTemperatureLogic.adjustRange(
                                proposed, highSelected, low, high, minimum, maximum, step,
                            )
                            low = revised.first
                            high = revised.second
                        } else {
                            single = ThermostatTemperatureLogic.snap(proposed, step, minimum, maximum)
                        }
                    },
                    onDragEnd = {
                        if (dragged) onApply(if (range) low else null, if (range) high else null, if (range) null else single)
                    },
                )
            },
        ) {
            val diameter = size.minDimension
            val radius = diameter / 2 - 18.dp.toPx()
            val center = Offset(size.width / 2, size.height / 2)
            val arcBounds = Size(radius * 2, radius * 2)
            val topLeft = Offset(center.x - radius, center.y - radius)
            drawArc(
                color = Color(0xFF728092), startAngle = ThermostatTemperatureLogic.START_DEGREES,
                sweepAngle = ThermostatTemperatureLogic.SWEEP_DEGREES,
                useCenter = false, topLeft = topLeft, size = arcBounds,
                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round),
            )
            fun marker(temp: Float, color: Color) {
                val fraction = ThermostatTemperatureLogic.normalized(temp, minimum, maximum)
                val theta = (ThermostatTemperatureLogic.START_DEGREES +
                    ThermostatTemperatureLogic.SWEEP_DEGREES * fraction) * PI / 180.0
                val x = center.x + radius * cos(theta).toFloat()
                val y = center.y + radius * sin(theta).toFloat()
                drawCircle(color = color, radius = 10.dp.toPx(), center = Offset(x, y))
                drawCircle(color = Color.White, radius = 10.dp.toPx(), center = Offset(x, y),
                    style = Stroke(width = 1.5.dp.toPx()))
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = android.graphics.Color.WHITE
                        textSize = 15.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                    }
                    drawText("${temp.toInt()}$unit", x, y + 29.dp.toPx(), paint)
                }
            }
            if (range) {
                marker(low, heatColor)
                marker(high, coolColor)
            } else {
                marker(single, heatColor)
            }
        }
        Column(
            modifier = Modifier.align(Alignment.Center).padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(if (range) "${low.toInt()}° – ${high.toInt()}°" else "${single.toInt()}$unit")
            Text("Drag marker · release to set")
        }
        Button(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            onClick = onBack,
        ) { Text("Back") }
    }
}
