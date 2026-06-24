package com.maswadkar.developers.androidify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maswadkar.developers.androidify.R
import com.maswadkar.developers.androidify.data.DiaryActivityType
import com.maswadkar.developers.androidify.data.FieldDiaryEntry
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldDiaryScreen(
    onBackClick: () -> Unit,
    onAddEntryClick: () -> Unit,
    onEntryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FieldDiaryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.field_diary_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddEntryClick,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                },
                text = { Text(stringResource(R.string.field_diary_add_entry)) }
            )
        }
    ) { paddingValues ->
        FieldDiaryContent(
            uiState = uiState,
            onFilterSelected = viewModel::onFilterSelected,
            onRetry = viewModel::loadEntries,
            onAddEntryClick = onAddEntryClick,
            onEntryClick = onEntryClick,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

@Composable
private fun FieldDiaryContent(
    uiState: FieldDiaryUiState,
    onFilterSelected: (DiaryActivityType?) -> Unit,
    onRetry: () -> Unit,
    onAddEntryClick: () -> Unit,
    onEntryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        uiState.isLoading -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        uiState.errorMessage != null -> {
            DiaryErrorState(
                message = uiState.errorMessage,
                onRetry = onRetry,
                modifier = modifier
            )
        }
        else -> {
            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item {
                    FieldDiaryHeader(
                        selectedFilter = uiState.selectedFilter,
                        onFilterSelected = onFilterSelected
                    )
                }

                if (uiState.entries.isEmpty()) {
                    item {
                        DiaryEmptyState(
                            onAddEntryClick = onAddEntryClick,
                            modifier = Modifier.fillParentMaxSize()
                        )
                    }
                } else if (uiState.groupedEntries.isEmpty()) {
                    item {
                        DiaryEmptyState(
                            onAddEntryClick = onAddEntryClick,
                            modifier = Modifier.fillParentMaxSize()
                        )
                    }
                } else {
                    uiState.groupedEntries.forEach { group ->
                        item(key = group.label) {
                            Text(
                                text = group.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                            )
                        }
                        items(
                            items = group.entries,
                            key = { entry -> entry.id.ifBlank { "${entry.activityType}-${entry.activityDate}" } }
                        ) { entry ->
                            DiaryEntryRow(
                                entry = entry,
                                onClick = {
                                    if (entry.id.isNotBlank()) {
                                        onEntryClick(entry.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldDiaryHeader(
    selectedFilter: DiaryActivityType?,
    onFilterSelected: (DiaryActivityType?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp)
    ) {
        Text(
            text = formatFieldDiaryDateLabel(LocalDate.now()),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DiaryFilterChip(
                label = stringResource(R.string.field_diary_filter_all),
                selected = selectedFilter == null,
                onClick = { onFilterSelected(null) }
            )
            DiaryActivityType.entries.forEach { activityType ->
                DiaryFilterChip(
                    label = stringResource(activityType.labelStringRes()),
                    selected = selectedFilter == activityType,
                    onClick = { onFilterSelected(activityType) }
                )
            }
        }
    }
}

@Composable
private fun DiaryFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}

@Composable
private fun DiaryEntryRow(
    entry: FieldDiaryEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activityType = DiaryActivityType.fromFirestoreValue(entry.activityType)
    val activityLabel = stringResource(activityType.labelStringRes())
    val accentColor = diaryActivityAccentColor(activityType)
    val detailParts = buildFieldDiaryDetailParts(entry)
    val costText = formatFieldDiaryCostAmount(entry.costAmount)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TimelineMarker(
            timeText = formatFieldDiaryTimeLabel(entry.activityDate),
            color = accentColor
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 0.dp,
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActivityBadge(
                    activityLabel = activityLabel,
                    color = accentColor
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activityLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (detailParts.isNotEmpty()) {
                        Text(
                            text = detailParts.joinToString(" • "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = entry.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (costText != null || entry.photoPaths.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            costText?.let { DiaryMetaChip(text = it) }
                            if (entry.photoPaths.isNotEmpty()) {
                                DiaryMetaChip(
                                    text = stringResource(R.string.field_diary_photos_count, entry.photoPaths.size),
                                    iconRes = R.drawable.ic_camera
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineMarker(
    timeText: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(58.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun ActivityBadge(
    activityLabel: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = activityLabel.first().toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun DiaryMetaChip(
    text: String,
    iconRes: Int? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        modifier = modifier.widthIn(min = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DiaryEmptyState(
    onAddEntryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_diary),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.field_diary_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.field_diary_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onAddEntryClick) {
            Text(stringResource(R.string.field_diary_add_entry))
        }
    }
}

@Composable
private fun DiaryErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onRetry) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.retry))
        }
    }
}

private fun diaryActivityAccentColor(activityType: DiaryActivityType): Color = when (activityType) {
    DiaryActivityType.LandPreparation -> Color(0xFF795548)
    DiaryActivityType.Sowing -> Color(0xFF558B2F)
    DiaryActivityType.Transplanting -> Color(0xFF00897B)
    DiaryActivityType.Irrigation -> Color(0xFF1976D2)
    DiaryActivityType.Fertilizer -> Color(0xFF2E7D32)
    DiaryActivityType.Weeding -> Color(0xFF6A8A00)
    DiaryActivityType.Pesticide -> Color(0xFF00695C)
    DiaryActivityType.Mulching -> Color(0xFF8D6E63)
    DiaryActivityType.Harvest -> Color(0xFFB26A00)
    DiaryActivityType.PostHarvest -> Color(0xFF6D4C41)
    DiaryActivityType.Other -> Color(0xFF546E7A)
}
