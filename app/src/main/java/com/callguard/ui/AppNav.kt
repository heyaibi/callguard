package com.callguard.ui

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.callguard.call.Dialer
import com.callguard.ui.screens.ComposeScreen
import com.callguard.ui.screens.ContactsScreen
import com.callguard.ui.screens.HomeScreen
import com.callguard.ui.screens.IncomingCallScreen
import com.callguard.ui.screens.KeypadScreen
import com.callguard.ui.screens.ListsScreen
import com.callguard.ui.screens.MessagesScreen
import com.callguard.ui.screens.RecentsScreen
import com.callguard.ui.screens.ThreadScreen
import com.callguard.ui.theme.CgColors
import com.callguard.ui.theme.MonoFont

object Routes {
    const val KEYPAD = "keypad"

    /** The keypad route with its optional prefill argument, as the nav graph registers it. */
    const val KEYPAD_ROUTE = "keypad?number={number}"
    const val RECENTS = "recents"
    const val CONTACTS = "contacts"
    const val MESSAGES = "messages"

    /** The GUARD tab: protection status and every setting. Lists is pushed from it. */
    const val HOME = "home"
    const val LISTS = "lists"
    const val INCOMING = "incoming"
    const val THREAD = "thread/{number}"
    const val COMPOSE = "compose"
}

/** The keypad route, pre-filled with [number] when given. The number is encoded so "+" survives the query. */
fun keypadRoute(number: String?): String = if (number == null) Routes.KEYPAD else "${Routes.KEYPAD}?number=${Uri.encode(number)}"

/** The conversation route for [number]. The number is encoded so "+" and other characters survive the path. */
fun threadRoute(number: String): String = "thread/${Uri.encode(number)}"

/** The incoming-call route for [number]. Without a number the screen shows the sample caller. */
fun incomingRoute(number: String): String = "${Routes.INCOMING}?number=${Uri.encode(number)}"

/**
 * A screen to open on top of the current one, from a launch intent, a notification or a caller ID card. [id] makes
 * each request a new one, so the same number opens again when it is asked for again.
 */
data class OpenRequest(
    val route: String,
    val id: Long = System.nanoTime(),
)

/** A bottom-bar tab. [owns] says which destination routes light this tab. */
private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val owns: (String) -> Boolean,
)

private val tabs =
    listOf(
        Tab(Routes.KEYPAD, "Keypad", Icons.Filled.Dialpad) { it.startsWith(Routes.KEYPAD) },
        Tab(Routes.RECENTS, "Recents", Icons.Filled.History) { it == Routes.RECENTS },
        Tab(Routes.MESSAGES, "Messages", Icons.AutoMirrored.Filled.Message) { it == Routes.MESSAGES || it == Routes.COMPOSE },
        Tab(Routes.CONTACTS, "Contacts", Icons.Filled.Contacts) { it == Routes.CONTACTS },
        Tab(Routes.HOME, "Guard", Icons.Filled.Shield) { it == Routes.HOME || it == Routes.LISTS },
    )

private val NavLabel =
    TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.Bold,
        fontSize = 8.sp,
        letterSpacing = 0.6.sp,
    )

/**
 * Root navigation: five tabs on a black bottom bar (KEYPAD, RECENTS, MESSAGES, CONTACTS, GUARD). Lists opens
 * from GUARD. The app starts on KEYPAD when it holds the dialer role, otherwise on GUARD.
 * The incoming-call, conversation and compose routes are full-bleed and hide the bar.
 * The Scaffold passes top and bottom insets to the tab screens through its padding.
 * [request] is a screen to open now: a conversation, an incoming call or the keypad, from MainActivity.
 */
@Composable
fun AppNav(request: OpenRequest? = null) {
    val context = LocalContext.current
    val nav = rememberNavController()
    val route =
        nav
            .currentBackStackEntryAsState()
            .value
            ?.destination
            ?.route
            .orEmpty()
    val start = remember { if (Dialer.isDefaultDialer(context)) Routes.KEYPAD_ROUTE else Routes.HOME }
    // The incoming route carries an optional number argument, so match its prefix.
    val fullBleed = route.startsWith(Routes.INCOMING) || route == Routes.THREAD || route == Routes.COMPOSE

    /** Switches tabs: one copy of each tab on the stack, its state kept when it is left and restored when it returns. */
    val openTab: (String) -> Unit = { target ->
        nav.navigate(target) {
            popUpTo(nav.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Each request opens its screen once. A new keypad replaces any keypad already open, so the number always shows as
    // it was handed in.
    LaunchedEffect(request) {
        request?.let { r ->
            if (r.route.startsWith(Routes.KEYPAD)) {
                nav.navigate(r.route) { popUpTo(Routes.KEYPAD_ROUTE) { inclusive = true } }
            } else {
                nav.navigate(r.route)
            }
        }
    }

    Scaffold(
        containerColor = CgColors.Black,
        bottomBar = {
            if (!fullBleed) {
                NavigationBar(
                    containerColor = CgColors.Black,
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = tab.owns(route),
                            onClick = { openTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = {
                                Text(
                                    text = tab.label.uppercase(),
                                    style = NavLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                )
                            },
                            alwaysShowLabel = true,
                            colors =
                                NavigationBarItemDefaults.colors(
                                    selectedIconColor = CgColors.White,
                                    selectedTextColor = CgColors.White,
                                    indicatorColor = CgColors.SurfaceHigh,
                                    unselectedIconColor = CgColors.TextDim,
                                    unselectedTextColor = CgColors.TextDim,
                                ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = nav,
            startDestination = start,
            modifier = if (fullBleed) Modifier.fillMaxSize() else Modifier.padding(innerPadding),
        ) {
            composable(
                route = Routes.KEYPAD_ROUTE,
                arguments =
                    listOf(
                        navArgument("number") {
                            type = NavType.StringType
                            nullable = true
                        },
                    ),
            ) { entry ->
                KeypadScreen(
                    initialNumber = entry.arguments?.getString("number"),
                    onOpenRecents = { openTab(Routes.RECENTS) },
                    onOpenContacts = { openTab(Routes.CONTACTS) },
                )
            }
            composable(Routes.RECENTS) {
                RecentsScreen(onOpenThread = { number -> nav.navigate(threadRoute(number)) { launchSingleTop = true } })
            }
            composable(Routes.CONTACTS) {
                ContactsScreen(onOpenThread = { number -> nav.navigate(threadRoute(number)) { launchSingleTop = true } })
            }
            composable(Routes.MESSAGES) {
                MessagesScreen(
                    onOpenThread = { number -> nav.navigate(threadRoute(number)) { launchSingleTop = true } },
                    onCompose = { nav.navigate(Routes.COMPOSE) { launchSingleTop = true } },
                )
            }
            composable(Routes.COMPOSE) {
                ComposeScreen(
                    onBack = { nav.popBackStack() },
                    onOpenThread = { number ->
                        nav.navigate(threadRoute(number)) { popUpTo(Routes.COMPOSE) { inclusive = true } }
                    },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onPreviewCall = { nav.navigate(Routes.INCOMING) },
                    onOpenLists = { nav.navigate(Routes.LISTS) { launchSingleTop = true } },
                )
            }
            composable(Routes.LISTS) { ListsScreen(onBack = { nav.popBackStack() }) }
            composable(
                route = "${Routes.INCOMING}?number={number}",
                arguments =
                    listOf(
                        navArgument("number") {
                            type = NavType.StringType
                            nullable = true
                        },
                    ),
            ) { entry ->
                IncomingCallScreen(number = entry.arguments?.getString("number"), onDismiss = { nav.popBackStack() })
            }
            composable(
                route = Routes.THREAD,
                arguments = listOf(navArgument("number") { type = NavType.StringType }),
            ) { entry ->
                ThreadScreen(
                    number = entry.arguments?.getString("number").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
