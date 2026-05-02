package io.homeassistant.companion.android.sensors.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Sanity checks for our local mirror of the HC SDK's exercise-type map.
 *
 * The SDK exposes the map as `@RestrictTo(LIBRARY)` so we can't reuse it. If the SDK adds
 * a new type and we don't mirror it here, the read sensor falls back to "unknown" and the
 * write payload rejects the slug with a helpful error — both are non-fatal degradations,
 * but the map is dense enough that a typo would silently mis-route an entry.
 */
@ExtendWith(ConsoleLogExtension::class)
class HealthConnectExerciseTypesTest {

    @Test
    fun `slug-to-int round-trips through int-to-slug`() {
        HealthConnectExerciseTypes.SLUG_TO_INT.forEach { (slug, intValue) ->
            assertEquals(
                slug,
                HealthConnectExerciseTypes.INT_TO_SLUG[intValue],
                "Round-trip mismatch for slug=$slug int=$intValue",
            )
        }
    }

    @Test
    fun `the most common slugs resolve to their HC constants`() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            HealthConnectExerciseTypes.SLUG_TO_INT["running"],
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
            HealthConnectExerciseTypes.SLUG_TO_INT["biking"],
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
            HealthConnectExerciseTypes.SLUG_TO_INT["swimming_pool"],
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
            HealthConnectExerciseTypes.SLUG_TO_INT["other_workout"],
        )
    }

    @Test
    fun `slugs are unique and lowercase snake_case`() {
        val slugs = HealthConnectExerciseTypes.SLUG_TO_INT.keys
        assertEquals(slugs.size, slugs.toSet().size, "Duplicate slugs detected")
        slugs.forEach { slug ->
            assertTrue(
                slug == slug.lowercase() && !slug.contains(' '),
                "Slug '$slug' must be lowercase with no spaces",
            )
        }
    }

    @Test
    fun `unknown slug resolves to null so the parser can reject it`() {
        assertEquals(null, HealthConnectExerciseTypes.SLUG_TO_INT["telepathy"])
    }
}
