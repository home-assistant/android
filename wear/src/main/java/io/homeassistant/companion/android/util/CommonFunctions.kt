package io.homeassistant.companion.android.util

import android.content.Context
import android.widget.Toast
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.home.HomePresenterImpl
import java.util.Calendar
import io.homeassistant.companion.android.common.R as commonR

fun stringForDomain(domain: String, context: Context): String? =
    (
        HomePresenterImpl.domainsWithNames + mapOf(
            "automation" to commonR.string.automation,
            "binary_sensor" to commonR.string.binary_sensor,
            "device_tracker" to commonR.string.device_tracker,
            "input_number" to commonR.string.domain_input_number,
            "media_player" to commonR.string.media_player,
            "persistent_notification" to commonR.string.persistent_notification,
            "person" to commonR.string.person,
            "select" to commonR.string.select,
            "sensor" to commonR.string.sensor,
            "sun" to commonR.string.sun,
            "update" to commonR.string.update,
            "weather" to commonR.string.weather,
            "zone" to commonR.string.zone
        )
        )[domain]?.let { context.getString(it) }

fun getIcon(icon: String?, domain: String, context: Context): IIcon? {
    val simpleEntity = Entity(
        "",
        "",
        mapOf("icon" to icon),
        Calendar.getInstance(),
        Calendar.getInstance(),
        null
    )
    return getIcon(simpleEntity as Entity<Map<String, Any>>, domain, context)
}

fun getIcon(entity: Entity<Map<String, Any>>?, domain: String, context: Context): IIcon? {
    val icon = entity?.attributes?.get("icon") as? String
    return if (icon?.startsWith("mdi") == true) {
        getIconFromMdiString(icon, context)
    } else {
        /**
         * Return a default icon for the domain that matches the icon used in the frontend, see
         * https://github.com/home-assistant/frontend/blob/dev/src/common/entity/domain_icon.ts.
         * Note: for SimplifiedEntity sometimes return a more general icon because we don't have state.
         */
        val compareState =
            if (entity?.state?.isNotBlank() == true)
                entity.state
            else
                entity?.attributes?.get("state") as String?
        when (domain) {
            "alert" -> CommunityMaterial.Icon.cmd_alert
            "air_quality" -> CommunityMaterial.Icon.cmd_air_filter
            "automation" -> CommunityMaterial.Icon3.cmd_robot
            "button" -> when (entity?.attributes?.get("device_class")) {
                "restart" -> CommunityMaterial.Icon3.cmd_restart
                "update" -> CommunityMaterial.Icon3.cmd_package_up
                else -> CommunityMaterial.Icon2.cmd_gesture_tap_button
            }
            "calendar" -> CommunityMaterial.Icon.cmd_calendar
            "camera" -> CommunityMaterial.Icon3.cmd_video
            "climate" -> CommunityMaterial.Icon3.cmd_thermostat
            "configurator" -> CommunityMaterial.Icon.cmd_cog
            "conversation" -> CommunityMaterial.Icon3.cmd_text_to_speech
            "cover" -> coverIcon(compareState, entity)
            "counter" -> CommunityMaterial.Icon.cmd_counter
            "fan" -> CommunityMaterial.Icon2.cmd_fan
            "google_assistant" -> CommunityMaterial.Icon2.cmd_google_assistant
            "group" -> CommunityMaterial.Icon2.cmd_google_circles_communities
            "homeassistant" -> CommunityMaterial.Icon2.cmd_home_assistant
            "homekit" -> CommunityMaterial.Icon2.cmd_home_automation
            "image_processing" -> CommunityMaterial.Icon2.cmd_image_filter_frames
            "input_boolean" -> if (entity?.entityId?.isNotBlank() == true) {
                if (compareState == "on")
                    CommunityMaterial.Icon.cmd_check_circle_outline
                else
                    CommunityMaterial.Icon.cmd_close_circle_outline
            } else { // For SimplifiedEntity without state, use a more generic icon
                CommunityMaterial.Icon2.cmd_light_switch
            }
            "input_button" -> CommunityMaterial.Icon2.cmd_gesture_tap_button
            "input_datetime" -> if (entity?.attributes?.get("has_date") == false)
                CommunityMaterial.Icon.cmd_clock
            else if (entity?.attributes?.get("has_time") == false)
                CommunityMaterial.Icon.cmd_calendar
            else
                CommunityMaterial.Icon.cmd_calendar_clock
            "input_select" -> CommunityMaterial.Icon2.cmd_format_list_bulleted
            "input_text" -> CommunityMaterial.Icon2.cmd_form_textbox
            "light" -> CommunityMaterial.Icon2.cmd_lightbulb
            "lock" -> when (compareState) {
                "unlocked" -> CommunityMaterial.Icon2.cmd_lock_open
                "jammed" -> CommunityMaterial.Icon2.cmd_lock_alert
                "locking", "unlocking" -> CommunityMaterial.Icon2.cmd_lock_clock
                else -> CommunityMaterial.Icon2.cmd_lock
            }
            "mailbox" -> CommunityMaterial.Icon3.cmd_mailbox
            "notify" -> CommunityMaterial.Icon.cmd_comment_alert
            "number" -> CommunityMaterial.Icon3.cmd_ray_vertex
            "persistent_notification" -> CommunityMaterial.Icon.cmd_bell
            "person" -> CommunityMaterial.Icon.cmd_account
            "plant" -> CommunityMaterial.Icon2.cmd_flower
            "proximity" -> CommunityMaterial.Icon.cmd_apple_safari
            "remote" -> CommunityMaterial.Icon3.cmd_remote
            "scene" -> CommunityMaterial.Icon3.cmd_palette_outline // Different from frontend: outline version
            "script" -> CommunityMaterial.Icon3.cmd_script_text_outline // Different from frontend: outline version
            "select" -> CommunityMaterial.Icon2.cmd_format_list_bulleted
            "sensor" -> CommunityMaterial.Icon.cmd_eye
            "siren" -> CommunityMaterial.Icon.cmd_bullhorn
            "simple_alarm" -> CommunityMaterial.Icon.cmd_bell
            "sun" -> if (compareState == "above_horizon")
                CommunityMaterial.Icon3.cmd_white_balance_sunny
            else
                CommunityMaterial.Icon3.cmd_weather_night
            "switch" -> if (entity?.entityId?.isNotBlank() == true) {
                when (entity.attributes["device_class"]) {
                    "outlet" -> if (compareState == "on") CommunityMaterial.Icon3.cmd_power_plug else CommunityMaterial.Icon3.cmd_power_plug_off
                    "switch" -> if (compareState == "on") CommunityMaterial.Icon3.cmd_toggle_switch else CommunityMaterial.Icon3.cmd_toggle_switch_off
                    else -> CommunityMaterial.Icon2.cmd_flash
                }
            } else { // For SimplifiedEntity without state, use a more generic icon
                CommunityMaterial.Icon2.cmd_light_switch
            }
            "timer" -> CommunityMaterial.Icon3.cmd_timer_outline
            "updater" -> CommunityMaterial.Icon.cmd_cloud_upload
            "vacuum" -> CommunityMaterial.Icon3.cmd_robot_vacuum
            "water_heater" -> CommunityMaterial.Icon3.cmd_thermometer
            "weather" -> CommunityMaterial.Icon3.cmd_weather_cloudy
            "zone" -> CommunityMaterial.Icon3.cmd_map_marker_radius
            else -> CommunityMaterial.Icon.cmd_bookmark
        }
    }
}

fun getIconFromMdiString(icon: String, context: Context): IIcon {
    val mdiIcon = icon.split(":")[1]
    return IconicsDrawable(context, "cmd-$mdiIcon").icon ?: CommunityMaterial.Icon.cmd_bookmark
}

private fun coverIcon(state: String?, entity: Entity<Map<String, Any>>?): IIcon? {
    val open = state !== "closed"

    return when (entity?.attributes?.get("device_class")) {
        "garage" -> when (state) {
            "opening" -> CommunityMaterial.Icon.cmd_arrow_up_box
            "closing" -> CommunityMaterial.Icon.cmd_arrow_down_box
            "closed" -> CommunityMaterial.Icon2.cmd_garage
            else -> CommunityMaterial.Icon2.cmd_garage_open
        }
        "gate" -> when (state) {
            "opening", "closing" -> CommunityMaterial.Icon2.cmd_gate_arrow_right
            "closed" -> CommunityMaterial.Icon2.cmd_gate
            else -> CommunityMaterial.Icon2.cmd_gate_open
        }
        "door" -> if (open) CommunityMaterial.Icon.cmd_door_open else CommunityMaterial.Icon.cmd_door_closed
        "damper" -> if (open) CommunityMaterial.Icon.cmd_circle else CommunityMaterial.Icon.cmd_circle_slice_8
        "shutter" -> when (state) {
            "opening" -> CommunityMaterial.Icon.cmd_arrow_up_box
            "closing" -> CommunityMaterial.Icon.cmd_arrow_down_box
            "closed" -> CommunityMaterial.Icon3.cmd_window_shutter
            else -> CommunityMaterial.Icon3.cmd_window_shutter_open
        }
        "curtain" -> when (state) {
            "opening" -> CommunityMaterial.Icon.cmd_arrow_split_vertical
            "closing" -> CommunityMaterial.Icon.cmd_arrow_collapse_horizontal
            "closed" -> CommunityMaterial.Icon.cmd_curtains_closed
            else -> CommunityMaterial.Icon.cmd_curtains
        }
        "blind", "shade" -> when (state) {
            "opening" -> CommunityMaterial.Icon.cmd_arrow_up_box
            "closing" -> CommunityMaterial.Icon.cmd_arrow_down_box
            "closed" -> CommunityMaterial.Icon.cmd_blinds
            else -> CommunityMaterial.Icon.cmd_blinds_open
        }
        else -> when (state) {
            "opening" -> CommunityMaterial.Icon.cmd_arrow_up_box
            "closing" -> CommunityMaterial.Icon.cmd_arrow_down_box
            "closed" -> CommunityMaterial.Icon3.cmd_window_closed
            else -> CommunityMaterial.Icon3.cmd_window_open
        }
    }
}

fun onEntityClickedFeedback(isToastEnabled: Boolean, isHapticEnabled: Boolean, context: Context, friendlyName: String, haptic: HapticFeedback) {
    val message = context.getString(commonR.string.toast_message, friendlyName)
    onEntityFeedback(isToastEnabled, isHapticEnabled, message, context, haptic)
}

fun onEntityFeedback(isToastEnabled: Boolean, isHapticEnabled: Boolean, message: String, context: Context, haptic: HapticFeedback) {
    if (isToastEnabled)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    if (isHapticEnabled)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
}
