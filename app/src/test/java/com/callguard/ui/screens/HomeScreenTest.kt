package com.callguard.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.callguard.ui.theme.CallGuardTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders the real Home screen on the JVM. HomeScreen reads its state from the application graph, so this runs against
 * the real [com.callguard.CallGuardApp] that Robolectric builds from the debug manifest.
 *
 * The SDK is pinned to 35: on the app's target SDK 37, Robolectric's application frame reflects into JDK-internal
 * packages that JDK 17+ does not export, and the Compose test rule cannot start. The window is pinned to a phone-sized
 * screen so the sections below the fold can be scrolled to instead of being clipped away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun homeScreenShowsHeadingProtectionAndQuickModes() {
        compose.setContent {
            CallGuardTheme {
                HomeScreen()
            }
        }

        // Above the fold: title, the settings entry and the protection status card.
        compose.onNodeWithText("CALLGUARD").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        compose.onNodeWithText("PROTECTION").assertIsDisplayed()

        // Below the fold: today's counts, the quick mode toggles and the last identified callers.
        compose.onNodeWithText("TODAY").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("SILENCE MODE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("IGNORE MODE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("LAST IDENTIFIED").performScrollTo().assertIsDisplayed()
    }
}
