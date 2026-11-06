package com.callguard.ui.screens

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.callguard.CallGuardApp
import com.callguard.spam.classify.SmsKind
import com.callguard.ui.theme.CallGuardTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders the real GUARD screen on the JVM. HomeScreen reads its state from the application graph, so this runs against
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
    fun guardScreenShowsStatusAndEverySettingInOneScroll() {
        compose.setContent {
            CallGuardTheme {
                HomeScreen()
            }
        }

        // Above the fold: the title and the protection status card. The settings section with the same label sits
        // lower on the screen, so the status card is the first match.
        compose.onNodeWithText("CALLGUARD").assertIsDisplayed()
        compose.onAllNodesWithText("PROTECTION")[0].assertIsDisplayed()
        // The separate Settings entry is gone, so no gear icon is left in the header.
        compose.onNodeWithContentDescription("Settings").assertDoesNotExist()

        // Under the status card: today's counts and the last identified callers, before the settings.
        compose.onNodeWithText("TODAY").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("LAST IDENTIFIED").performScrollTo().assertIsDisplayed()

        // Below them, on the same scroll: the settings sections, the lists row and the debug preview.
        compose.onNodeWithText("SILENCE MODE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("IGNORE MODE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("ALLOW / BLOCK LISTS").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("PERMISSIONS").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("PREVIEW CALL").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun listsRowOpensTheListsScreen() {
        var opened = false
        compose.setContent {
            CallGuardTheme {
                HomeScreen(onOpenLists = { opened = true })
            }
        }

        compose.onNodeWithText("ALLOW / BLOCK LISTS").performScrollTo().performClick()

        assertTrue(opened)
    }

    @Test
    fun guardScreenShowsTextsThatRingWithARowPerKind() {
        compose.setContent {
            CallGuardTheme {
                HomeScreen()
            }
        }

        compose.onNodeWithText("TEXTS THAT RING").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("PROMOTIONS").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("BANKS & INVESTMENTS").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tappingAKindRowTurnsThatKindOnAndOff() {
        val store = CallGuardApp.graph(ApplicationProvider.getApplicationContext<Context>()).settingsStore
        compose.setContent {
            CallGuardTheme {
                HomeScreen()
            }
        }
        assertFalse(SmsKind.PROMOTIONAL in store.settings.value.ringKinds)

        compose.onNodeWithText("PROMOTIONS").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(SmsKind.PROMOTIONAL in store.settings.value.ringKinds)

        compose.onNodeWithText("PROMOTIONS").performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(SmsKind.PROMOTIONAL in store.settings.value.ringKinds)
    }
}
