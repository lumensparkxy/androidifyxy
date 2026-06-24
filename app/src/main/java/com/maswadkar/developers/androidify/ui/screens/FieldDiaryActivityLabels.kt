package com.maswadkar.developers.androidify.ui.screens

import androidx.annotation.StringRes
import com.maswadkar.developers.androidify.R
import com.maswadkar.developers.androidify.data.DiaryActivityType

@StringRes
internal fun DiaryActivityType.labelStringRes(): Int = when (this) {
    DiaryActivityType.LandPreparation -> R.string.field_diary_activity_land_preparation
    DiaryActivityType.Sowing -> R.string.field_diary_activity_sowing
    DiaryActivityType.Transplanting -> R.string.field_diary_activity_transplanting
    DiaryActivityType.Irrigation -> R.string.field_diary_activity_irrigation
    DiaryActivityType.Fertilizer -> R.string.field_diary_activity_fertilizer
    DiaryActivityType.Weeding -> R.string.field_diary_activity_weeding
    DiaryActivityType.Pesticide -> R.string.field_diary_activity_pesticide
    DiaryActivityType.Mulching -> R.string.field_diary_activity_mulching
    DiaryActivityType.Harvest -> R.string.field_diary_activity_harvest
    DiaryActivityType.PostHarvest -> R.string.field_diary_activity_post_harvest
    DiaryActivityType.Other -> R.string.field_diary_activity_other
}
