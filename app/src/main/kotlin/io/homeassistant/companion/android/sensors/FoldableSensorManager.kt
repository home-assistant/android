package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.core.content.getSystemService
import androidx.window.WindowSdkExtensions
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.MdiIcon
import io.github.timoptr.mdiicons.generated.BookOpenOutline
import io.github.timoptr.mdiicons.generated.BookOpenPageVariantOutline
import io.github.timoptr.mdiicons.generated.Cellphone
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.ProvidesSensor
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.common.util.SdkVersion
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

private val FOLD_STATE_TIMEOUT = 5.seconds

/** Below this Window SDK extensions version, the window layout of a context that is not an activity is always empty. */
private const val WINDOW_CONTEXT_MIN_EXTENSIONS_VERSION = 2

/**
 * The position of the hinge of a foldable device, reported as the state of the hinge status sensor.
 *
 * @property state The sensor state, shared with the iOS app so both report the same values.
 */
sealed class HingeStatus(val state: String, val icon: MdiIcon) {
    data object Closed : HingeStatus("closed", Mdi.Cellphone)
    data object PartiallyOpen : HingeStatus("partially_open", Mdi.BookOpenOutline)
    data object FullyOpen : HingeStatus("fully_open", Mdi.BookOpenPageVariantOutline)
}

/**
 * Reports whether a foldable device is closed, partially open or fully open.
 *
 * The state comes from the window layout of the default display rather than from the hinge angle
 * sensor: the system stops delivering sensor events to apps in the background, while it keeps
 * delivering window layout changes. The inner screen is the only one crossed by a fold, so the
 * layout reports a [FoldingFeature] exactly when the device is open, and its state tells how far.
 */
@Singleton
class FoldableSensorManager @VisibleForTesting internal constructor(
    override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
    private val windowExtensionsVersion: Int,
    windowLayoutInfo: () -> Flow<WindowLayoutInfo>,
) : SensorManager {

    @Inject
    constructor(
        @ApplicationContext applicationContext: Context,
        sensorRepository: SensorRepository,
        serverManager: ServerManager,
    ) : this(
        applicationContext = applicationContext,
        sensorRepository = sensorRepository,
        serverManager = serverManager,
        windowExtensionsVersion = WindowSdkExtensions.getInstance().extensionVersion,
        windowLayoutInfo = {
            if (SdkVersion.isAtLeast(Build.VERSION_CODES.R)) {
                defaultDisplayWindowLayoutInfo(applicationContext)
            } else {
                emptyFlow()
            }
        },
    )

    companion object {
        @ProvidesSensor
        val hingeStatus = SensorManager.BasicSensor(
            "hinge_status",
            "sensor",
            commonR.string.basic_sensor_name_hinge_status,
            commonR.string.sensor_description_hinge_status,
            Mdi.BookOpenPageVariantOutline,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
    }

    private val hingeStatusFlow: Flow<HingeStatus> by lazy {
        windowLayoutInfo()
            .map { layoutInfo -> layoutInfo.toHingeStatus() }
            .distinctUntilChanged()
    }

    override val name: Int
        get() = commonR.string.sensor_name_foldable

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return listOf(hingeStatus)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    /**
     * Foldable support landed in Android 11, together with the hinge angle sensor that identifies a foldable.
     * The device also needs Window SDK extensions recent enough to report the layout without an activity,
     * otherwise the layout is always empty and the sensor would report closed on an open device.
     */
    override fun hasSensor(): Boolean {
        return SdkVersion.isAtLeast(Build.VERSION_CODES.R) &&
            windowExtensionsVersion >= WINDOW_CONTEXT_MIN_EXTENSIONS_VERSION &&
            applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
    }

    override suspend fun requestSensorUpdate() {
        if (!hasSensor() || !isEnabled(hingeStatus)) {
            return
        }

        val status = withTimeoutOrNull(FOLD_STATE_TIMEOUT) { hingeStatusFlow.firstOrNull() }
        if (status == null) {
            Timber.w("Hinge status not reported, skipping update")
            return
        }

        onSensorUpdated(hingeStatus, status.state, status.icon, mapOf())
    }

    /**
     * Requests an update of the sensor each time the device is folded or unfolded, including while
     * the app is in the background. Suspends until cancelled, or returns immediately when the device
     * is not a foldable.
     */
    suspend fun updateOnFoldChange() {
        if (!hasSensor()) {
            return
        }

        hingeStatusFlow.collect { status ->
            Timber.d("Hinge status: ${status.state}")
            if (isEnabled(hingeStatus)) {
                SensorReceiver.updateSensor(applicationContext, hingeStatus.id)
            }
        }
    }
}

/** Closed without a fold on the screen, otherwise as open as the fold reports. */
private fun WindowLayoutInfo.toHingeStatus(): HingeStatus {
    val fold = displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull() ?: return HingeStatus.Closed
    return when (fold.state) {
        FoldingFeature.State.HALF_OPENED -> HingeStatus.PartiallyOpen
        else -> HingeStatus.FullyOpen
    }
}

/**
 * Window layout of the default display, observed through a window context instead of an activity so
 * it keeps emitting while the app has no visible UI.
 */
@RequiresApi(Build.VERSION_CODES.R)
private fun defaultDisplayWindowLayoutInfo(applicationContext: Context): Flow<WindowLayoutInfo> {
    val defaultDisplay = applicationContext.getSystemService<DisplayManager>()?.getDisplay(Display.DEFAULT_DISPLAY)
        ?: return emptyFlow()
    // No window is ever added, so SYSTEM_ALERT_WINDOW permission is not needed: the permission guards addView, not the context.
    val windowContext = applicationContext
        .createDisplayContext(defaultDisplay)
        .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    return WindowInfoTracker.getOrCreate(windowContext).windowLayoutInfo(windowContext)
}
