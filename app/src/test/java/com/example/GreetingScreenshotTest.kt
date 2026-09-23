package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.MovieRecord
import com.example.ui.MovieCardItem
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        MovieCardItem(
          movie = MovieRecord(
            id = 1,
            name = "Dacoit A Love Story (2026)",
            sublink = "/dacoit-a-love-story-2026-tamil-movie/",
            category = "tamil-2026-movies",
            link = "https://moviesda30.com/dacoit-a-love-story-2026-tamil-movie/",
            pageUrl = "https://moviesda30.com/tamil-2026-movies/"
          ),
          isChecked = false,
          onCheckToggle = {},
          onDelete = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
