package io.homeassistant.companion.android.widgets.entity

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.testing.unit.stringResource
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class EntityWidgetConfigureScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given custom attributes when add is clicked then custom attributes are selected`() {
        var viewState by mutableStateOf(
            EntityWidgetConfigureViewState(
                selectedEntityId = ENTITY.entityId,
                appendAttributes = true,
            ),
        )

        composeTestRule.apply {
            setContent {
                HATheme {
                    EntityWidgetConfigureView(
                        servers = emptyList(),
                        viewState = viewState,
                        onServerSelected = {},
                        entities = listOf(ENTITY),
                        onEntitySelected = {},
                        availableAttributes = listOf("brightness"),
                        onAppendAttributesChanged = { viewState = viewState.copy(appendAttributes = it) },
                        onAttributeAdded = {
                            viewState = viewState.copy(selectedAttributeIds = viewState.selectedAttributeIds + it)
                        },
                        onAttributeRemoved = {},
                        onCustomAttributeChanged = { viewState = viewState.copy(customAttribute = it) },
                        onCustomAttributesAdded = {
                            val attributes = viewState.customAttribute.split(',').map(String::trim)
                            viewState = viewState.copy(
                                selectedAttributeIds = viewState.selectedAttributeIds + attributes,
                                customAttribute = "",
                            )
                        },
                        onLabelChanged = {},
                        onTextSizeChanged = {},
                        onStateSeparatorChanged = {},
                        onAttributeSeparatorChanged = {},
                        isToggleable = true,
                        onTapActionSelected = {},
                        onBackgroundTypeSelected = {},
                        dynamicColorAvailable = true,
                        onTextColorSelected = {},
                        onErrorShown = {},
                        onActionClick = {},
                    )
                }
            }

            onNodeWithTag(ENTITY_WIDGET_CUSTOM_ATTRIBUTE_TAG)
                .performScrollTo()
                .performTextInput("power, current")
            onAllNodesWithContentDescription(stringResource(commonR.string.widget_attribute_add))[0].performClick()

            assertEquals(listOf("power", "current"), viewState.selectedAttributeIds)
        }
    }

    @Test
    fun `Given no entity selected then action button is disabled`() {
        composeTestRule.apply {
            setContent {
                HATheme {
                    EntityWidgetConfigureView(
                        servers = emptyList(),
                        viewState = EntityWidgetConfigureViewState(selectedEntityId = null),
                        onServerSelected = {},
                        entities = listOf(ENTITY),
                        onEntitySelected = {},
                        availableAttributes = emptyList(),
                        onAppendAttributesChanged = {},
                        onAttributeAdded = {},
                        onAttributeRemoved = {},
                        onCustomAttributeChanged = {},
                        onCustomAttributesAdded = {},
                        onLabelChanged = {},
                        onTextSizeChanged = {},
                        onStateSeparatorChanged = {},
                        onAttributeSeparatorChanged = {},
                        isToggleable = false,
                        onTapActionSelected = {},
                        onBackgroundTypeSelected = {},
                        dynamicColorAvailable = false,
                        onTextColorSelected = {},
                        onErrorShown = {},
                        onActionClick = {},
                    )
                }
            }

            onNodeWithTag(ENTITY_WIDGET_ACTION_BUTTON_TAG).performScrollTo().assertIsNotEnabled()
        }
    }

    private companion object {
        val ENTITY = Entity(
            entityId = "light.office",
            state = "on",
            attributes = mapOf("friendly_name" to "Office light"),
            lastChanged = LocalDateTime.MIN,
            lastUpdated = LocalDateTime.MIN,
        )
    }
}
