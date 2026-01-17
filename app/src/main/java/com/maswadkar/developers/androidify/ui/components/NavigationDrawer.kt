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
    data object NewChat : DrawerItem(R.drawable.ic_add, R.string.menu_new_chat)
    data object History : DrawerItem(R.drawable.ic_chat, R.string.menu_history)
    data object MandiPrices : DrawerItem(R.drawable.ic_price, R.string.menu_mandi_prices)
    data object Weather : DrawerItem(R.drawable.ic_weather, R.string.menu_weather)
    data object Offers : DrawerItem(R.drawable.ic_offer, R.string.menu_offers)
    data object CarbonCredits : DrawerItem(R.drawable.ic_carbon, R.string.menu_carbon_credits)
    data object KnowledgeBase : DrawerItem(R.drawable.ic_knowledge, R.string.menu_knowledge_base)
    data object MandiSettings : DrawerItem(R.drawable.ic_settings, R.string.menu_mandi_settings)
    data object SignOut : DrawerItem(R.drawable.ic_logout, R.string.sign_out)
}

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

        // Main items
        NavigationDrawerItem(
            icon = {
                Icon(
                    painter = painterResource(id = DrawerItem.NewChat.iconRes),
                    contentDescription = null
                )
            },
            label = { Text(stringResource(DrawerItem.NewChat.labelRes)) },
            selected = selectedItem == DrawerItem.NewChat,
            onClick = { onItemClick(DrawerItem.NewChat) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        NavigationDrawerItem(
            icon = {
                Icon(
                    painter = painterResource(id = DrawerItem.History.iconRes),
                    contentDescription = null
                )
            },
            label = { Text(stringResource(DrawerItem.History.labelRes)) },
            selected = selectedItem == DrawerItem.History,
            onClick = { onItemClick(DrawerItem.History) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        NavigationDrawerItem(
            icon = {
                Icon(
                    painter = painterResource(id = DrawerItem.MandiPrices.iconRes),
                    contentDescription = null
                )
            },
            label = { Text(stringResource(DrawerItem.MandiPrices.labelRes)) },
            selected = selectedItem == DrawerItem.MandiPrices,
            onClick = { onItemClick(DrawerItem.MandiPrices) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        NavigationDrawerItem(
            icon = {
                Icon(
                    painter = painterResource(id = DrawerItem.Weather.iconRes),
                    contentDescription = null
                )
            },
            label = { Text(stringResource(DrawerItem.Weather.labelRes)) },
            selected = selectedItem == DrawerItem.Weather,
            onClick = { onItemClick(DrawerItem.Weather) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        NavigationDrawerItem(
            icon = {
                Icon(
                    painter = painterResource(id = DrawerItem.Offers.iconRes),
                    contentDescription = null
                )
            },
            label = { Text(stringResource(DrawerItem.Offers.labelRes)) },
            selected = selectedItem == DrawerItem.Offers,
            onClick = { onItemClick(DrawerItem.Offers) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        NavigationDrawerItem(
            icon = {
                Icon(
                    painter = painterResource(id = DrawerItem.CarbonCredits.iconRes),
                    contentDescription = null
                )
            },
            label = { Text(stringResource(DrawerItem.CarbonCredits.labelRes)) },
            selected = selectedItem == DrawerItem.CarbonCredits,
            onClick = { onItemClick(DrawerItem.CarbonCredits) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        NavigationDrawerItem(
            icon = {
                Icon(
                    painter = painterResource(id = DrawerItem.KnowledgeBase.iconRes),
                    contentDescription = null
                )
            },
            label = { Text(stringResource(DrawerItem.KnowledgeBase.labelRes)) },
            selected = selectedItem == DrawerItem.KnowledgeBase,
            onClick = { onItemClick(DrawerItem.KnowledgeBase) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

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
                    painter = painterResource(id = DrawerItem.MandiSettings.iconRes),
                    contentDescription = null
                )
            },
            label = { Text(stringResource(DrawerItem.MandiSettings.labelRes)) },
            selected = selectedItem == DrawerItem.MandiSettings,
            onClick = { onItemClick(DrawerItem.MandiSettings) },
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
