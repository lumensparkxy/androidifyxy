package com.maswadkar.developers.androidify.ui.navigation

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Chat : Screen("chat")
    data object History : Screen("history")
    data object MandiPrices : Screen("mandi_prices")
    data object MandiSettings : Screen("mandi_settings")
    data object Offers : Screen("offers")
    data object CarbonCredits : Screen("carbon_credits")
    data object KnowledgeBase : Screen("knowledge_base")
    data object KnowledgeDocuments : Screen("knowledge_documents/{cropId}/{cropName}") {
        fun createRoute(cropId: String, cropName: String): String {
            val encodedName = URLEncoder.encode(cropName, StandardCharsets.UTF_8.toString())
            return "knowledge_documents/$cropId/$encodedName"
        }

        fun decodeCropName(encodedName: String): String {
            return URLDecoder.decode(encodedName, StandardCharsets.UTF_8.toString())
        }
    }
}

