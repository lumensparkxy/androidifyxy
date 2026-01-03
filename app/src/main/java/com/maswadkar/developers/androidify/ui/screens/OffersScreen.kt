package com.maswadkar.developers.androidify.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maswadkar.developers.androidify.R
import com.maswadkar.developers.androidify.data.FilterOption
import com.maswadkar.developers.androidify.data.Offer
import com.maswadkar.developers.androidify.data.OfferFilters
import com.maswadkar.developers.androidify.data.OfferRepository
import com.maswadkar.developers.androidify.ui.components.NativeAdCard
import com.maswadkar.developers.androidify.ui.components.OfferDetailBottomSheet
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OffersScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OffersViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Show offer detail bottom sheet when an offer is selected
    if (uiState.selectedOffer != null) {
        OfferDetailBottomSheet(
            offer = uiState.selectedOffer!!,
            supplier = uiState.selectedOfferSupplier,
            isLoadingSupplier = uiState.isLoadingSupplier,
            sheetState = bottomSheetState,
            onDismiss = { viewModel.onDismissOfferDetail() }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_offers)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (uiState.selectedDistrictId != null || uiState.selectedCategory != null) {
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
            // Filters Section
            FiltersSection(
                districts = uiState.districts,
                categories = uiState.categories,
                selectedDistrictId = uiState.selectedDistrictId,
                selectedCategory = uiState.selectedCategory,
                onDistrictSelected = viewModel::onDistrictSelected,
                onCategorySelected = viewModel::onCategorySelected
            )

            // Sort Section
            SortSection(
                sortField = uiState.sortField,
                sortDirection = uiState.sortDirection,
                onSortByPrice = viewModel::onSortByPrice,
                onSortByDate = viewModel::onSortByDate
            )

            HorizontalDivider()

            // Native Ad Unit
            NativeAdCard(
                adUnitId = "ca-app-pub-6317522941728465/6769905906"
            )

            // Results Section
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator()
                    }
                    uiState.error != null -> {
                        ErrorMessage(
                            message = uiState.error!!,
                            onDismiss = viewModel::clearError
                        )
                    }
                    uiState.offers.isEmpty() -> {
                        EmptyOffersState()
                    }
                    else -> {
                        OffersList(
                            offers = uiState.offers,
                            onOfferClick = viewModel::onOfferSelected
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltersSection(
    districts: List<FilterOption>,
    categories: List<FilterOption>,
    selectedDistrictId: String?,
    selectedCategory: String?,
    onDistrictSelected: (String?) -> Unit,
    onCategorySelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var districtExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // District Filter
            ExposedDropdownMenuBox(
                expanded = districtExpanded,
                onExpandedChange = { districtExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = selectedDistrictId?.let { OfferFilters.getDistrictDisplayName(it) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.filter_district)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = districtExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    singleLine = true
                )

                ExposedDropdownMenu(
                    expanded = districtExpanded,
                    onDismissRequest = { districtExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.all_districts)) },
                        onClick = {
                            onDistrictSelected(null)
                            districtExpanded = false
                        }
                    )
                    districts.forEach { district ->
                        DropdownMenuItem(
                            text = { Text(district.displayName) },
                            onClick = {
                                onDistrictSelected(district.id)
                                districtExpanded = false
                            }
                        )
                    }
                }
            }

            // Category Filter
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = selectedCategory?.let { OfferFilters.getCategoryDisplayName(it) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.filter_category)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    singleLine = true
                )

                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.all_categories)) },
                        onClick = {
                            onCategorySelected(null)
                            categoryExpanded = false
                        }
                    )
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.displayName) },
                            onClick = {
                                onCategorySelected(category.id)
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortSection(
    sortField: OfferRepository.SortField,
    sortDirection: OfferRepository.SortDirection,
    onSortByPrice: () -> Unit,
    onSortByDate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.sort_by),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.CenterVertically)
        )

        FilterChip(
            selected = sortField == OfferRepository.SortField.PRICE_NORMALIZED,
            onClick = onSortByPrice,
            label = { Text(stringResource(R.string.sort_price)) },
            trailingIcon = if (sortField == OfferRepository.SortField.PRICE_NORMALIZED) {
                {
                    Icon(
                        imageVector = if (sortDirection == OfferRepository.SortDirection.ASCENDING)
                            Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else null
        )

        FilterChip(
            selected = sortField == OfferRepository.SortField.CREATED_AT,
            onClick = onSortByDate,
            label = { Text(stringResource(R.string.sort_date)) },
            trailingIcon = if (sortField == OfferRepository.SortField.CREATED_AT) {
                {
                    Icon(
                        imageVector = if (sortDirection == OfferRepository.SortDirection.ASCENDING)
                            Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else null
        )
    }
}

@Composable
private fun OffersList(
    offers: List<Offer>,
    onOfferClick: (Offer) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(offers, key = { it.id }) { offer ->
            OfferCard(
                offer = offer,
                onClick = { onOfferClick(offer) }
            )
        }
    }
}

@Composable
private fun OfferCard(
    offer: Offer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Product Name and Category
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = offer.productName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!offer.npkRaw.isNullOrEmpty()) {
                        Text(
                            text = "NPK: ${offer.npkRaw}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = OfferFilters.getCategoryDisplayName(offer.category),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pack Size and Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${offer.packSize.toInt()} ${offer.packUnit}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "₹${offer.priceRetail.toInt()}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(8.dp))

            // Supplier and Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = offer.supplierName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                offer.createdAt?.toDate()?.let { date ->
                    Text(
                        text = dateFormat.format(date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyOffersState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.no_offers_found),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.no_offers_hint),
            style = MaterialTheme.typography.bodyMedium,
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
    Card(
        modifier = modifier.padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss))
            }
        }
    }
}

