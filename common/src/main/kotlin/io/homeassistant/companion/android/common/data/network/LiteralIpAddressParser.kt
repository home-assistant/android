package io.homeassistant.companion.android.common.data.network

import android.net.InetAddresses
import android.os.Build
import java.net.Inet6Address
import java.net.InetAddress

private val IPV4_PATTERN = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

/**
 * Parses numeric IP literals for [NetworkAwareDns] without triggering DNS lookups.
 *
 * Hostnames that accidentally contain digits (or bracketed IPv6 in URLs) must not be mistaken
 * for literals. Values such as `192.168.1.1:8123` are rejected because [Dns.lookup] receives
 * hostnames without ports.
 */
internal object LiteralIpAddressParser {

    /**
     * Returns a numeric [InetAddress] when [host] is an IPv4 or IPv6 literal, otherwise `null`.
     */
    fun parse(host: String): InetAddress? {
        if (host.isBlank()) {
            return null
        }
        val stripped = stripOptionalBrackets(host) ?: return null
        if (containsZoneIndex(stripped) || isHostWithPort(stripped) || looksLikeUnbracketedIpv6WithPort(stripped)) {
            return null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            parseWithPlatformApi(stripped)?.let { return it }
        }
        return parseLegacy(stripped)
    }

    /**
     * Strips a single surrounding `[`/`]` pair when present; rejects mismatched brackets.
     */
    internal fun stripOptionalBrackets(host: String): String? {
        val hasLeadingBracket = host.startsWith("[")
        val hasTrailingBracket = host.endsWith("]")
        if (hasLeadingBracket != hasTrailingBracket) {
            return null
        }
        if (!hasLeadingBracket) {
            return host
        }
        if (host.length <= 2) {
            return null
        }
        return host.substring(1, host.length - 1)
    }

    /**
     * Returns `true` when [value] looks like an IPv4 host with a trailing port (`host:port`).
     */
    internal fun isHostWithPort(value: String): Boolean {
        if (value.count { it == ':' } != 1) {
            return false
        }
        val portSeparator = value.lastIndexOf(':')
        if (portSeparator <= 0 || portSeparator == value.lastIndex) {
            return false
        }
        if (value.substring(0, portSeparator).contains('.')) {
            val port = value.substring(portSeparator + 1)
            return port.isNotEmpty() && port.all { it.isDigit() }
        }
        return false
    }

    /**
     * Returns `true` when [value] looks like an unbracketed IPv6 literal with trailing port.
     */
    internal fun looksLikeUnbracketedIpv6WithPort(value: String): Boolean {
        if (value.count { it == ':' } < 3) {
            return false
        }
        val portSeparator = value.lastIndexOf(':')
        if (portSeparator <= 0 || portSeparator == value.lastIndex) {
            return false
        }
        val hostCandidate = value.substring(0, portSeparator)
        val port = value.substring(portSeparator + 1)
        if (port.isEmpty() || !port.all { it.isDigit() }) {
            return false
        }
        if (port.toIntOrNull()?.let { candidatePort -> candidatePort in 1..65535 } != true) {
            return false
        }
        return isPlausibleIpv6Literal(hostCandidate)
    }

    private fun containsZoneIndex(value: String): Boolean = value.contains('%')

    private fun parseWithPlatformApi(value: String): InetAddress? {
        return try {
            InetAddresses.parseNumericAddress(value)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseLegacy(value: String): InetAddress? {
        return if (value.contains(':')) {
            parseLegacyIpv6(value)
        } else {
            parseLegacyIpv4(value)
        }
    }

    private fun parseLegacyIpv4(value: String): InetAddress? {
        val match = IPV4_PATTERN.matchEntire(value) ?: return null
        val octets = match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
        if (octets.any { octet -> octet !in 0..255 }) {
            return null
        }
        return InetAddress.getByAddress(value, octets.map(Int::toByte).toByteArray())
    }

    private fun parseLegacyIpv6(value: String): InetAddress? {
        if (!isPlausibleIpv6Literal(value)) {
            return null
        }
        return try {
            Inet6Address.getByName(value)
        } catch (_: Exception) {
            null
        }
    }

    private fun isPlausibleIpv6Literal(value: String): Boolean {
        if (value.endsWith(':')) {
            return false
        }
        if (value.count { it == ':' } < 2) {
            return false
        }
        return value.all { character ->
            character.isDigit() ||
                character in 'a'..'f' ||
                character in 'A'..'F' ||
                character == ':'
        }
    }
}
