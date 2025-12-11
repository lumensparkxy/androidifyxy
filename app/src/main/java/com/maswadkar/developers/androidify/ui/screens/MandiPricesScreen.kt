package com.maswadkar.developers.androidify.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maswadkar.developers.androidify.R
import com.maswadkar.developers.androidify.data.MandiPrice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MandiPricesScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MandiPricesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_mandi_prices)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (uiState.prices.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearFilters() }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.clear_filters)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Show loading during initialization
            if (uiState.isInitializing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            // Filters Section - Compact or Full mode
            if (uiState.isCompactMode) {
                CompactFiltersSection(
                    uiState = uiState,
                    onCommoditySelected = viewModel::onCommoditySelected,
                    onSearchClick = viewModel::loadPrices,
                    onChangeLocation = viewModel::switchToFullMode
                )
            } else {
                FullFiltersSection(
                    uiState = uiState,
                    onStateSelected = viewModel::onStateSelected,
                    onDistrictSelected = viewModel::onDistrictSelected,
                    onCommoditySelected = viewModel::onCommoditySelected,
                    onSearchClick = viewModel::loadPrices,
                    onCancelChange = if (uiState.hasPreferences) viewModel::switchToCompactMode else null
                )
            }

            HorizontalDivider()

            // Results Section
            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    uiState.error != null -> {
                        ErrorMessage(
                            message = uiState.error!!,
                            onDismiss = viewModel::clearError,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    uiState.prices.isEmpty() && uiState.selectedState != null && uiState.selectedDistrict != null -> {
                        EmptyState(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    !uiState.isCompactMode && uiState.selectedState == null -> {
                        SelectStatePrompt(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    uiState.isCompactMode && uiState.prices.isEmpty() -> {
                        SearchPrompt(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        PricesList(
                            prices = uiState.prices,
                            latestDataDate = uiState.latestDataDate
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactFiltersSection(
    uiState: MandiPricesUiState,
    onCommoditySelected: (String?) -> Unit,
    onSearchClick: () -> Unit,
    onChangeLocation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Location chip with change button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = { },
                label = {
                    Text(
                        text = "${uiState.savedPreferences?.district}, ${uiState.savedPreferences?.state}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            TextButton(onClick = onChangeLocation) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.change_location))
            }
        }

        // Commodity dropdown + Search button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterDropdown(
                label = stringResource(R.string.filter_commodity),
                options = uiState.commodities,
                selectedOption = uiState.selectedCommodity,
                onOptionSelected = onCommoditySelected,
                enabled = uiState.commodities.isNotEmpty(),
                modifier = Modifier.weight(1f)
            )

            FilledTonalButton(
                onClick = onSearchClick,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.search))
            }
        }
    }
}

@Composable
private fun FullFiltersSection(
    uiState: MandiPricesUiState,
    onStateSelected: (String?) -> Unit,
    onDistrictSelected: (String?) -> Unit,
    onCommoditySelected: (String?) -> Unit,
    onSearchClick: () -> Unit,
    onCancelChange: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Show cancel button if user has preferences and switched to full mode
        if (onCancelChange != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.change_location_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onCancelChange) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }

        // State Dropdown
        FilterDropdown(
            label = stringResource(R.string.filter_state),
            options = uiState.states,
            selectedOption = uiState.selectedState,
            onOptionSelected = onStateSelected,
            enabled = uiState.states.isNotEmpty()
        )

        // District Dropdown
        FilterDropdown(
            label = stringResource(R.string.filter_district),
            options = uiState.districts,
            selectedOption = uiState.selectedDistrict,
            onOptionSelected = onDistrictSelected,
            enabled = uiState.selectedState != null && uiState.districts.isNotEmpty()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Commodity Dropdown
            FilterDropdown(
                label = stringResource(R.string.filter_commodity),
                options = uiState.commodities,
                selectedOption = uiState.selectedCommodity,
                onOptionSelected = onCommoditySelected,
                enabled = uiState.selectedDistrict != null && uiState.commodities.isNotEmpty(),
                modifier = Modifier.weight(1f)
            )

            // Search Button
            FilledTonalButton(
                onClick = onSearchClick,
                enabled = uiState.selectedState != null && uiState.selectedDistrict != null,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.search))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    options: List<String>,
    selectedOption: String?,
    onOptionSelected: (String?) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { newValue -> if (enabled) expanded = newValue },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedOption ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            enabled = enabled,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun PricesList(
    prices: List<MandiPrice>,
    modifier: Modifier = Modifier,
    latestDataDate: String? = null
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Show "Data as of" header if we have a date
        if (latestDataDate != null) {
            item {
                DataAsOfHeader(date = latestDataDate)
            }
        }

        items(prices, key = { it.id.ifEmpty { "${it.market}_${it.commodity}_${it.arrivalDate}" } }) { price ->
            PriceCard(price = price)
        }
    }
}

@Composable
private fun DataAsOfHeader(
    date: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.data_as_of, date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun PriceCard(
    price: MandiPrice,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header with commodity name - uses primary container for theme harmony
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = price.commodity,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (price.variety.isNotEmpty() || price.grade.isNotEmpty()) {
                        Text(
                            text = listOfNotNull(
                                price.variety.takeIf { it.isNotEmpty() },
                                price.grade.takeIf { it.isNotEmpty() }
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Content section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Market and Location with icon
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = price.market,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "${price.district}, ${price.state}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Prices Row with theme-aware chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PriceChip(
                        label = stringResource(R.string.price_min),
                        value = "₹${price.minPrice.toInt()}",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    PriceChip(
                        label = stringResource(R.string.price_modal),
                        value = "₹${price.modalPrice.toInt()}",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        isHighlighted = true
                    )
                    PriceChip(
                        label = stringResource(R.string.price_max),
                        value = "₹${price.maxPrice.toInt()}",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Arrival Date with calendar icon
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = price.arrivalDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceChip(
    label: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.7f)
            )
            Text(
                text = value,
                style = if (isHighlighted) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

@Composable
private fun SelectStatePrompt(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.select_state_prompt),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SearchPrompt(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.search_prompt),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.no_prices_found),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorMessage(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.dismiss))
        }
    }
}
