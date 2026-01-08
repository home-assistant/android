// package io.homeassistant.companion.android.util.compose.helper
//
// import kotlinx.coroutines.async
// import kotlinx.coroutines.coroutineScope
//
// /**
// * Icon retrieval system following Home Assistant frontend logic
// */
//
// // ============================================================================
// // Data Classes
// // ============================================================================
//
// data class HassEntity(
//    val entityId: String,
//    val state: String,
//    val attributes: Map<String, Any?> = emptyMap()
// ) {
//    val deviceClass: String? get() = attributes["device_class"] as? String
//    val icon: String? get() = attributes["icon"] as? String
// }
//
// data class EntityRegistryEntry(
//    val entityId: String,
//    val icon: String? = null,
//    val platform: String? = null,
//    val translationKey: String? = null
// )
//
// data class IconTranslations(
//    val default: String? = null,
//    val state: Map<String, String>? = null,
//    val range: Map<Int, String>? = null,
//    val stateAttributes: Map<String, AttributeIconTranslations>? = null
// )
//
// data class AttributeIconTranslations(
//    val default: String,
//    val state: Map<String, String>? = null,
//    val range: Map<Int, String>? = null
// )
//
// // ============================================================================
// // Constants
// // ============================================================================
//
// object IconConstants {
//    /** Icon to use when no icon specified for domain. */
//    const val DEFAULT_DOMAIN_ICON = "mdi:bookmark"
//
//    /** Fallback icons for each domain */
//    val FALLBACK_DOMAIN_ICONS = mapOf(
//        "ai_task" to "mdi:star-four-points",
//        "air_quality" to "mdi:air-filter",
//        "alert" to "mdi:alert",
//        "automation" to "mdi:robot",
//        "calendar" to "mdi:calendar",
//        "climate" to "mdi:thermostat",
//        "configurator" to "mdi:cog",
//        "conversation" to "mdi:forum-outline",
//        "counter" to "mdi:counter",
//        "date" to "mdi:calendar",
//        "datetime" to "mdi:calendar-clock",
//        "demo" to "mdi:home-assistant",
//        "device_tracker" to "mdi:account",
//        "google_assistant" to "mdi:google-assistant",
//        "group" to "mdi:google-circles-communities",
//        "homeassistant" to "mdi:home-assistant",
//        "homekit" to "mdi:home-automation",
//        "image_processing" to "mdi:image-filter-frames",
//        "image" to "mdi:image",
//        "input_boolean" to "mdi:toggle-switch",
//        "input_button" to "mdi:button-pointer",
//        "input_datetime" to "mdi:calendar-clock",
//        "input_number" to "mdi:ray-vertex",
//        "input_select" to "mdi:format-list-bulleted",
//        "input_text" to "mdi:form-textbox",
//        "lawn_mower" to "mdi:robot-mower",
//        "light" to "mdi:lightbulb",
//        "notify" to "mdi:comment-alert",
//        "number" to "mdi:ray-vertex",
//        "persistent_notification" to "mdi:bell",
//        "person" to "mdi:account",
//        "plant" to "mdi:flower",
//        "proximity" to "mdi:apple-safari",
//        "remote" to "mdi:remote",
//        "scene" to "mdi:palette",
//        "schedule" to "mdi:calendar-clock",
//        "script" to "mdi:script-text",
//        "select" to "mdi:format-list-bulleted",
//        "sensor" to "mdi:eye",
//        "simple_alarm" to "mdi:bell",
//        "siren" to "mdi:bullhorn",
//        "stt" to "mdi:microphone-message",
//        "sun" to "mdi:white-balance-sunny",
//        "text" to "mdi:form-textbox",
//        "time" to "mdi:clock",
//        "timer" to "mdi:timer-outline",
//        "template" to "mdi:code-braces",
//        "todo" to "mdi:clipboard-list",
//        "tts" to "mdi:speaker-message",
//        "vacuum" to "mdi:robot-vacuum",
//        "wake_word" to "mdi:chat-sleep",
//        "weather" to "mdi:weather-partly-cloudy",
//        "zone" to "mdi:map-marker-radius"
//    )
// }
//
// // ============================================================================
// // Icon Service (mock - you'd implement actual API calls)
// // ============================================================================
//
// interface IconService {
//    suspend fun getPlatformIcons(platform: String): Map<String, Map<String, IconTranslations>>?
//    suspend fun getComponentIcons(domain: String): Map<String, IconTranslations>?
//    suspend fun getStateIcon(stateObj: HassEntity, state: String?): String?
// }
//
// // ============================================================================
// // Icon Retrieval Logic
// // ============================================================================
//
// class EntityIconResolver(
//    private val iconService: IconService,
//    private val entityRegistry: Map<String, EntityRegistryEntry>
// ) {
//
//    /**
//     * Get icon for an entity following the Home Assistant hierarchy
//     *
//     * Priority order:
//     * 1. Override icon parameter
//     * 2. Entity registry icon
//     * 3. Entity attributes icon
//     * 4. Platform-specific icon (based on translation_key and platform)
//     * 5. State-based icon
//     * 6. Component icon (based on device_class or domain)
//     * 7. Fallback domain icon
//     * 8. Default domain icon
//     */
//    suspend fun getEntityIcon(
//        stateObj: HassEntity,
//        overrideIcon: String? = null,
//        stateValue: String? = null
//    ): String {
//        // 1. Check override icon (highest priority)
//        if (overrideIcon != null) {
//            return overrideIcon
//        }
//
//        val entry = entityRegistry[stateObj.entityId]
//
//        // 2. Check entity registry icon
//        if (entry?.icon != null) {
//            return entry.icon
//        }
//
//        // 3. Check entity attributes icon
//        if (stateObj.icon != null) {
//            return stateObj.icon
//        }
//
//        val domain = stateObj.entityId.substringBefore('.')
//        val state = stateValue ?: stateObj.state
//
//        // 4-6. Try to get dynamic icon
//        val dynamicIcon = getEntityIconDynamic(
//            domain = domain,
//            stateObj = stateObj,
//            stateValue = state,
//            entry = entry
//        )
//
//        if (dynamicIcon != null) {
//            return dynamicIcon
//        }
//
//        // 7. Fallback to domain-specific icon
//        val fallbackIcon = IconConstants.FALLBACK_DOMAIN_ICONS[domain]
//        if (fallbackIcon != null) {
//            return fallbackIcon
//        }
//
//        // 8. Ultimate fallback
//        return IconConstants.DEFAULT_DOMAIN_ICON
//    }
//
//    /**
//     * Get dynamic icon based on platform, state, and component translations
//     */
//    private suspend fun getEntityIconDynamic(
//        domain: String,
//        stateObj: HassEntity,
//        stateValue: String?,
//        entry: EntityRegistryEntry?
//    ): String? = coroutineScope {
//        val platform = entry?.platform
//        val translationKey = entry?.translationKey
//        val deviceClass = stateObj.deviceClass
//
//        var icon: String? = null
//
//        // 4. Try platform-specific icons
//        if (translationKey != null && platform != null) {
//            val platformIconsDeferred = async {
//                iconService.getPlatformIcons(platform)
//            }
//
//            val platformIcons = platformIconsDeferred.await()
//            if (platformIcons != null) {
//                val translations = platformIcons[domain]?.get(translationKey)
//                icon = getIconFromTranslations(stateValue, translations)
//            }
//        }
//
//        // 5. Try state-based icon
//        if (icon == null) {
//            icon = iconService.getStateIcon(stateObj, stateValue)
//        }
//
//        // 6. Try component icons
//        if (icon == null) {
//            val componentIcons = iconService.getComponentIcons(domain)
//            if (componentIcons != null) {
//                val translations = if (deviceClass != null) {
//                    componentIcons[deviceClass] ?: componentIcons["_"]
//                } else {
//                    componentIcons["_"]
//                }
//                icon = getIconFromTranslations(stateValue, translations)
//            }
//        }
//
//        icon
//    }
//
//    /**
//     * Get icon from translations based on state value
//     */
//    private fun getIconFromTranslations(
//        state: String?,
//        translations: IconTranslations?
//    ): String? {
//        if (translations == null) {
//            return null
//        }
//
//        // First check for exact state match
//        if (state != null && translations.state?.containsKey(state) == true) {
//            return translations.state[state]
//        }
//
//        // Then check for range-based icons if we have a numeric state
//        if (state != null) {
//            val numericState = state.toDoubleOrNull()
//            if (numericState != null && translations.range != null) {
//                val iconFromRange = getIconFromRange(numericState, translations.range)
//                if (iconFromRange != null) {
//                    return iconFromRange
//                }
//            }
//        }
//
//        // Fallback to default icon
//        return translations.default
//    }
//
//    /**
//     * Get icon from a range of values
//     * Returns icon for the highest threshold that's <= the value
//     */
//    private fun getIconFromRange(
//        value: Double,
//        range: Map<Int, String>
//    ): String? {
//        // Sort range keys
//        val rangeValues = range.keys.sorted()
//
//        if (rangeValues.isEmpty()) {
//            return null
//        }
//
//        // If the value is below the first threshold, return null
//        if (value < rangeValues.first()) {
//            return null
//        }
//
//        // Find the highest threshold that's less than or equal to the value
//        var selectedThreshold = rangeValues.first()
//        for (threshold in rangeValues) {
//            if (value >= threshold) {
//                selectedThreshold = threshold
//            } else {
//                break
//            }
//        }
//
//        return range[selectedThreshold]
//    }
//
//    /**
//     * Get icon for an entity registry entry (without state object)
//     */
//    suspend fun getEntryIcon(entry: EntityRegistryEntry): String {
//        // Check entry icon first
//        if (entry.icon != null) {
//            return entry.icon
//        }
//
//        // Try to get state object
//        val domain = entry.entityId.substringBefore('.')
//
//        // Try dynamic resolution
//        val dynamicIcon = getEntityIconDynamic(
//            domain = domain,
//            stateObj = HassEntity(entry.entityId, "unknown"),
//            stateValue = null,
//            entry = entry
//        )
//
//        if (dynamicIcon != null) {
//            return dynamicIcon
//        }
//
//        // Fallback to domain icon
//        return IconConstants.FALLBACK_DOMAIN_ICONS[domain]
//            ?: IconConstants.DEFAULT_DOMAIN_ICON
//    }
// }
//
// // ============================================================================
// // Usage Example
// // ============================================================================
//
// suspend fun example() {
//    // Mock implementation of IconService
//    val iconService = object : IconService {
//        override suspend fun getPlatformIcons(platform: String) = null
//        override suspend fun getComponentIcons(domain: String) = null
//        override suspend fun getStateIcon(stateObj: HassEntity, state: String?) = null
//    }
//
//    val entityRegistry = mapOf(
//        "light.living_room" to EntityRegistryEntry(
//            entityId = "light.living_room",
//            icon = null, // Custom icon would override
//            platform = "hue",
//            translationKey = "light"
//        )
//    )
//
//    val resolver = EntityIconResolver(iconService, entityRegistry)
//
//    val entity = HassEntity(
//        entityId = "light.living_room",
//        state = "on",
//        attributes = mapOf(
//            "device_class" to "light",
//            "icon" to null
//        )
//    )
//
//    // Get icon - will follow the full hierarchy
//    val icon = resolver.getEntityIcon(entity)
//    println("Icon for light.living_room: $icon")
//
//    // With override
//    val overrideIcon = resolver.getEntityIcon(entity, overrideIcon = "mdi:custom-icon")
//    println("Overridden icon: $overrideIcon")
// }
