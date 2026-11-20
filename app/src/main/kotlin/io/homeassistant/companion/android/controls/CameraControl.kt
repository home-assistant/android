package io.homeassistant.companion.android.controls

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.service.controls.Control
import android.service.controls.DeviceTypes
import android.service.controls.actions.ControlAction
import android.service.controls.templates.ThumbnailTemplate
import androidx.annotation.RequiresApi
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.util.STATE_UNAVAILABLE
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

private val THUMBNAIL_TIMEOUT = 2.seconds

@RequiresApi(Build.VERSION_CODES.S)
object CameraControl : HaControl {
    override suspend fun provideControlFeatures(
        context: Context,
        control: Control.StatefulBuilder,
        item: EntityDisplayWithContext,
        info: HaControlInfo,
    ): Control.StatefulBuilder {
        val entityPicturePath = item.cameraControls?.entityPicturePath
        val image = if (info.baseUrl != null && entityPicturePath != null) {
            getThumbnail(context, info.baseUrl + entityPicturePath)
        } else {
            null
        }
        val icon = if (image != null) {
            Icon.createWithBitmap(image)
        } else {
            Icon.createWithResource(context, R.drawable.control_camera_placeholder)
        }
        control.setControlTemplate(
            ThumbnailTemplate(
                item.entityId,
                item.rawState != STATE_UNAVAILABLE && image != null,
                icon,
                context.getString(commonR.string.widget_camera_contentdescription),
            ),
        )
        return control
    }

    override fun getDeviceType(item: EntityDisplayWithContext): Int = DeviceTypes.TYPE_CAMERA

    override fun getDomainString(context: Context, item: EntityDisplayWithContext): String =
        context.getString(commonR.string.domain_camera)

    override suspend fun performAction(
        integrationRepository: IntegrationRepository,
        action: ControlAction,
        serverId: Int,
    ): Boolean {
        // No action is received, Android immediately invokes long press
        return true
    }

    private suspend fun getThumbnail(context: Context, url: String): Bitmap? = withTimeoutOrNull(THUMBNAIL_TIMEOUT) {
        val request = ImageRequest.Builder(context)
            .data(url)
            // The bitmap is sent to the system UI, keep it software-backed
            .allowHardware(false)
            // The snapshot URL is stable while its content changes, always fetch it
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
        when (val result = context.imageLoader.execute(request)) {
            is SuccessResult -> result.image.toBitmap()

            is ErrorResult -> {
                Timber.e(result.throwable, "Couldn't download image for control")
                null
            }
        }
    }
}
