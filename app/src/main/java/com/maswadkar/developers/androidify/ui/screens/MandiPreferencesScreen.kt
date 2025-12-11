package com.maswadkar.developers.androidify.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maswadkar.developers.androidify.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MandiPreferencesScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MandiPreferencesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_mandi_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current preferences card
            if (uiState.hasPreferences && uiState.savedPreferences != null) {
                CurrentPreferencesCard(
                    state = uiState.savedPreferences!!.state,
                    district = uiState.savedPreferences!!.district
                )
            }

            Text(
                text = stringResource(R.string.preferences_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // State Dropdown
            PreferencesDropdown(
                label = stringResource(R.string.filter_state),
                options = uiState.states,
                selectedOption = uiState.selectedState,
                onOptionSelected = viewModel::onStateSelected,
                enabled = uiState.states.isNotEmpty(),
                isLoading = uiState.isLoadingStates
            )

            // District Dropdown
            PreferencesDropdown(
                label = stringResource(R.string.filter_district),
                options = uiState.districts,
                selectedOption = uiState.selectedDistrict,
                onOptionSelected = viewModel::onDistrictSelected,
                enabled = uiState.selectedState != null && uiState.districts.isNotEmpty(),
                isLoading = uiState.isLoadingDistricts
            )

            Spacer(modifier = Modifier.weight(1f))

            // Save button
            Button(
                onClick = {
                    viewModel.savePreferences()
                    onBackClick()
                },
                enabled = uiState.selectedState != null && uiState.selectedDistrict != null && !uiState.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.save_preferences))
                }
            }

            // Error message
            if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun CurrentPreferencesCard(
    state: String,
    district: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.current_location),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = "$district, $state",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreferencesDropdown(
    label: String,
    options: List<String>,
    selectedOption: String?,
    onOptionSelected: (String?) -> Unit,
    enabled: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled && !isLoading) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedOption ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            enabled = enabled && !isLoading,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled && !isLoading)
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

