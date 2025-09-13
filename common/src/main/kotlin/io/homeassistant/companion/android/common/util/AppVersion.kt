package io.homeassistant.companion.android.common.util

import io.homeassistant.companion.android.common.util.AppVersion.Companion.from
import kotlinx.serialization.Serializable

private val versionRegex = """^.* \(\d+\)$""".toRegex()

/**
 * Represents the app version in the format "BuildConfig.VERSION_NAME (BuildConfig.VERSION_CODE)".
 * This class ensures that the version string adheres to the specified pattern and enforce strong typing in the application.
 *
 * Use the [from] static methods to create instances of `AppVersion` to ensure proper formatting and validation.
 *
 * @property value The string representation of the app version.
 * @throws FailFastException (in debug builds only) if the provided version string does not match the expected pattern.
 */
@Serializable
@JvmInline
value class AppVersion private constructor(val value: String) {
    init {
        FailFast.failWhen(!versionRegex.matches(value)) {
            "Invalid app version: $value you should use `from` static function instead or follow this pattern \"BuildConfig.VERSION_NAME (BuildConfig.VERSION_CODE)\""
        }
    }

    companion object {
        fun from(versionName: String, versionCode: Int): AppVersion = AppVersion("$versionName ($versionCode)")
        fun from(rawVersion: String): AppVersion = AppVersion(rawVersion)
    }
}

fun interface AppVersionProvider {
    operator fun invoke(): AppVersion
}
