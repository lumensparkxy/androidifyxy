package com.maswadkar.developers.androidify.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maswadkar.developers.androidify.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarbonCreditsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CarbonCreditsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var formTopPosition by remember { mutableIntStateOf(0) }

    // Show success dialog
    if (uiState.submitSuccess) {
        AlertDialog(
            onDismissRequest = {
                viewModel.resetForm()
                onBackClick()
            },
            title = { Text("Success") },
            text = { Text(stringResource(R.string.submit_success_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetForm()
                    onBackClick()
                }) {
                    Text("OK")
                }
            }
        )
    }

    // Show error snackbar
    if (uiState.error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(uiState.error ?: "An error occurred") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_carbon_credits)) },
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
                .imePadding() // Handle keyboard
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Headline
            Text(
                text = stringResource(R.string.carbon_credits_headline),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Body text
            Text(
                text = stringResource(R.string.carbon_credits_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )


            Spacer(modifier = Modifier.height(8.dp))

            // Calculate button - track its position for scrolling
            if (!uiState.showForm) {
                Button(
                    onClick = {
                        viewModel.onCalculateClick()
                        // Scroll to form after it appears
                        coroutineScope.launch {
                            // Small delay to let the form render
                            kotlinx.coroutines.delay(100)
                            scrollState.animateScrollTo(formTopPosition)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            formTopPosition = coordinates.positionInParent().y.toInt()
                        },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.calculate_carbon_credit_button),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            // Form section (animated visibility)
            AnimatedVisibility(
                visible = uiState.showForm,
                enter = fadeIn() + expandVertically(),
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    formTopPosition = coordinates.positionInParent().y.toInt()
                }
            ) {
                CarbonCreditForm(
                    uiState = uiState,
                    onLandSizeChange = viewModel::onLandSizeChange,
                    onTreeCountChange = viewModel::onTreeCountChange,
                    onGreenFarmingChange = viewModel::onGreenFarmingChange,
                    onPhoneNumberChange = viewModel::onPhoneNumberChange,
                    onSubmit = viewModel::onSubmit
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CarbonCreditForm(
    uiState: CarbonCreditsUiState,
    onLandSizeChange: (String) -> Unit,
    onTreeCountChange: (String) -> Unit,
    onGreenFarmingChange: (Boolean) -> Unit,
    onPhoneNumberChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.carbon_form_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            // Land size input
            OutlinedTextField(
                value = uiState.landSizeAcres,
                onValueChange = onLandSizeChange,
                label = { Text(stringResource(R.string.land_size_label)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                isError = uiState.landSizeError != null,
                supportingText = uiState.landSizeError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Tree count input
            OutlinedTextField(
                value = uiState.bigTreeCount,
                onValueChange = onTreeCountChange,
                label = { Text(stringResource(R.string.tree_count_label)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                isError = uiState.treeCountError != null,
                supportingText = uiState.treeCountError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Green farming interest toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.green_farming_label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.interestedInGreenFarming,
                    onCheckedChange = onGreenFarmingChange
                )
            }

            // Phone number input
            OutlinedTextField(
                value = uiState.phoneNumber,
                onValueChange = onPhoneNumberChange,
                label = { Text(stringResource(R.string.phone_number_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = uiState.phoneNumberError != null,
                supportingText = uiState.phoneNumberError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Submit button
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSubmitting
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(R.string.submit),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

