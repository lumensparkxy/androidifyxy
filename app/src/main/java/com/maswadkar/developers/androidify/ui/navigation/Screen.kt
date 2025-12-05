package com.maswadkar.developers.androidify.ui.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Chat : Screen("chat")
    data object History : Screen("history")
}

