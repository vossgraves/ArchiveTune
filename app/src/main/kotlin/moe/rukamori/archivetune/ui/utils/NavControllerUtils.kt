/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.ui.utils

import androidx.navigation.NavController
import moe.rukamori.archivetune.ui.screens.Screens

fun NavController.backToMain() {
    val mainRoutes = Screens.MainScreens.map { it.route }

    while (previousBackStackEntry != null &&
        currentBackStackEntry?.destination?.route !in mainRoutes
    ) {
        popBackStack()
    }
}

/**
 * Leaves a media page the way the back gesture should once there is nothing left to clear.
 *
 * A page opened straight from a deep link (a shared playlist URL, a notification) has no entry
 * beneath it, so a plain pop finishes the activity and the app closes as if it had crashed. Landing
 * on the library instead keeps the user inside the app, and it is the same place
 * [backToMain] already sends them.
 *
 * The two attempts are wrapped because a pop during an enter/exit transition can throw as well as
 * return false; either way the library navigation is the answer.
 */
fun NavController.popBackOrToLibrary() {
    try {
        if (popBackStack()) return
    } catch (_: Exception) {
        // Fall through to navigateUp below.
    }

    try {
        if (navigateUp()) return
    } catch (_: Exception) {
        // Fall through to the library, which is always reachable.
    }

    navigate("library") { launchSingleTop = true }
}
