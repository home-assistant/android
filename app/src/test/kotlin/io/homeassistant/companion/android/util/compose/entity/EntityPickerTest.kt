package io.homeassistant.companion.android.util.compose.entity

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit4Rule
import io.homeassistant.companion.android.testing.unit.stringResource
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class EntityPickerTest {

    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val mainDispatcherRule = MainDispatcherJUnit4Rule()

    @get:Rule(order = 3)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>(mainDispatcherRule.testDispatcher)

    /**
     * Advances virtual time past the debounce delay and waits for compose to process changes.
     *
     * The SearchField uses a 300ms debounce delay. This helper advances time by 500ms
     * on both the test dispatcher scheduler and Compose's main clock, then waits for
     * compose to be idle, ensuring all filtering work completes.
     */
    private fun advanceTimeAndWaitForIdle() {
        // Advance the test dispatcher scheduler to run pending coroutines
        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(500)
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        // Advance compose frame time
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()
    }

    /**
     * Waits for initial entity list to load.
     *
     * The entity loading involves multiple async steps:
     * 1. Entity mapping to searchable fields (LaunchedEffect on entities)
     * 2. Initial filtering with empty query (LaunchedEffect on entitiesWithFields)
     * 3. Compose recomposition with filtered results
     *
     * This uses waitUntil to wait for a specific entity to appear, handling the
     * async nature of the loading process reliably.
     *
     * @param entityName The name of an entity to wait for (default: "Bedroom Light")
     */
    private fun waitForInitialEntityLoad(entityName: String = "Bedroom Light") {
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            // Advance time on each check to allow coroutines to progress
            mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(100)
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()
            composeTestRule.mainClock.advanceTimeBy(100)

            try {
                composeTestRule.onNodeWithText(entityName).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        composeTestRule.waitForIdle()
    }

    /**
     * Sets up test content with a forced tablet size to use the inline dropdown instead of a bottom sheet.
     *
     * The EntityPicker uses a bottom sheet on compact screens (< 600dp width), which creates a separate
     * composition root and causes issues with Compose testing (multiple roots, synchronization problems).
     * By forcing a tablet size, we use the inline dropdown which stays in the same composition tree.
     */
    private fun setExpandedEntityPickerContent(
        entities: List<EntityPickerItem> = createTestEntities(),
        selectedEntityId: String? = null,
        onEntitySelectedId: (String) -> Unit = {},
        onEntityCleared: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            TabletSizeContent {
                HAThemeForPreview {
                    EntityPicker(
                        entities = entities,
                        selectedEntityId = selectedEntityId,
                        onEntitySelectedId = onEntitySelectedId,
                        onEntityCleared = onEntityCleared,
                        isExpanded = true,
                        dispatcher = mainDispatcherRule.testDispatcher,
                    )
                }
            }
        }
    }

    private fun createTestEntities() = listOf(
        EntityPickerItem(
            entityId = "light.living_room",
            domain = "light",
            friendlyName = "Living Room Light",
            icon = CommunityMaterial.Icon2.cmd_lightbulb,
            areaName = "Living Room",
            deviceName = "Smart Bulb",
        ),
        EntityPickerItem(
            entityId = "light.bedroom",
            domain = "light",
            friendlyName = "Bedroom Light",
            icon = CommunityMaterial.Icon2.cmd_lightbulb,
            areaName = "Bedroom",
        ),
        EntityPickerItem(
            entityId = "sensor.temperature",
            domain = "sensor",
            friendlyName = "Temperature Sensor",
            areaName = "Living Room",
            icon = CommunityMaterial.Icon3.cmd_temperature_celsius,
        ),
        EntityPickerItem(
            entityId = "switch.fan",
            domain = "switch",
            friendlyName = "Ceiling Fan",
            icon = CommunityMaterial.Icon2.cmd_fan,
            areaName = "Bedroom",
            deviceName = "Smart Switch",
        ),
    )

    @Test
    fun `Given no selection when rendered then shows add entity button`() {
        composeTestRule.setContent {
            HAThemeForPreview {
                EntityPicker(
                    entities = createTestEntities(),
                    selectedEntityId = null,
                    onEntitySelectedId = {},
                    onEntityCleared = {},
                )
            }
        }

        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.entity_picker_add_entity))
            .assertIsDisplayed()
    }

    @Test
    fun `Given selected entity when rendered then shows entity name and clear button`() {
        composeTestRule.setContent {
            HAThemeForPreview {
                EntityPicker(
                    entities = createTestEntities(),
                    selectedEntityId = "light.living_room",
                    onEntitySelectedId = {},
                    onEntityCleared = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Living Room Light").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(composeTestRule.stringResource(commonR.string.search_clear_selection))
            .assertIsDisplayed()
    }

    @Test
    fun `Given selected entity with area and device when rendered then shows subtitle`() {
        composeTestRule.setContent {
            HAThemeForPreview {
                EntityPicker(
                    entities = createTestEntities(),
                    selectedEntityId = "light.living_room",
                    onEntitySelectedId = {},
                    onEntityCleared = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Living Room Light").assertIsDisplayed()
        composeTestRule.onNodeWithText("Living Room ▸ Smart Bulb").assertIsDisplayed()
    }

    @Test
    fun `Given selected entity without device when rendered then shows only area`() {
        composeTestRule.setContent {
            HAThemeForPreview {
                EntityPicker(
                    entities = createTestEntities(),
                    selectedEntityId = "light.bedroom",
                    onEntitySelectedId = {},
                    onEntityCleared = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Bedroom Light").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bedroom").assertIsDisplayed()
    }

    @Test
    fun `Given expanded picker when rendered then shows search field and entities`() {
        setExpandedEntityPickerContent()

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.entity_picker_add_entity))
            .assertIsDisplayed()
            .performClick()

        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.search)).assertIsDisplayed()

        // Wait for initial load (entity mapping + filtering)
        waitForInitialEntityLoad()

        // Entities are sorted alphabetically, so check in order: Bedroom, Ceiling, Living Room, Temperature
        composeTestRule.onNodeWithText("Bedroom Light").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ENTITY_LIST_TEST_TAG).performScrollToNode(hasText("Ceiling Fan"))
        composeTestRule.onNodeWithText("Ceiling Fan").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ENTITY_LIST_TEST_TAG).performScrollToNode(hasText("Living Room Light"))
        composeTestRule.onNodeWithText("Living Room Light").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ENTITY_LIST_TEST_TAG).performScrollToNode(hasText("Temperature Sensor"))
        composeTestRule.onNodeWithText("Temperature Sensor").assertIsDisplayed()
    }

    @Test
    fun `Given expanded picker with empty list when rendered then shows no entities message`() {
        setExpandedEntityPickerContent(entities = emptyList())

        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.entity_picker_no_entity_found))
            .assertIsDisplayed()
    }

    @Test
    fun `Given selected entity when clear button clicked then callback invoked`() {
        val onEntityCleared: () -> Unit = mockk(relaxed = true)

        composeTestRule.setContent {
            HAThemeForPreview {
                EntityPicker(
                    entities = createTestEntities(),
                    selectedEntityId = "light.living_room",
                    onEntitySelectedId = {},
                    onEntityCleared = onEntityCleared,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(composeTestRule.stringResource(commonR.string.search_clear_selection))
            .performClick()

        verify(exactly = 1) { onEntityCleared() }
    }

    @Test
    fun `Given expanded picker when entity selected then callback invoked with entity id`() {
        val onEntitySelectedId: (String) -> Unit = mockk(relaxed = true)

        setExpandedEntityPickerContent(onEntitySelectedId = onEntitySelectedId)

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.entity_picker_add_entity))
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Bedroom Light").performClick()

        composeTestRule.waitForIdle()

        verify(exactly = 1) { onEntitySelectedId("light.bedroom") }
    }

    @Test
    fun `Given expanded picker with entities when search text entered then filters entities`() {
        setExpandedEntityPickerContent()

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.entity_picker_add_entity))
            .assertIsDisplayed()
            .performClick()

        // Wait for initial entities to load
        waitForInitialEntityLoad()

        // Enter search text
        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.search))
            .assertIsDisplayed()
            .performTextInput("bedroom")

        composeTestRule.onNodeWithText("bedroom").assertIsDisplayed()

        advanceTimeAndWaitForIdle()

        // Verify filtered results (sorted alphabetically: Bedroom Light, Ceiling Fan)
        composeTestRule.onNodeWithText("Bedroom Light").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ENTITY_LIST_TEST_TAG).performScrollToNode(hasText("Ceiling Fan"))
        composeTestRule.onNodeWithText("Ceiling Fan").assertIsDisplayed() // Has "Bedroom" in area
        composeTestRule.onNode(hasText("Living Room Light")).assertDoesNotExist()
        composeTestRule.onNode(hasText("Temperature Sensor")).assertDoesNotExist()
    }

    @Test
    fun `Given filtered entities when search text cleared then shows all entities`() {
        setExpandedEntityPickerContent()

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.entity_picker_add_entity))
            .assertIsDisplayed()
            .performClick()

        // Enter search text
        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.search))
            .assertIsDisplayed()
            .performTextInput("bedroom")

        composeTestRule.onNodeWithText("bedroom").assertIsDisplayed()

        advanceTimeAndWaitForIdle()

        // Verify filtered
        composeTestRule.onNode(hasText("Living Room Light")).assertDoesNotExist()

        // Clear search
        composeTestRule.onNodeWithText("bedroom")
            .performTextClearance()

        advanceTimeAndWaitForIdle()

        // Verify all entities shown
        composeTestRule.onNodeWithTag(ENTITY_LIST_TEST_TAG).performScrollToNode(hasText("Living Room Light"))
        composeTestRule.onNodeWithText("Living Room Light").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ENTITY_LIST_TEST_TAG).performScrollToNode(hasText("Bedroom Light"))
        composeTestRule.onNodeWithText("Bedroom Light").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ENTITY_LIST_TEST_TAG).performScrollToNode(hasText("Temperature Sensor"))
        composeTestRule.onNodeWithText("Temperature Sensor").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ENTITY_LIST_TEST_TAG).performScrollToNode(hasText("Ceiling Fan"))
        composeTestRule.onNodeWithText("Ceiling Fan").assertIsDisplayed()
    }

    @Test
    fun `Given expanded picker when searching with no matches then shows no results message`() {
        setExpandedEntityPickerContent()

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.entity_picker_add_entity))
            .assertIsDisplayed()
            .performClick()

        // Enter search text with no matches
        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.search))
            .assertIsDisplayed()
            .performTextInput("nonexistent")

        composeTestRule.onNodeWithText("nonexistent").assertIsDisplayed()

        advanceTimeAndWaitForIdle()

        // Verify no results message
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(commonR.string.entity_picker_no_entity_found_for, "nonexistent"),
        ).assertIsDisplayed()
    }

    @Test
    fun `Given expanded picker with entities when searching by domain then filters by domain`() {
        setExpandedEntityPickerContent()

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.entity_picker_add_entity))
            .assertIsDisplayed()
            .performClick()

        // Wait for initial entities to load
        waitForInitialEntityLoad()

        // Search for "light" domain
        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.search))
            .assertIsDisplayed()
            .performTextInput("light")

        composeTestRule.onNodeWithText("light").assertIsDisplayed()

        advanceTimeAndWaitForIdle()

        composeTestRule.onNodeWithText("Bedroom Light").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ENTITY_LIST_TEST_TAG).performScrollToNode(hasText("Living Room Light"))
        composeTestRule.onNodeWithText("Living Room Light").assertIsDisplayed()
        composeTestRule.onNode(hasText("Temperature Sensor")).assertDoesNotExist()
        composeTestRule.onNode(hasText("Ceiling Fan")).assertDoesNotExist()
    }

    @Test
    fun `Given expanded picker with entities when searching with multiple terms then filters by all terms`() {
        setExpandedEntityPickerContent()

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.entity_picker_add_entity))
            .assertIsDisplayed()
            .performClick()

        // Search with multiple terms
        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.search))
            .assertIsDisplayed()
            .performTextInput("living light")

        composeTestRule.onNodeWithText("living light").assertIsDisplayed()

        advanceTimeAndWaitForIdle()

        // Verify only matching entity shown
        composeTestRule.onNodeWithText("Living Room Light").assertIsDisplayed()
        composeTestRule.onNode(hasText("Bedroom Light")).assertDoesNotExist()
    }

    @Test
    fun `Given expanded picker with search text when clear button clicked then clears search text`() {
        setExpandedEntityPickerContent()

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.entity_picker_add_entity))
            .assertIsDisplayed()
            .performClick()

        // Wait for initial entities to load
        waitForInitialEntityLoad()

        // Enter search text
        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.search))
            .assertIsDisplayed()
            .performTextInput("bedroom")

        composeTestRule.onNodeWithText("bedroom").assertIsDisplayed()

        advanceTimeAndWaitForIdle()

        // Click clear search button
        composeTestRule.onNodeWithContentDescription(composeTestRule.stringResource(commonR.string.clear_search))
            .performClick()

        advanceTimeAndWaitForIdle()

        // Verify all entities shown again (sorted alphabetically: Bedroom, Ceiling, Living Room, Temperature)
        composeTestRule.onNodeWithText("Bedroom Light").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ENTITY_LIST_TEST_TAG).performScrollToNode(hasText("Living Room Light"))
        composeTestRule.onNodeWithText("Living Room Light").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ENTITY_LIST_TEST_TAG).performScrollToNode(hasText("Temperature Sensor"))
        composeTestRule.onNodeWithText("Temperature Sensor").assertIsDisplayed()
    }

    @Test
    fun `Given expanded picker with entities when searching with different case then matches case insensitively`() {
        setExpandedEntityPickerContent()

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.entity_picker_add_entity))
            .assertIsDisplayed()
            .performClick()

        // Wait for initial entities to load
        waitForInitialEntityLoad()

        // Search with different case
        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.search))
            .assertIsDisplayed()
            .performTextInput("BEDROOM")

        composeTestRule.onNodeWithText("BEDROOM").assertIsDisplayed()

        advanceTimeAndWaitForIdle()

        // Verify case-insensitive match (sorted alphabetically: Bedroom Light, Ceiling Fan)
        composeTestRule.onNodeWithText("Bedroom Light").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ENTITY_LIST_TEST_TAG).performScrollToNode(hasText("Ceiling Fan"))
        composeTestRule.onNodeWithText("Ceiling Fan").assertIsDisplayed() // Area: Bedroom
    }
}

/**
 * Forces tablet size (1280x800dp) to avoid bottom sheet usage in EntityPicker.
 *
 * On compact screens (< 600dp width), EntityPicker shows a bottom sheet which creates
 * a separate composition root. This causes issues with Compose testing including:
 * - Multiple root nodes making assertions difficult
 * - Synchronization issues between the test dispatcher and compose recomposition
 *
 * Using a tablet size forces the inline dropdown variant which stays in the same
 * composition tree and works reliably with Compose testing.
 */
@Composable
private fun TabletSizeContent(content: @Composable () -> Unit) {
    DeviceConfigurationOverride(
        DeviceConfigurationOverride.ForcedSize(DpSize(1280.dp, 800.dp)),
    ) {
        content()
    }
}
