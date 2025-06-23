package io.homeassistant.companion.android.util.icondialog

import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.ITypeface
import java.text.Normalizer
import java.util.Locale

/**
 * Normalize [this] string, removing all diacritics, all unicode characters, hyphens,
 * apostrophes and more. Resulting text has only lowercase latin letters and digits.
 */
private fun String.normalize(locale: Locale): String {
    val normalized = this.lowercase(locale).trim().let { Normalizer.normalize(it, Normalizer.Form.NFKD) }
    return normalized.filter { c -> c in 'a'..'z' || c in 'а'..'я' || c in '0'..'9' }
}

/**
 * Class used to filter the icons for search and sort them afterwards.
 * Icon filter must be parcelable to be put in bundle.
 */
interface IconFilter {

    /**
     * Get a list of all matching icons for a search [query], in no specific order.
     */
    fun queryIcons(pack: ITypeface, query: String? = null): List<IIcon>
}

class DefaultIconFilter(
    /**
     * Regex used to split the query into multiple search terms.
     * Can also be null to not split the query.
     */
    private val termSplitPattern: Regex? = """[;,\s]""".toRegex(),
    /**
     * Whether to normalize search query or not, using [String.normalize].
     */
    private val queryNormalized: Boolean = true,
) : IconFilter {

    /**
     * Get a list of all matching icons for a search [query].
     * Base implementation only returns the complete list of icons in the pack,
     * sorted by ID. Subclasses take care of actual searching and must always ensure
     * that the returned list is sorted by ID.
     */
    override fun queryIcons(pack: ITypeface, query: String?): List<IIcon> {
        val icons = pack.icons

        if (query.isNullOrBlank()) {
            // No search query, return all icons.
            return icons.map { key -> pack.getIcon(key) }
        }

        // Split query into terms.
        val trimmedQuery = query.trim()
        val terms = (termSplitPattern?.let { trimmedQuery.split(it) } ?: listOf(trimmedQuery))
            .map {
                if (queryNormalized) {
                    it.normalize(Locale.ROOT)
                } else {
                    it.lowercase(Locale.ROOT)
                }
            }.filter { it.isNotBlank() }

        // Remove all icons that don't match any of the search terms.
        return icons
            .filter { icon -> matchesSearch(icon, terms) }
            .map { key -> pack.getIcon(key) }
    }

    /**
     * Check if an [icon] name matches any of the search [terms].
     */
    private fun matchesSearch(icon: String, terms: List<String>): Boolean {
        val name = if (queryNormalized) icon.normalize(Locale.ROOT) else icon
        return terms.any { it in name }
    }
}
