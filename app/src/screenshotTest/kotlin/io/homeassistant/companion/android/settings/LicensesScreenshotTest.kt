package io.homeassistant.companion.android.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.mikepenz.aboutlibraries.Libs
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.settings.license.LicensesContent
import io.homeassistant.companion.android.util.compose.HAPreviews

class LicensesScreenshotTest {

    @HAPreviews
    @PreviewTest
    @Composable
    fun Licences() {
        HAThemeForPreview {
            val libs = Libs.Builder()
                .withJson(
                    """
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
        "Apache-2.0"
      ],
      "funding": []
    }
  ],
  "licenses": {
    "Apache-2.0": {
      "name": "Apache License 2.0",
      "url": "https://spdx.org/licenses/Apache-2.0.html",
      "content": "CLEARED FOR TEST",
      "internalHash": "Apache-2.0",
      "spdxId": "Apache-2.0",
      "hash": "Apache-2.0"
    }
  }
}               
                    """.trimIndent(),
                )
                .build()

            assert(libs.libraries.isNotEmpty()) { "Fail to parse fake JSON" }

            LicensesContent(
                libs,
            )
        }
    }
}
