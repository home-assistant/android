package io.homeassistant.companion.android.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.tools.screenshot.PreviewTest
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.settings.license.LicensesContent
import io.homeassistant.companion.android.util.compose.HAPreviews

private const val FAKE_LIBS = """
{
  "libraries": [
    {
      "uniqueId": "androidx.activity:activity",
      "artifactVersion": "1.13.0",
      "name": "Activity",
      "description": "Provides the base Activity subclass and the relevant hooks to build a composable structure on top.",
      "website": "https://developer.android.com/jetpack/androidx/releases/activity#1.13.0",
      "developers": [
        {
          "name": "The Android Open Source Project"
        }
      ],
      "organization": {
        "name": "The Android Open Source Project"
      },
      "scm": {
        "connection": "scm:git:https://android.googlesource.com/platform/frameworks/support",
        "url": "https://cs.android.com/androidx/platform/frameworks/support"
      },
      "licenses": [
        "Apache-2.0"
      ],
      "funding": []
    },
    {
      "uniqueId": "io.homeassistant",
      "artifactVersion": "2026.1.1",
      "name": "HomeAssistant",
      "description": "Home Assistant core",
      "website": "https://home-assistant.io",
      "developers": [
        {
          "name": "Us"
        }
      ],
      "organization": {
        "name": "Open Home Foundation"
      },
      "scm": {
        "connection": "scm:git:https://github.com/home-assistant/android",
        "url": "https://github.com/home-assistant/android"
      },
      "licenses": [
        "Home-Assistant"
      ],
      "funding": []
    }
  ],
  "licenses": {
    "Apache-2.0": {
      "name": "Apache License 2.0",
      "url": "https://spdx.org/licenses/Apache-2.0.html",
      "content": "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.",
      "internalHash": "Apache-2.0",
      "spdxId": "Apache-2.0",
      "hash": "Apache-2.0"
    },
    "Home-Assistant": {
      "name": "Home Assistant",
      "url": "https://home-assistant.io",
      "content": "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.",
      "internalHash": "HA",
      "spdxId": "FAKE",
      "hash": "FAKE"
    }
  }
}
"""

class LicensesScreenshotTest {

    @HAPreviews
    @PreviewTest
    @Composable
    fun Licences() {
        FailFast.setHandler { throwable, string -> }
        HAThemeForPreview {
            val libs = Libs.Builder()
                .withJson(FAKE_LIBS)
                .build()

            // assert(libs.libraries.isNotEmpty()) { "Failed to parse fake JSON" }

            var openDialog by remember { mutableStateOf<Library?>(null) }
            var openSheet by remember { mutableStateOf<Library?>(null) }
            LicensesContent(
                libraries = libs,
                dialogLibrary = openDialog,
                sheetLibrary = openSheet,
                onDialogLibraryChange = { openDialog = it },
                onSheetLibraryChange = { openSheet = it },
            )
        }
    }

    @HAPreviews
    @PreviewTest
    @Composable
    fun `Licences sheet details`() {
        FailFast.setHandler { throwable, string -> }
        HAThemeForPreview {
            val libs = Libs.Builder()
                .withJson(FAKE_LIBS)
                .build()

            // assert(libs.libraries.size == 2) { "Failed to parse fake JSON" }

            var openDialog by remember { mutableStateOf<Library?>(null) }
            var openSheet by remember { mutableStateOf<Library?>(libs.libraries[1]) }
            LicensesContent(
                libraries = libs,
                dialogLibrary = openDialog,
                sheetLibrary = openSheet,
                onDialogLibraryChange = { openDialog = it },
                onSheetLibraryChange = { openSheet = it },
            )
        }
    }
}
