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
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.BookOpenBlankVariant
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

/**
 * Reports whether the screen of a foldable device is open.
 *
 * The state comes from the window layout of the default display rather than from the hinge angle
 * sensor: the system stops delivering sensor events to apps in the background, while it keeps
 * delivering window layout changes. The inner screen is the only one crossed by a fold, so the
 * device is open exactly when the layout reports a [FoldingFeature].
 */
@Singleton
class FoldableSensorManager @VisibleForTesting internal constructor(
    override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
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
        val foldableScreen = SensorManager.BasicSensor(
            "foldable_screen",
            "binary_sensor",
            commonR.string.basic_sensor_name_foldable_screen,
            commonR.string.sensor_description_foldable_screen,
            Mdi.BookOpenBlankVariant,
            deviceClass = "opening",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
    }

    private val isScreenOpen: Flow<Boolean> by lazy {
        windowLayoutInfo()
            .map { layoutInfo -> layoutInfo.displayFeatures.any { it is FoldingFeature } }
            .distinctUntilChanged()
    }

    override val name: Int
        get() = commonR.string.sensor_name_foldable

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return listOf(foldableScreen)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    /** Foldable support landed in Android 11, together with the hinge angle sensor that identifies a foldable. */
    override fun hasSensor(): Boolean {
        return SdkVersion.isAtLeast(Build.VERSION_CODES.R) &&
            applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
    }

    override suspend fun requestSensorUpdate() {
        if (!hasSensor() || !isEnabled(foldableScreen)) {
            return
        }

        val isOpen = withTimeoutOrNull(FOLD_STATE_TIMEOUT) { isScreenOpen.firstOrNull() }
        if (isOpen == null) {
            Timber.w("Fold state not reported, skipping update")
            return
        }

        onSensorUpdated(
            foldableScreen,
            isOpen,
            if (isOpen) Mdi.BookOpenBlankVariant else Mdi.Cellphone,
            mapOf(),
        )
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

        isScreenOpen.collect { isOpen ->
            Timber.d("Foldable screen open: $isOpen")
            if (isEnabled(foldableScreen)) {
                SensorReceiver.updateSensor(applicationContext, foldableScreen.id)
            }
        }
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
