package io.homeassistant.companion.android.tiles

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.wear.protolayout.ResourceBuilders
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders a top-perimeter arc as a small transparent ProtoLayout inline image.
 * Both the tile and Compose editor use the same fraction/angle mapping.
 * Numeric labels sit below, not next to, each marker.
 */
internal object ThermostatArcBitmap {
    const val RESOURCE_ID = "thermostat_temperature_arc"
    const val START_DEGREES = 205f
    const val SWEEP_DEGREES = 130f
    private const val WIDTH = 512
    private const val HEIGHT = 230
    private const val RED = 0xFFF87171.toInt()
    private const val BLUE = 0xFF60A5FA.toInt()

    fun render(
        mode: String,
        single: Float?,
        low: Float?,
        high: Float?,
        min: Float,
        max: Float,
    ): ResourceBuilders.ImageResource {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF687789.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
        }
        val circle = RectF(36f, 26f, 476f, 466f)
        canvas.drawArc(circle, START_DEGREES, SWEEP_DEGREES, false, track)
        val range = mode == "heat_cool" && low != null && high != null
        fun marker(temp: Float, color: Int) {
            val frac = ThermostatTemperatureLogic.normalized(temp, min, max)
            val angle = (START_DEGREES + SWEEP_DEGREES * frac) * PI / 180.0
            val x = 256f + 220 * cos(angle).toFloat()
            val y = 246f + 220 * sin(angle).toFloat()
            val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            canvas.drawCircle(x, y, 11f, pen)
            pen.color = Color.WHITE
            pen.style = Paint.Style.STROKE
            pen.strokeWidth = 2f
            canvas.drawCircle(x, y, 11f, pen)
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = 31f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            canvas.drawText("${format(temp)}°", x, y + 41f, label)
        }
        if (range) {
            marker(checkNotNull(low), RED)
            marker(checkNotNull(high), BLUE)
        } else if (single != null) {
            marker(single, if (mode == "cool") BLUE else RED)
        }
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        return ResourceBuilders.ImageResource.Builder()
            .setInlineResource(
                ResourceBuilders.InlineImageResource.Builder()
                    .setData(output.toByteArray())
                    .setWidthPx(WIDTH)
                    .setHeightPx(HEIGHT)
                    .build(),
            ).build()
    }

    private fun format(temp: Float): String =
        if (temp % 1f == 0f) temp.toInt().toString() else "%.1f".format(java.util.Locale.US, temp)
}
