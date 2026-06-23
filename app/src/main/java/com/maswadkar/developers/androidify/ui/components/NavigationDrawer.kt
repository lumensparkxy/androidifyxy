package com.maswadkar.developers.androidify.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.maswadkar.developers.androidify.R

data class DrawerUser(
    val name: String,
    val email: String,
    val photoUrl: String?
)

sealed class DrawerItem(
    val iconRes: Int,
    val labelRes: Int
) {
    data object Home : DrawerItem(R.drawable.ic_home, R.string.menu_home)
    data object NewChat : DrawerItem(R.drawable.ic_add, R.string.menu_new_chat)
    data object PlantDiagnosis : DrawerItem(R.drawable.ic_camera, R.string.menu_plant_diagnosis)
    data object History : DrawerItem(R.drawable.ic_history, R.string.menu_history)
    data object MandiPrices : DrawerItem(R.drawable.ic_price, R.string.menu_mandi_prices)
    data object Weather : DrawerItem(R.drawable.ic_weather, R.string.menu_weather)
    data object Offers : DrawerItem(R.drawable.ic_offer, R.string.menu_offers)
    data object FieldDiary : DrawerItem(R.drawable.ic_diary, R.string.menu_field_diary)
    data object CarbonCredits : DrawerItem(R.drawable.ic_carbon, R.string.menu_carbon_credits)
    data object KnowledgeBase : DrawerItem(R.drawable.ic_knowledge, R.string.menu_knowledge_base)
    data object FarmerProfile : DrawerItem(R.drawable.ic_settings, R.string.menu_mandi_settings)
    data object SignOut : DrawerItem(R.drawable.ic_logout, R.string.sign_out)
}

internal val MAIN_DRAWER_ITEMS = listOf(
    DrawerItem.NewChat,
    DrawerItem.PlantDiagnosis,
    DrawerItem.History,
    DrawerItem.MandiPrices,
    DrawerItem.FieldDiary,
    DrawerItem.Weather,
    DrawerItem.Offers,
    DrawerItem.CarbonCredits,
    DrawerItem.KnowledgeBase
)

@Composable
fun AppDrawerContent(
    user: DrawerUser?,
    selectedItem: DrawerItem?,
    onItemClick: (DrawerItem) -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(modifier = modifier) {
        // Header
        DrawerHeader(user = user)

        HorizontalDivider()

        Spacer(modifier = Modifier.height(8.dp))

        // Home item
        NavigationDrawerItem(
            icon = {
                Icon(
                    painter = painterResource(id = DrawerItem.Home.iconRes),
                    contentDescription = null
                )
            },
            label = { Text(stringResource(DrawerItem.Home.labelRes)) },
            selected = selectedItem == DrawerItem.Home,
            onClick = { onItemClick(DrawerItem.Home) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Main items
        MAIN_DRAWER_ITEMS.forEach { item ->
            AppDrawerItem(
                item = item,
                selected = selectedItem == item,
                onClick = { onItemClick(item) }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        HorizontalDivider()

        // Other section
        Text(
            text = stringResource(R.string.menu_other),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
        )

        NavigationDrawerItem(
            icon = {
                Icon(
                    painter = painterResource(id = DrawerItem.FarmerProfile.iconRes),
                    contentDescription = null
                )
            },
            label = { Text(stringResource(DrawerItem.FarmerProfile.labelRes)) },
            selected = selectedItem == DrawerItem.FarmerProfile,
            onClick = { onItemClick(DrawerItem.FarmerProfile) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        NavigationDrawerItem(
            icon = {
                Icon(
                    painter = painterResource(id = DrawerItem.SignOut.iconRes),
                    contentDescription = null
                )
            },
            label = { Text(stringResource(DrawerItem.SignOut.labelRes)) },
            selected = false,
            onClick = { onItemClick(DrawerItem.SignOut) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AppDrawerItem(
    item: DrawerItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        icon = {
            Icon(
                painter = painterResource(id = item.iconRes),
                contentDescription = null
            )
        },
        label = { Text(stringResource(item.labelRes)) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

@Composable
private fun DrawerHeader(
    user: DrawerUser?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(top = 24.dp)
        ) {
            // User Photo
            if (user?.photoUrl != null) {
                AsyncImage(
                    model = user.photoUrl,
                    contentDescription = stringResource(R.string.user_photo),
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_default_avatar),
                    contentDescription = stringResource(R.string.user_photo),
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // User Name
            Text(
                text = user?.name ?: "User",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            // User Email
            if (user?.email != null) {
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
