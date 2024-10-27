package io.homeassistant.companion.android.common.util.feature

/**
 * This file represents the "Feature" concept, inspired by the feature toggle methodology as discussed by
 * [Baracoda](https://baracoda.com/blog/trunk-based-development).
 *
 * Features are temporary constructs. Once a feature is fully integrated and approved, both the feature and any
 * associated code should be removed to maintain a clean, manageable codebase. For applications like HomeAssistant,
 * this approach is especially useful for refactoring legacy code, allowing contributors to maintain a steady release
 * pace without creating large, complex pull requests. This helps support a trunk-based development workflow.
 *
 * ## Key Characteristics and Design Principles
 *
 * - **Automatic Feature Listing**: All features should be discoverable automatically without the need to
 *   manually construct a registry. This is achieved using Dependency Injection (DI), ensuring all features are
 *   properly initialized and easily accessible within the codebase.
 *
 * - **Unique Definitions**: Each feature must be uniquely defined across the entire codebase. This allows
 *   developers to define features in any part of the code while maintaining a unique, identifiable feature list.
 *
 * - **Separation of Definition and Value**: A feature's definition is separate from its value, providing
 *   a clear abstraction layer. This separation allows developers to define feature logic independently from its
 *   storage, making feature management more flexible.
 *
 * - **Updatable or Non-Updatable by Design**: Contributors can determine whether a feature is updatable
 *   from within the app. For instance, if a feature is meant to be purely internal and has no impact on
 *   user-facing behavior, it can be marked as non-updatable.
 *
 * - **Flexible Value Storage**: The mechanism for storing a feature's value is customizable based on the
 *   feature creator’s requirements. The system could supports a prioritization chain, where a feature can check for
 *   values from local storage, fall back to remote storage, and finally default to the predefined value if no
 *   other value is available.
 *
 * - **Type Flexibility**: Features can hold any type of value. While simple primitives are recommended
 *   to avoid complex serialization issues, the system allows flexibility in type choice.
 *
 * - **Compile-Time Type Safety**: Type safety should be enforced at compile-time, reducing runtime errors
 *   and ensuring strong consistency in feature use. For example, introducing a new type within the scope of
 *   `Type Flexibility` should result in a compilation error until the new type is fully supported.
 *
 * - **Platform Agnosticism**: The feature management system is designed to be platform-agnostic, allowing it to be
 *   used across various environments and projects beyond Android. This flexibility supports the reusability of
 *   features in different contexts, making the system adaptable and transferable across different application
 *   architectures.
 *
 * ## Implementation and Extensibility
 *
 * The simplest implementation of this concept stores feature values locally. However, the design allows for
 * easy extensibility, enabling integration with remote platforms like Firebase or Split for A/B testing and
 * other advanced use cases. This extensible design makes it possible to adapt the feature concept based on
 * project needs and community feedback.
 *
 * ## Usage Guidelines
 *
 * Features should be defined close to the related code for better context and maintainability.
 * To retrieve the value of a feature, utilize the [FeatureValuesStore] interface, which provides a centralized
 * mechanism for accessing feature values throughout the application.
 */

/**
 * Represents a feature with a name and default value for display and control purposes.
 *
 * Using a sealed interface allows leveraging the `when` operator when implementing feature logic,
 * enhancing type safety within the code. The sealed class should encompass all supported types
 * within the system.
 *
 * A `FeatureDefinition` is likely to be structured as a `data object`.
 *
 * This implementation requires that the feature be defined explicitly and not through an anonymous class.
 * Attempting to use an anonymous class will result in an exception, as the class's [Class.canonicalName] is used
 * as a key in local storage.
 *
 * A feature should include:
 * - **Name**: Used for display purposes, providing a recognizable identifier for the feature.
 * - **Default Value**: This value is applied if no stored value is available, allowing developers
 *   to control the default behavior of the feature in the absence of user-defined values.
 * - **Key**: Used as unique identifier of the feature, deduced from the canonicalName of the class that implement the interface.
 *
 * @param T The type of value associated with the feature.
 *
 * @throws IllegalStateException If the feature class is anonymous or a lambda when retrieving the canonical name of the feature.
 */
sealed interface FeatureDefinition<T> {
    val featureName: String
    val defaultValue: T

    // We could consider implementing a FailEarly mechanism to prevent app crashes in release builds by falling back to the default value when the key is null.
    val key: String
        get() = checkNotNull(this::class.java.canonicalName) { "The class for the feature ($featureName) should be non-anonymous nor a lambda otherwise" }

    // Supported types
    interface BooleanFeatureDefinition : FeatureDefinition<Boolean>
    interface StringFeatureDefinition : FeatureDefinition<String>
}

/**
 * Represents a value associated with a [FeatureDefinition], allowing retrieval and potential updating based on the feature’s configuration.
 *
 * The `FeatureValue` interface supports the concept of multiple value sources (e.g., local, remote, code),
 * providing a flexible, prioritized approach to retrieving feature values.
 *
 * @param T The type of value associated with the feature.
 */
interface FeatureValue<T> {
    val feature: FeatureDefinition<T>

    /**
     * Retrieves the current value of the feature. This function is `suspendable` because the value source
     * may involve asynchronous operations (e.g., fetching from a remote server or database).
     * The function can access multiple sources in a priority chain (e.g., local storage, remote configuration,
     * or default), depending on availability.
     *
     * @return The current value of the feature.
     */
    suspend fun getValue(): T

    /**
     * Checks if the feature is updatable, based on whether it implements the `UpdatableFeatureValue` interface.
     *
     * @return `true` if the feature is updatable; `false` otherwise.
     */
    fun isFeatureUpdatable(): Boolean = this is UpdatableFeatureValue

    /**
     * Represents an updatable feature value, where the value can be changed by the application.
     * Some features might be designed to be non-updatable (e.g., values derived from an external source).
     */
    interface UpdatableFeatureValue<T> : FeatureValue<T> {

        /**
         * Updates the value of the feature to the specified new value.
         * This function is `suspendable` because the value source may involve asynchronous operations (e.g., storing into a database).
         *
         * @param newValue The new value to be assigned to the feature.
         */
        suspend fun updateValue(newValue: T)
    }
}

/**
 * Manages the storage and retrieval of all [FeatureValue] within the codebase, ensuring a single instance
 * for consistent access and uniqueness of features across the application.
 *
 * The `FeatureValuesStore` interface provides methods to access individual [FeatureValue] by their definition
 * and to retrieve a comprehensive list of all available [FeatureDefinition] for display or management purposes.
 *
 * It is intended that there is only one instance of this store, which encapsulates the logic required to
 * maintain unique feature definitions throughout the application.
 */
interface FeatureValuesStore {
    /**
     * Retrieves a [FeatureValue] instance based on the provided [FeatureDefinition].
     *
     * @param T The type of value associated with the feature.
     * @param feature The feature definition used to identify and retrieve the corresponding value.
     * @return The `FeatureValue` associated with the given feature definition.
     */
    fun <T> getFeatureValue(feature: FeatureDefinition<T>): FeatureValue<T>

    /**
     * Returns a set of all feature values managed by this store, useful for displaying or listing
     * all features available within the application.
     *
     * @return A set containing each unique [FeatureValue] instance managed by the store.
     */
    fun featuresValue(): Set<FeatureValue<*>>

    /**
     * Returns a set of all feature definitions managed by this store, useful for displaying or listing
     * all features available within the application.
     *
     * @return A set containing each unique [FeatureDefinition] instance managed by the store.
     */
    fun features(): Set<FeatureDefinition<*>>
}
