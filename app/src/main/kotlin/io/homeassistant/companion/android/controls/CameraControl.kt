package io.homeassistant.companion.android.controls

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import android.service.controls.Control
import android.service.controls.DeviceTypes
import android.service.controls.actions.ControlAction
import android.service.controls.templates.ThumbnailTemplate
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.util.STATE_UNAVAILABLE
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.S)
object CameraControl : HaControl {
    override fun provideControlFeatures(
        context: Context,
        control: Control.StatefulBuilder,
        entity: Entity,
        info: HaControlInfo,
    ): Control.StatefulBuilder {
        val image = if (info.baseUrl != null &&
            (entity.attributes["entity_picture"] as? String)?.isNotBlank() == true
        ) {
            getThumbnail(info.baseUrl + entity.attributes["entity_picture"] as String)
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
                entity.entityId,
                entity.state != STATE_UNAVAILABLE && image != null,
                icon,
                context.getString(commonR.string.widget_camera_contentdescription),
            ),
        )
        return control
    }

    override fun getDeviceType(entity: Entity): Int = DeviceTypes.TYPE_CAMERA

    override fun getDomainString(context: Context, entity: Entity): String =
        context.getString(commonR.string.domain_camera)

    override suspend fun performAction(integrationRepository: IntegrationRepository, action: ControlAction): Boolean {
        // No action is received, Android immediately invokes long press
        return true
    }

    private fun getThumbnail(path: String): Bitmap? = runBlocking {
        var image: Bitmap? = null
        withTimeoutOrNull(TimeUnit.SECONDS.toMillis(2)) {
            withContext(Dispatchers.IO) {
                try {
                    image = BitmapFactory.decodeStream(URL(path).openStream())
                } catch (e: Exception) {
                    Timber.e(e, "Couldn't download image for control")
                }
            }
        }
        return@runBlocking image
    }
}
