package com.maswadkar.developers.androidify.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.maswadkar.developers.androidify.R
import com.maswadkar.developers.androidify.ui.components.AppDrawerContent
import com.maswadkar.developers.androidify.ui.components.DrawerItem
import com.maswadkar.developers.androidify.ui.components.DrawerUser
import kotlinx.coroutines.launch

data class HomeFeature(
    val iconRes: Int,
    val titleRes: Int,
    val descriptionRes: Int,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onChatClick: () -> Unit,
    onPlantDiagnosisClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onMandiPricesClick: () -> Unit,
    onWeatherClick: () -> Unit,
    onOffersClick: () -> Unit,
    onCarbonCreditsClick: () -> Unit,
    onKnowledgeBaseClick: () -> Unit,
    onMandiSettingsClick: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Get current user for drawer
    val currentUser = FirebaseAuth.getInstance().currentUser
    val drawerUser = currentUser?.let {
        DrawerUser(
            name = it.displayName ?: "Farmer",
            email = it.email ?: it.phoneNumber ?: "",
            photoUrl = it.photoUrl?.toString()
        )
    }

    val features = listOf(
        HomeFeature(
            iconRes = R.drawable.ic_chat,
            titleRes = R.string.home_feature_chat_title,
            descriptionRes = R.string.home_feature_chat_desc,
            onClick = onChatClick
        ),
        HomeFeature(
            iconRes = R.drawable.ic_camera,
            titleRes = R.string.home_feature_diagnosis_title,
            descriptionRes = R.string.home_feature_diagnosis_desc,
            onClick = onPlantDiagnosisClick
        ),
        HomeFeature(
            iconRes = R.drawable.ic_price,
            titleRes = R.string.home_feature_mandi_title,
            descriptionRes = R.string.home_feature_mandi_desc,
            onClick = onMandiPricesClick
        ),
        HomeFeature(
            iconRes = R.drawable.ic_weather,
            titleRes = R.string.home_feature_weather_title,
            descriptionRes = R.string.home_feature_weather_desc,
            onClick = onWeatherClick
        ),
        HomeFeature(
            iconRes = R.drawable.ic_knowledge,
            titleRes = R.string.home_feature_knowledge_title,
            descriptionRes = R.string.home_feature_knowledge_desc,
            onClick = onKnowledgeBaseClick
        ),
        HomeFeature(
            iconRes = R.drawable.ic_offer,
            titleRes = R.string.home_feature_offers_title,
            descriptionRes = R.string.home_feature_offers_desc,
            onClick = onOffersClick
        ),
        HomeFeature(
            iconRes = R.drawable.ic_carbon,
            titleRes = R.string.home_feature_carbon_title,
            descriptionRes = R.string.home_feature_carbon_desc,
            onClick = onCarbonCreditsClick
        ),
        HomeFeature(
            iconRes = R.drawable.ic_history,
            titleRes = R.string.home_feature_history_title,
            descriptionRes = R.string.home_feature_history_desc,
            onClick = onHistoryClick
        )
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                user = drawerUser,
                selectedItem = null,
                onItemClick = { item ->
                    scope.launch { drawerState.close() }
                    when (item) {
                        DrawerItem.Home -> { /* Already on home */ }
                        DrawerItem.NewChat -> onChatClick()
                        DrawerItem.PlantDiagnosis -> onPlantDiagnosisClick()
                        DrawerItem.History -> onHistoryClick()
                        DrawerItem.MandiPrices -> onMandiPricesClick()
                        DrawerItem.Weather -> onWeatherClick()
                        DrawerItem.Offers -> onOffersClick()
                        DrawerItem.CarbonCredits -> onCarbonCreditsClick()
                        DrawerItem.KnowledgeBase -> onKnowledgeBaseClick()
                        DrawerItem.MandiSettings -> onMandiSettingsClick()
                        DrawerItem.SignOut -> onSignOut()
                    }
                }
            )
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = stringResource(R.string.open_drawer)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Welcome Header
                item(span = { GridItemSpan(2) }) {
                    WelcomeHeader()
                }

                // Feature Cards
                items(features) { feature ->
                    FeatureCard(
                        iconRes = feature.iconRes,
                        title = stringResource(feature.titleRes),
                        description = stringResource(feature.descriptionRes),
                        onClick = feature.onClick
                    )
                }

                // Bottom spacer
                item(span = { GridItemSpan(2) }) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun WelcomeHeader(
    modifier: Modifier = Modifier
) {
    val currentUser = FirebaseAuth.getInstance().currentUser
    val userName = currentUser?.displayName?.split(" ")?.firstOrNull() ?: stringResource(R.string.home_greeting_default_name)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.home_greeting, userName),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun FeatureCard(
    iconRes: Int,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon in a circular container
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(24.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
