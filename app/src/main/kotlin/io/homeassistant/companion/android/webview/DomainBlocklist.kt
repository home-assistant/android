package io.homeassistant.companion.android.webview

import android.content.Context

object DomainBlocklist {
    private const val PREFS_NAME = "domain_blocklist"
    private const val KEY_BLOCKED_DOMAINS = "blocked_domains"

    fun normalize(domain: String): String =
        runCatching { java.net.IDN.toASCII(domain).lowercase() }.getOrDefault(domain.lowercase())

    fun getBlockedDomains(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_BLOCKED_DOMAINS, emptySet())
            ?.map { normalize(it) }
            ?.toSet()
            ?: emptySet()
    }

    fun addDomain(context: Context, domain: String) {
        if (domain.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = getBlockedDomains(context).toMutableSet()
        updated.add(normalize(domain))
        prefs.edit().putStringSet(KEY_BLOCKED_DOMAINS, updated).apply()
    }

    fun removeDomain(context: Context, domain: String) {
        if (domain.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = getBlockedDomains(context).toMutableSet()
        updated.remove(normalize(domain))
        prefs.edit().putStringSet(KEY_BLOCKED_DOMAINS, updated).apply()
    }
}
