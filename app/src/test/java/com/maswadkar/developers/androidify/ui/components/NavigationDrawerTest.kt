package com.maswadkar.developers.androidify.ui.components

import com.maswadkar.developers.androidify.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationDrawerTest {

    @Test
    fun `main drawer includes field diary and keeps offers`() {
        assertTrue(MAIN_DRAWER_ITEMS.contains(DrawerItem.FieldDiary))
        assertTrue(MAIN_DRAWER_ITEMS.contains(DrawerItem.Offers))
        assertEquals(R.string.menu_field_diary, DrawerItem.FieldDiary.labelRes)
    }

    @Test
    fun `field diary is grouped with farmer tools before offers`() {
        val fieldDiaryIndex = MAIN_DRAWER_ITEMS.indexOf(DrawerItem.FieldDiary)
        val offersIndex = MAIN_DRAWER_ITEMS.indexOf(DrawerItem.Offers)

        assertTrue(fieldDiaryIndex > MAIN_DRAWER_ITEMS.indexOf(DrawerItem.MandiPrices))
        assertTrue(fieldDiaryIndex < offersIndex)
    }
}
