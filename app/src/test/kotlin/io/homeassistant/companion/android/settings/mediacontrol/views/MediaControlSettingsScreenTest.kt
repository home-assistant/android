package io.homeassistant.companion.android.settings.mediacontrol.views

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Speaker
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
import io.homeassistant.companion.android.settings.mediacontrol.MediaControlSelectedEntity
import io.homeassistant.companion.android.settings.mediacontrol.MediaControlSettingsUiState
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit4Rule
import io.homeassistant.companion.android.testing.unit.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class MediaControlSettingsScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val mainDispatcherRule = MainDispatcherJUnit4Rule()

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given loading state when displayed then no entity configuration is offered`() {
        composeTestRule.apply {
            testScreen(MediaControlSettingsUiState(isLoading = true)) {
                onNodeWithText(stringResource(commonR.string.media_control_description)).assertIsDisplayed()
                onNodeWithText(stringResource(commonR.string.media_control_select_entity)).assertDoesNotExist()
                onNodeWithText(LIVING_ROOM_NAME).assertDoesNotExist()
            }
        }
    }

    @Test
    fun `Given a single server when displayed then the server selector is hidden`() {
        composeTestRule.apply {
            testScreen(singleServerState) {
                onNodeWithText(stringResource(commonR.string.server_select)).assertDoesNotExist()
            }
        }
    }

    @Test
    fun `Given several servers when displayed then the server selector is shown`() {
        composeTestRule.apply {
            testScreen(multipleServersState) {
                onNodeWithText(stringResource(commonR.string.server_select), substring = true).assertIsDisplayed()
            }
        }
    }

    @Test
    fun `Given a resolved entity when displayed then its name and subtitle are shown`() {
        composeTestRule.apply {
            testScreen(singleServerState) {
                onNodeWithText(LIVING_ROOM_NAME).assertIsDisplayed()
                onNodeWithText("Living Room ▸ Sonos One").assertIsDisplayed()
            }
        }
    }

    @Test
    fun `Given an entity without area or device when displayed then it has no subtitle`() {
        composeTestRule.apply {
            testScreen(singleServerState) {
                onNodeWithText(KITCHEN_NAME).assertIsDisplayed()
                // The only rows with a subtitle are the ones the display data gives one
                onNodeWithText(HOME_SERVER_NAME).assertDoesNotExist()
            }
        }
    }

    @Test
    fun `Given an unresolved entity when displayed then the raw entity id is shown`() {
        composeTestRule.apply {
            testScreen(multipleServersState) {
                onNodeWithText(OFFICE_ENTITY_ID).assertIsDisplayed()
            }
        }
    }

    @Test
    fun `Given several servers when displayed then each row leads with its server name`() {
        composeTestRule.apply {
            testScreen(multipleServersState) {
                onNodeWithText("$HOME_SERVER_NAME ▸ Living Room ▸ Sonos One").assertIsDisplayed()
                // The server name is the whole subtitle when the entity contributes none
                onNodeWithText(OFFICE_SERVER_NAME).assertIsDisplayed()
            }
        }
    }

    @Test
    fun `Given a configured entity when remove is clicked then onRemoveEntity is invoked with it`() {
        composeTestRule.apply {
            testScreen(singleServerState) {
                onAllNodesWithContentDescription(stringResource(commonR.string.media_control_remove_entity))[0]
                    .performClick()

                assertEquals(livingRoomConfig, removedEntity?.config)
            }
        }
    }

    @Test
    fun `Given several servers when another one is selected then onServerSelected is invoked with its id`() {
        composeTestRule.apply {
            testScreen(multipleServersState) {
                onNodeWithText(stringResource(commonR.string.server_select), substring = true).performClick()
                waitForIdle()
                // The office row also carries that name as its subtitle, only the menu entry is clickable
                onNode(hasText(OFFICE_SERVER_NAME) and hasClickAction()).performClick()

                assertEquals(OTHER_SERVER_ID, selectedServerId)
            }
        }
    }

    @Test
    fun `Given a configured entity when the picker is expanded then it is not offered again`() {
        composeTestRule.apply {
            testScreen(singleServerState) {
                onNodeWithText(stringResource(commonR.string.media_control_select_entity)).performClick()
                waitForIdle()

                // The picker offers the unconfigured player, while the configured ones stay a single
                // occurrence each: their row behind the sheet, never also a choice inside it
                onNodeWithText(BEDROOM_NAME).assertIsDisplayed()
                onAllNodesWithText(LIVING_ROOM_NAME).assertCountEquals(1)
                onAllNodesWithText(KITCHEN_NAME).assertCountEquals(1)
            }
        }
    }

    @Test
    fun `Given the picker when an entity is selected then onEntitySelected is invoked with its id`() {
        composeTestRule.apply {
            testScreen(singleServerState) {
                onNodeWithText(stringResource(commonR.string.media_control_select_entity)).performClick()
                waitForIdle()
                onNodeWithText(BEDROOM_NAME).performClick()
                waitForIdle()

                assertEquals(BEDROOM_ENTITY_ID, selectedEntityId)
            }
        }
    }

    private class TestHelper {
        var selectedServerId: Int? = null
        var selectedEntityId: String? = null
        var removedEntity: MediaControlSelectedEntity? = null
    }

    private fun AndroidComposeTestRule<*, *>.testScreen(
        uiState: MediaControlSettingsUiState,
        dsl: TestHelper.() -> Unit,
    ) {
        TestHelper().apply {
            setContent {
                HATheme {
                    MediaControlSettingsContent(
                        uiState = uiState,
                        onServerSelected = { selectedServerId = it },
                        onEntitySelected = { selectedEntityId = it },
                        onRemoveEntity = { removedEntity = it },
                    )
                }
            }
            dsl()
        }
    }

    private companion object {
        const val SERVER_ID = 1
        const val OTHER_SERVER_ID = 2

        const val HOME_SERVER_NAME = "Home"
        const val OFFICE_SERVER_NAME = "Office"

        const val LIVING_ROOM_NAME = "Living Room TV"
        const val KITCHEN_NAME = "Kitchen Radio"
        const val BEDROOM_NAME = "Bedroom Speaker"

        const val BEDROOM_ENTITY_ID = "media_player.bedroom"
        const val OFFICE_ENTITY_ID = "media_player.office"

        val livingRoomConfig = MediaControlEntityConfig(serverId = SERVER_ID, entityId = "media_player.living_room")
        val kitchenConfig = MediaControlEntityConfig(serverId = SERVER_ID, entityId = "media_player.kitchen")
        val officeConfig = MediaControlEntityConfig(serverId = OTHER_SERVER_ID, entityId = OFFICE_ENTITY_ID)

        /** Resolved with an area and a device, so it contributes the longest subtitle. */
        val livingRoomItem = EntityDisplayWithContext(
            item = EntityDisplayWithoutContext(
                entityId = livingRoomConfig.entityId,
                name = LIVING_ROOM_NAME,
                icon = Mdi.Speaker,
            ),
            areaName = "Living Room",
            deviceName = "Sonos One",
        )

        /** Resolved without an area or a device, so it contributes no subtitle. */
        val kitchenItem = EntityDisplayWithContext(
            item = EntityDisplayWithoutContext(
                entityId = kitchenConfig.entityId,
                name = KITCHEN_NAME,
                icon = Mdi.Speaker,
            ),
        )

        /** Resolved but not configured, so the picker offers it. */
        val bedroomItem = EntityDisplayWithContext(
            item = EntityDisplayWithoutContext(
                entityId = BEDROOM_ENTITY_ID,
                name = BEDROOM_NAME,
                icon = Mdi.Speaker,
            ),
        )

        val displayState = EntityDisplayState.Loaded(listOf(livingRoomItem, kitchenItem, bedroomItem))

        val singleServerState = MediaControlSettingsUiState(
            isLoading = false,
            selectedServerId = SERVER_ID,
            serversDropdownItems = listOf(HADropdownItem(key = SERVER_ID, label = HOME_SERVER_NAME)),
            mediaControlEntityConfigs = listOf(livingRoomConfig, kitchenConfig),
            entityDisplayStatePerServer = mapOf(SERVER_ID to displayState),
        )

        /** Adds a second server, and an entity of it that is never resolved. */
        val multipleServersState = singleServerState.copy(
            serversDropdownItems = listOf(
                HADropdownItem(key = SERVER_ID, label = HOME_SERVER_NAME),
                HADropdownItem(key = OTHER_SERVER_ID, label = OFFICE_SERVER_NAME),
            ),
            mediaControlEntityConfigs = listOf(livingRoomConfig, kitchenConfig, officeConfig),
        )
    }
}
