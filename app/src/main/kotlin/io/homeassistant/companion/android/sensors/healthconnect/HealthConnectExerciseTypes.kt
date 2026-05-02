package io.homeassistant.companion.android.sensors.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord

/**
 * Local copy of `ExerciseSessionRecord.EXERCISE_TYPE_STRING_TO_INT_MAP` /
 * `EXERCISE_TYPE_INT_TO_STRING_MAP`. Both SDK maps are `@RestrictTo(LIBRARY)`, so we mirror
 * them here with the same string slugs the SDK uses internally (constant name minus the
 * `EXERCISE_TYPE_` prefix, lowercased).
 *
 * Used in two places:
 *  - The HC → HA read sensor exposes the slug as the entity state.
 *  - The HA → HC write payload accepts the slug and resolves it to the int constant.
 *
 * Add new types when androidx.health.connect ships them — the test in
 * `HealthConnectExerciseTypesTest` (if/when added) catches drift.
 */
internal object HealthConnectExerciseTypes {

    val SLUG_TO_INT: Map<String, Int> = mapOf(
        "other_workout" to ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
        "badminton" to ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON,
        "baseball" to ExerciseSessionRecord.EXERCISE_TYPE_BASEBALL,
        "basketball" to ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL,
        "biking" to ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        "biking_stationary" to ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
        "boot_camp" to ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP,
        "boxing" to ExerciseSessionRecord.EXERCISE_TYPE_BOXING,
        "calisthenics" to ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS,
        "cricket" to ExerciseSessionRecord.EXERCISE_TYPE_CRICKET,
        "dancing" to ExerciseSessionRecord.EXERCISE_TYPE_DANCING,
        "elliptical" to ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL,
        "exercise_class" to ExerciseSessionRecord.EXERCISE_TYPE_EXERCISE_CLASS,
        "fencing" to ExerciseSessionRecord.EXERCISE_TYPE_FENCING,
        "football_american" to ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN,
        "football_australian" to ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AUSTRALIAN,
        "frisbee_disc" to ExerciseSessionRecord.EXERCISE_TYPE_FRISBEE_DISC,
        "golf" to ExerciseSessionRecord.EXERCISE_TYPE_GOLF,
        "guided_breathing" to ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING,
        "gymnastics" to ExerciseSessionRecord.EXERCISE_TYPE_GYMNASTICS,
        "handball" to ExerciseSessionRecord.EXERCISE_TYPE_HANDBALL,
        "high_intensity_interval_training" to ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
        "hiking" to ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
        "ice_hockey" to ExerciseSessionRecord.EXERCISE_TYPE_ICE_HOCKEY,
        "ice_skating" to ExerciseSessionRecord.EXERCISE_TYPE_ICE_SKATING,
        "martial_arts" to ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS,
        "paddling" to ExerciseSessionRecord.EXERCISE_TYPE_PADDLING,
        "paragliding" to ExerciseSessionRecord.EXERCISE_TYPE_PARAGLIDING,
        "pilates" to ExerciseSessionRecord.EXERCISE_TYPE_PILATES,
        "racquetball" to ExerciseSessionRecord.EXERCISE_TYPE_RACQUETBALL,
        "rock_climbing" to ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING,
        "roller_hockey" to ExerciseSessionRecord.EXERCISE_TYPE_ROLLER_HOCKEY,
        "rowing" to ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
        "rowing_machine" to ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
        "rugby" to ExerciseSessionRecord.EXERCISE_TYPE_RUGBY,
        "running" to ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        "running_treadmill" to ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
        "sailing" to ExerciseSessionRecord.EXERCISE_TYPE_SAILING,
        "scuba_diving" to ExerciseSessionRecord.EXERCISE_TYPE_SCUBA_DIVING,
        "skating" to ExerciseSessionRecord.EXERCISE_TYPE_SKATING,
        "skiing" to ExerciseSessionRecord.EXERCISE_TYPE_SKIING,
        "snowboarding" to ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING,
        "snowshoeing" to ExerciseSessionRecord.EXERCISE_TYPE_SNOWSHOEING,
        "soccer" to ExerciseSessionRecord.EXERCISE_TYPE_SOCCER,
        "softball" to ExerciseSessionRecord.EXERCISE_TYPE_SOFTBALL,
        "squash" to ExerciseSessionRecord.EXERCISE_TYPE_SQUASH,
        "stair_climbing" to ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING,
        "stair_climbing_machine" to ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE,
        "strength_training" to ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        "stretching" to ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING,
        "surfing" to ExerciseSessionRecord.EXERCISE_TYPE_SURFING,
        "swimming_open_water" to ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
        "swimming_pool" to ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        "table_tennis" to ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS,
        "tennis" to ExerciseSessionRecord.EXERCISE_TYPE_TENNIS,
        "volleyball" to ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL,
        "walking" to ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
        "water_polo" to ExerciseSessionRecord.EXERCISE_TYPE_WATER_POLO,
        "weightlifting" to ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
        "wheelchair" to ExerciseSessionRecord.EXERCISE_TYPE_WHEELCHAIR,
        "yoga" to ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
    )

    val INT_TO_SLUG: Map<Int, String> = SLUG_TO_INT.entries.associate { (k, v) -> v to k }
}
