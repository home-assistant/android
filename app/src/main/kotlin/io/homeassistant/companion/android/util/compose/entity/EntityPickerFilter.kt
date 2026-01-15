package io.homeassistant.companion.android.util.compose.entity

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.text.similarity.LevenshteinDistance

/**
 * Configuration for fuzzy search behavior, similar to Fuse.js options.
 */
private object FuzzySearchConfig {
    /**
     * Fuzzy match threshold. Lower values require closer matches.
     * - 0.0 = exact match required
     * - 1.0 = match anything
     * Similar to Fuse.js threshold (0.3)
     */
    const val THRESHOLD = 0.3

    /**
     * Minimum number of characters required in a search term before matching.
     * Similar to Fuse.js minMatchCharLength (2)
     */
    const val MIN_MATCH_CHAR_LENGTH = 2

    /**
     * Field weights for scoring, matching the frontend implementation.
     */
    object Weights {
        const val FRIENDLY_NAME = 8
        const val DEVICE_NAME = 7
        const val AREA_NAME = 6
        const val DOMAIN_NAME = 6
        const val ENTITY_ID = 3
    }
}

/**
 * Entity with pre-computed searchable fields for performance optimization.
 * This avoids rebuilding searchable fields on every search query change.
 *
 * Exposed as internal for testing purposes.
 *
 * @param entity The original entity
 * @param sortingKey Pre-computed key used for final sorting the entities
 */
@VisibleForTesting
internal data class EntityWithSearchFields(
    val entity: EntityPickerItem,
    val searchableFields: List<SearchField>,
    val sortingKey: String,
)

/**
 * Composable function that filters and sorts entities with optimized caching.
 *
 * This function caches the searchable fields mapping using `remember`, so they're only
 * computed once when the entities list changes, not on every query change.
 *
 * @param entities The list of entities to search through
 * @param searchQuery The search query string
 * @return A state holding the filtered and sorted list of entities
 */
@Composable
internal fun rememberFilteredEntities(
    entities: List<EntityPickerItem>,
    searchQuery: String,
    dispatcher: CoroutineContext = Dispatchers.Default,
): List<EntityPickerItem> {
    // Cache the entities with pre-computed searchable fields
    // Computed on background to avoid blocking UI
    var entitiesWithFields by remember { mutableStateOf<List<EntityWithSearchFields>>(emptyList()) }

    LaunchedEffect(entities) {
        entitiesWithFields = entities.mapToEntitiesWithFields(dispatcher)
    }

    var filteredEntities by remember { mutableStateOf(entities) }

    LaunchedEffect(entitiesWithFields, searchQuery) {
        filteredEntities = filterAndSortEntitiesOptimized(entitiesWithFields, searchQuery, dispatcher)
    }

    return filteredEntities
}

private suspend fun List<EntityPickerItem>.mapToEntitiesWithFields(
    dispatcher: CoroutineContext,
): List<EntityWithSearchFields> = withContext(dispatcher) {
    return@withContext map { entity ->
        val sortingKey = entity.friendlyName.lowercase()

        EntityWithSearchFields(
            entity = entity,
            sortingKey = sortingKey,
            searchableFields = buildList {
                // Store fields in lowercase to avoid repeated conversions during search
                add(SearchField(sortingKey, FuzzySearchConfig.Weights.FRIENDLY_NAME))
                entity.deviceName?.let {
                    add(SearchField(it.lowercase(), FuzzySearchConfig.Weights.DEVICE_NAME))
                }
                entity.areaName?.let { add(SearchField(it.lowercase(), FuzzySearchConfig.Weights.AREA_NAME)) }
                add(SearchField(entity.domain.lowercase(), FuzzySearchConfig.Weights.DOMAIN_NAME))
                add(SearchField(entity.entityId.lowercase(), FuzzySearchConfig.Weights.ENTITY_ID))
            },
        )
    }
}

/**
 * Optimized version that uses pre-computed searchable fields.
 *
 * @param entitiesWithFields List of entities with pre-computed searchable fields
 * @param query The search query string
 * @return A filtered and sorted list of entities
 */
@VisibleForTesting
internal suspend fun filterAndSortEntitiesOptimized(
    entitiesWithFields: List<EntityWithSearchFields>,
    query: String,
    dispatcher: CoroutineContext = Dispatchers.Default,
): List<EntityPickerItem> = withContext(dispatcher) {
    val trimmedQuery = query.trim()

    if (trimmedQuery.isBlank()) {
        return@withContext entitiesWithFields
            .sortedBy { it.sortingKey }
            .map { it.entity }
    }

    // Split query into terms (space-separated)
    val terms = trimmedQuery.lowercase()
        .split(" ")
        .map { it.trim() }
        .filter { it.length >= FuzzySearchConfig.MIN_MATCH_CHAR_LENGTH }

    if (terms.isEmpty()) {
        return@withContext entitiesWithFields
            .sortedBy { it.sortingKey }
            .map { it.entity }
    }

    // Score each entity using pre-computed fields
    val scoredEntities = entitiesWithFields.mapNotNull { entityWithFields ->
        val score = calculateFuzzyMatchScoreOptimized(entityWithFields.searchableFields, terms)
        if (score > 0.0) {
            ScoredEntity(
                entity = entityWithFields.entity,
                score = score,
                sortingKey = entityWithFields.sortingKey,
            )
        } else {
            null
        }
    }

    // Sort by score (descending) then by friendly name (ascending)
    scoredEntities
        .sortedWith(
            compareByDescending<ScoredEntity> { it.score }
                .thenBy { it.sortingKey },
        )
        .map { it.entity }
}

/**
 * Calculates fuzzy match score using pre-computed searchable fields.
 *
 * Exposed as internal for testing purposes.
 *
 * @param searchableFields Pre-computed searchable fields with weights
 * @param terms The list of search terms (already normalized to lowercase)
 * @return The total match score (0.0 if any term doesn't match)
 */
@VisibleForTesting
internal fun calculateFuzzyMatchScoreOptimized(searchableFields: List<SearchField>, terms: List<String>): Double {
    var totalScore = 0.0

    // Each term must match at least one field
    for (term in terms) {
        var bestMatchForTerm = 0.0

        for (field in searchableFields) {
            // Field values are already stored in lowercase
            val similarity = fuzzyMatch(field.value, term)

            // Check if similarity meets threshold
            if (similarity >= (1.0 - FuzzySearchConfig.THRESHOLD)) {
                // Calculate weighted score: similarity * weight
                val weightedScore = similarity * field.weight
                bestMatchForTerm = max(bestMatchForTerm, weightedScore)
            }
        }

        // If any term has no match, the entity doesn't match at all
        if (bestMatchForTerm == 0.0) {
            return 0.0
        }

        totalScore += bestMatchForTerm
    }

    return totalScore
}

/**
 * Performs fuzzy string matching using Apache Commons Text's Levenshtein distance algorithm.
 *
 * Exposed as internal for testing purposes.
 *
 * Returns a similarity score between 0.0 and 1.0:
 * - 1.0 = exact match (or pattern found as substring)
 * - < 1.0 = fuzzy match with some differences
 * - 0.0 = no reasonable match
 *
 * The algorithm:
 * 1. First checks for exact substring match (returns 1.0)
 * 2. Then checks for prefix match (returns high score)
 * 3. Finally calculates Levenshtein distance for fuzzy matching using Apache Commons Text
 *
 * @param text The text to search in (should be lowercase)
 * @param pattern The pattern to search for (should be lowercase)
 * @return Similarity score from 0.0 to 1.0
 */
internal fun fuzzyMatch(text: String, pattern: String): Double {
    if (pattern.isEmpty()) return 0.0
    if (text.isEmpty()) return 0.0

    // Exact substring match
    if (text.contains(pattern)) {
        return 1.0
    }

    // Prefix match gets high score
    if (text.startsWith(pattern)) {
        return 0.95
    }

    // Calculate Levenshtein distance using Apache Commons Text
    val distance = LevenshteinDistance.getDefaultInstance().apply(text, pattern)
    val maxLength = max(text.length, pattern.length)

    // Normalize to similarity score (0.0 to 1.0)
    // If distance is too large relative to pattern length, it's not a match
    if (distance > pattern.length * 2) {
        return 0.0
    }

    return 1.0 - (distance.toDouble() / maxLength)
}

/**
 * Represents a searchable field with its value and weight.
 *
 * Exposed as internal for testing purposes.
 *
 * @param value The field value to search in (should be stored in lowercase for performance)
 * @param weight The importance weight of this field
 */
internal data class SearchField(val value: String, val weight: Int)

/**
 * Internal data class to hold an entity with its calculated match score.
 *
 * Exposed as internal for testing purposes.
 *
 * @param entity The entity item
 * @param score The calculated match score
 * @param sortingKey Cached lowercase friendly name for efficient sorting
 */
internal data class ScoredEntity(val entity: EntityPickerItem, val score: Double, val sortingKey: String)
