package io.homeassistant.companion.android.common.util

import io.homeassistant.companion.android.common.util.AppVersion.Companion.from
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

private val versionRegex = """^(.*) \((\d+)\)$""".toRegex()

/** Version code used when parsing a raw version that does not carry one. */
const val UNKNOWN_VERSION_CODE = 0

/**
 * Represents the app version as a version [name] and [code], serialized and displayed as
 * "BuildConfig.VERSION_NAME (BuildConfig.VERSION_CODE)".
 *
 * Use the constructor when the name and code are known, or [from] to parse a raw version string.
 *
 * @property name The version name, for instance `2025.8.1`.
 * @property code The version code, or [UNKNOWN_VERSION_CODE] if it could not be parsed.
 */
@Serializable(with = AppVersionSerializer::class)
data class AppVersion(val name: String, val code: Int) {
    override fun toString(): String = "$name ($code)"

    companion object {
        /**
         * Parses [rawVersion] in the format "BuildConfig.VERSION_NAME (BuildConfig.VERSION_CODE)".
         *
         * @throws FailFastException (in debug builds only) if [rawVersion] does not match the expected pattern.
         * In release builds the whole string becomes [name] and [code] falls back to [UNKNOWN_VERSION_CODE].
         */
        fun from(rawVersion: String): AppVersion {
            val match = versionRegex.matchEntire(rawVersion)
            val code = match?.groupValues?.get(2)?.toIntOrNull()
            FailFast.failWhen(code == null) {
                "Invalid app version: $rawVersion it should follow the pattern \"BuildConfig.VERSION_NAME (BuildConfig.VERSION_CODE)\""
            }
            return if (match != null && code != null) {
                AppVersion(match.groupValues[1], code)
            } else {
                AppVersion(rawVersion, UNKNOWN_VERSION_CODE)
            }
        }
    }
}

internal object AppVersionSerializer : KSerializer<AppVersion> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("AppVersion", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AppVersion) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): AppVersion = from(decoder.decodeString())
}
