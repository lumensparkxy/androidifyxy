package com.maswadkar.developers.androidify.ui.navigation

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Home : Screen("home")
    data object Chat : Screen("chat")
    data object PlantDiagnosis : Screen("plant_diagnosis")
    data object History : Screen("history")
    data object MandiPrices : Screen("mandi_prices")
    data object FarmerProfile : Screen("farmer_profile")
    data object Offers : Screen("offers")
    data object FieldDiary : Screen("field_diary")
    data object AddDiaryEntry : Screen("field_diary/add_entry")
    data object EditDiaryEntry : Screen("field_diary/edit_entry/{entryId}") {
        fun createRoute(entryId: String): String {
            val encodedEntryId = URLEncoder.encode(entryId, StandardCharsets.UTF_8.toString())
            return "field_diary/edit_entry/$encodedEntryId"
        }

        fun decodeEntryId(encodedEntryId: String): String {
            return URLDecoder.decode(encodedEntryId, StandardCharsets.UTF_8.toString())
        }
    }
    data object CarbonCredits : Screen("carbon_credits")
    data object KnowledgeBase : Screen("knowledge_base")
    data object Weather : Screen("weather")
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
