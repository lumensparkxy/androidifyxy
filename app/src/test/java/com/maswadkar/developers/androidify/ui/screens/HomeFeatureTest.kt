package com.maswadkar.developers.androidify.ui.screens

import com.maswadkar.developers.androidify.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeFeatureTest {

    @Test
    fun `home feature order replaces offers tile with field diary`() {
        assertEquals(
            listOf(
                HomeFeatureDestination.Chat,
                HomeFeatureDestination.PlantDiagnosis,
                HomeFeatureDestination.MandiPrices,
                HomeFeatureDestination.Weather,
                HomeFeatureDestination.KnowledgeBase,
                HomeFeatureDestination.FieldDiary,
                HomeFeatureDestination.CarbonCredits,
                HomeFeatureDestination.History
            ),
            HOME_FEATURES.map { it.destination }
        )
        assertEquals(R.string.home_feature_diary_title, HOME_FEATURES[5].titleRes)
        assertEquals(R.drawable.ic_diary, HOME_FEATURES[5].iconRes)
    }

    @Test
    fun `home feature resources do not include offers`() {
        assertFalse(HOME_FEATURES.any { it.titleRes == R.string.home_feature_offers_title })
    }
}
