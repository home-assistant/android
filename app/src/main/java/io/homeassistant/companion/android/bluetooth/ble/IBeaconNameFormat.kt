package io.homeassistant.companion.android.bluetooth.ble

fun <T> name(uuid: T, major: T, minor: T): String {
    return "${uuid}_${major}_$minor"
}
