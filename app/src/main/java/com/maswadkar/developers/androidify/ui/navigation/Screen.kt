package com.maswadkar.developers.androidify.ui.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Chat : Screen("chat")
    data object History : Screen("history")
    data object MandiPrices : Screen("mandi_prices")
    data object MandiSettings : Screen("mandi_settings")
    data object Offers : Screen("offers")
    data object CarbonCredits : Screen("carbon_credits")
}

