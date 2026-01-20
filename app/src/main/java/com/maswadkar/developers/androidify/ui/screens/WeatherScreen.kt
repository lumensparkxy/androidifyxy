package com.maswadkar.developers.androidify.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.maswadkar.developers.androidify.R
import com.maswadkar.developers.androidify.weather.WeatherAstro
import com.maswadkar.developers.androidify.weather.WeatherForecastDay
import com.maswadkar.developers.androidify.ui.components.NativeAdCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WeatherViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    val hasPermissionState = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermissionState.value = granted
        viewModel.load(permissionGranted = granted)
    }

    LaunchedEffect(Unit) {
        // Auto-load on first open
        viewModel.load(permissionGranted = hasPermissionState.value)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.weather_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.load(
                                permissionGranted = hasPermissionState.value,
                                forceRefresh = true
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.weather_refresh)
                        )
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                LoadingState(padding)
            }

            uiState.needsPermission -> {
                PermissionState(
                    paddingValues = padding,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
                )
            }

            uiState.error != null -> {
                ErrorState(
                    paddingValues = padding,
                    message = uiState.error ?: "",
                    onRetry = { viewModel.load(permissionGranted = hasPermissionState.value) }
                )
            }

            uiState.forecast != null -> {
                ForecastContent(
                    paddingValues = padding,
                    locationLabel = uiState.forecast!!.locationLabel,
                    isFromCache = uiState.forecast!!.isFromCache,
                    days = uiState.forecast!!.days,
                    astro = uiState.forecast!!.astro
                )
            }

            else -> {
                // Fallback
                LoadingState(padding)
            }
        }
    }
}

@Composable
private fun LoadingState(paddingValues: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.weather_loading))
    }
}

@Composable
private fun PermissionState(
    paddingValues: PaddingValues,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.weather_permission_required),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.material3.Button(onClick = onRequestPermission) {
            Text(stringResource(R.string.weather_enable_location))
        }
    }
}

@Composable
private fun ErrorState(
    paddingValues: PaddingValues,
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.material3.Button(onClick = onRetry) {
            Text(stringResource(R.string.weather_try_again))
        }
    }
}

@Composable
private fun ForecastContent(
    paddingValues: PaddingValues,
    locationLabel: String,
    isFromCache: Boolean,
    days: List<WeatherForecastDay>,
    astro: WeatherAstro?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = locationLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.weather_forecast_3_days),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isFromCache) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.weather_from_cache),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Native Ad Unit (same as MandiPrices/Offers)
        NativeAdCard(
            adUnitId = "ca-app-pub-6317522941728465/6769905906"
        )

        // Astro card
        if (astro != null && (astro.sunrise != null || astro.sunset != null || astro.moonPhase != null)) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Sun & Moon",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    AstroRow(
                        icon = Icons.Default.WbSunny,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        label = "Sunrise",
                        value = astro.sunrise ?: "—"
                    )
                    AstroRow(
                        icon = Icons.Default.Bedtime,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        label = "Sunset",
                        value = astro.sunset ?: "—"
                    )
                    AstroRow(
                        icon = Icons.Default.DarkMode,
                        iconTint = MaterialTheme.colorScheme.primary,
                        label = "Moon phase",
                        value = astro.moonPhase ?: "—"
                    )
                }
            }
        }

        // 3-Day Forecast Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.weather_forecast_3_days),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    days.forEachIndexed { index, day ->
                        ForecastDayTile(
                            day = day,
                            isToday = index == 0
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AstroRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ForecastDayTile(
    day: WeatherForecastDay,
    isToday: Boolean = false
) {
    val containerColor = if (isToday) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val contentColor = if (isToday) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.width(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isToday) 4.dp else 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Day label at top
            Text(
                text = if (isToday) "Today" else formatDayLabel(day.dateEpochSeconds),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )

            // Weather Icon
            day.conditionIconUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = day.conditionText,
                    modifier = Modifier.size(40.dp)
                )
            } ?: Spacer(modifier = Modifier.size(40.dp))

            // Temperature range
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "${day.maxTempC.toInt()}°",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = "${day.minTempC.toInt()}°",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isToday) {
                        contentColor.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

private fun formatDayLabel(dateEpochSeconds: Long): String {
    val date = Date(dateEpochSeconds * 1000L)
    val fmt = SimpleDateFormat("EEE", Locale.getDefault())
    return fmt.format(date)
}
