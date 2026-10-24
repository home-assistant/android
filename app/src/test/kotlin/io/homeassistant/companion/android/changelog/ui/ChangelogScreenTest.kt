package io.homeassistant.companion.android.changelog.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.changelog.ChangelogAction
import io.homeassistant.companion.android.changelog.ChangelogCategory
import io.homeassistant.companion.android.changelog.ChangelogEntry
import io.homeassistant.companion.android.changelog.ChangelogPlatform
import io.homeassistant.companion.android.changelog.ChangelogSection
import io.homeassistant.companion.android.changelog.ChangelogUiState
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.testing.unit.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val VERSION_NAME = "2026.7.6"
private const val RELEASE_URL = "https://github.com/home-assistant/android/releases/tag/2026.7.6"
private const val ACTION_URL = "https://www.home-assistant.io/blog/"

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
internal class ChangelogScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    private var closeClicked = false
    private val clickedActions = mutableListOf<ChangelogAction>()

    private fun uiState(
        action: ChangelogAction? = null,
        currentPlatform: ChangelogPlatform = ChangelogPlatform.APP,
        platforms: Set<ChangelogPlatform> = setOf(ChangelogPlatform.APP, ChangelogPlatform.WEAR),
        actionRequiresPlatform: Boolean = false,
    ) = ChangelogUiState(
        versionName = VERSION_NAME,
        releaseUrl = RELEASE_URL,
        currentPlatform = currentPlatform,
        sections = listOf(
            ChangelogSection(
                category = ChangelogCategory.NEW,
                entries = listOf(
                    ChangelogEntry(
                        contentRes = commonR.string.changelog_entry_bug_fixes,
                        platforms = platforms,
                        action = action,
                        actionRequiresPlatform = actionRequiresPlatform,
                    ),
                ),
            ),
        ),
    )

    private fun setContent(
        action: ChangelogAction? = null,
        currentPlatform: ChangelogPlatform = ChangelogPlatform.APP,
        platforms: Set<ChangelogPlatform> = setOf(ChangelogPlatform.APP, ChangelogPlatform.WEAR),
        actionRequiresPlatform: Boolean = false,
    ) {
        composeTestRule.setContent {
            ChangelogScreenContent(
                uiState = uiState(action, currentPlatform, platforms, actionRequiresPlatform),
                onCloseClick = { closeClicked = true },
                onActionClick = { clickedActions += it },
            )
        }
    }

    private fun tapEntry() {
        composeTestRule.apply {
            onNodeWithText(stringResource(commonR.string.changelog_entry_bug_fixes)).performClick()
        }
    }

    @Test
    fun `Given displayed changelog then version content platform tags and release link are displayed`() {
        setContent()

        composeTestRule.apply {
            onNodeWithText(VERSION_NAME).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.changelog_entry_bug_fixes)).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.changelog_category_new)).assertIsDisplayed()
            onNodeWithText(
                activity.getString(
                    commonR.string.changelog_platform_this_device,
                    stringResource(commonR.string.changelog_platform_app),
                ),
            ).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.changelog_platform_wear)).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.changelog_show_full_changelog)).assertIsDisplayed()
        }
    }

    @Test
    fun `Given entry with action when tapping it then it invokes onActionClick with the action`() {
        val action = ChangelogAction.OpenUrl(ACTION_URL)
        setContent(action = action)

        tapEntry()

        assertEquals(listOf<ChangelogAction>(action), clickedActions)
    }

    @Test
    fun `Given entry without action when tapping it then nothing happens`() {
        setContent()

        tapEntry()

        assertTrue(clickedActions.isEmpty())
    }

    @Test
    fun `Given platform-restricted action when current platform is unsupported then tapping does nothing`() {
        setContent(
            action = ChangelogAction.OpenUrl(ACTION_URL),
            currentPlatform = ChangelogPlatform.AUTOMOTIVE,
            platforms = setOf(ChangelogPlatform.APP),
            actionRequiresPlatform = true,
        )

        tapEntry()

        assertTrue(clickedActions.isEmpty())
    }

    @Test
    fun `Given platform-restricted action when current platform is supported then tapping invokes it`() {
        val action = ChangelogAction.OpenUrl(ACTION_URL)
        setContent(
            action = action,
            currentPlatform = ChangelogPlatform.APP,
            platforms = setOf(ChangelogPlatform.APP),
            actionRequiresPlatform = true,
        )

        tapEntry()

        assertEquals(listOf<ChangelogAction>(action), clickedActions)
    }

    @Test
    fun `Given unrestricted action when current platform is unsupported then tapping still invokes it`() {
        val action = ChangelogAction.OpenUrl(ACTION_URL)
        setContent(
            action = action,
            currentPlatform = ChangelogPlatform.AUTOMOTIVE,
            platforms = setOf(ChangelogPlatform.APP),
            actionRequiresPlatform = false,
        )

        tapEntry()

        assertEquals(listOf<ChangelogAction>(action), clickedActions)
    }

    @Test
    fun `Given displayed changelog when tapping got it then it invokes onCloseClick`() {
        setContent()

        composeTestRule.apply {
            onNodeWithText(stringResource(commonR.string.changelog_got_it)).performClick()
        }

        assertTrue(closeClicked)
    }

    @Test
    fun `Given displayed changelog when tapping close then it invokes onCloseClick`() {
        setContent()

        composeTestRule.apply {
            onNodeWithContentDescription(stringResource(commonR.string.close)).performClick()
        }

        assertTrue(closeClicked)
    }
}
