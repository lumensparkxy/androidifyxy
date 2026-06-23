package com.maswadkar.developers.androidify.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.maswadkar.developers.androidify.R
import com.maswadkar.developers.androidify.data.DiaryActivityType
import com.maswadkar.developers.androidify.ui.components.ImagePickerBottomSheet
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiaryEntryFormScreen(
    entryId: String?,
    onBackClick: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiaryEntryFormViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showImagePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var tempCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    LaunchedEffect(entryId) {
        viewModel.start(entryId)
    }

    LaunchedEffect(uiState.saveCompleted, uiState.deleteCompleted) {
        if (uiState.saveCompleted || uiState.deleteCompleted) {
            onSaved()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempCameraUri?.let(viewModel::onPhotoSelected)
        }
    }

    val launchDiaryCamera = {
        try {
            val tempFile = File.createTempFile(
                "field_diary_${System.currentTimeMillis()}",
                ".jpg",
                context.cacheDir
            )
            val uri = FileProvider.getUriForFile(
                context,
                "com.maswadkar.developers.androidify.fileprovider",
                tempFile
            )
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (error: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.field_diary_camera_launch_error, error.message.orEmpty()),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchDiaryCamera()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.field_diary_camera_permission_required),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let(viewModel::onPhotoSelected)
    }

    if (showImagePicker) {
        ImagePickerBottomSheet(
            sheetState = bottomSheetState,
            onDismiss = { showImagePicker = false },
            onGalleryClick = {
                scope.launch {
                    bottomSheetState.hide()
                    showImagePicker = false
                    try {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    } catch (error: Exception) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.field_diary_gallery_launch_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onCameraClick = {
                scope.launch {
                    bottomSheetState.hide()
                    showImagePicker = false

                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        launchDiaryCamera()
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.values.activityDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let(viewModel::onActivityDateSelected)
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (uiState.showDeleteDialog) {
        DeleteDiaryEntryDialog(
            isDeleting = uiState.isDeleting,
            onDismiss = viewModel::dismissDeleteConfirmation,
            onConfirm = viewModel::delete
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (uiState.mode == DiaryEntryFormMode.Edit) {
                                R.string.field_diary_form_edit_title
                            } else {
                                R.string.field_diary_form_add_title
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (uiState.mode == DiaryEntryFormMode.Edit) {
                        IconButton(
                            onClick = viewModel::showDeleteConfirmation,
                            enabled = !uiState.isSaving && !uiState.isDeleting
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.field_diary_delete_entry),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    TextButton(
                        onClick = { viewModel.save(context) },
                        enabled = !uiState.isSaving && !uiState.isDeleting && !uiState.isLoading
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.mode == DiaryEntryFormMode.Edit &&
                uiState.errorMessage != null &&
                uiState.values.cropName.isBlank() &&
                uiState.existingPhotoPaths.isEmpty() -> {
                DiaryEntryFormLoadError(
                    message = uiState.errorMessage.orEmpty(),
                    onRetry = { viewModel.start(entryId, forceReload = true) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            else -> {
                DiaryEntryFormContent(
                    uiState = uiState,
                    onDateClick = { showDatePicker = true },
                    onActivityTypeSelected = viewModel::onActivityTypeSelected,
                    onCropNameChanged = viewModel::onCropNameChanged,
                    onFieldNameChanged = viewModel::onFieldNameChanged,
                    onNotesChanged = viewModel::onNotesChanged,
                    onInputNameChanged = viewModel::onInputNameChanged,
                    onQuantityChanged = viewModel::onQuantityChanged,
                    onCostAmountChanged = viewModel::onCostAmountChanged,
                    onAddPhotoClick = { showImagePicker = true },
                    onExistingPhotoRemoved = viewModel::onExistingPhotoRemoved,
                    onNewPhotoRemoved = viewModel::onNewPhotoRemoved,
                    onSave = { viewModel.save(context) },
                    onDismissError = viewModel::clearError,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiaryEntryFormContent(
    uiState: DiaryEntryFormUiState,
    onDateClick: () -> Unit,
    onActivityTypeSelected: (DiaryActivityType) -> Unit,
    onCropNameChanged: (String) -> Unit,
    onFieldNameChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onInputNameChanged: (String) -> Unit,
    onQuantityChanged: (String) -> Unit,
    onCostAmountChanged: (String) -> Unit,
    onAddPhotoClick: () -> Unit,
    onExistingPhotoRemoved: (String) -> Unit,
    onNewPhotoRemoved: (Int) -> Unit,
    onSave: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        DiaryEntryDateRow(
            dateLabel = formatDiaryEntryFormDate(uiState.values.activityDateMillis),
            hasError = DiaryEntryFormFieldError.MissingActivityDate in uiState.fieldErrors,
            onClick = onDateClick
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.field_diary_activity_type),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DiaryActivityType.entries.forEach { activityType ->
                    FilterChip(
                        selected = uiState.values.activityType == activityType,
                        onClick = { onActivityTypeSelected(activityType) },
                        label = { Text(activityType.displayName) }
                    )
                }
            }
        }

        OutlinedTextField(
            value = uiState.values.cropName,
            onValueChange = onCropNameChanged,
            label = { Text(stringResource(R.string.field_diary_crop_required)) },
            singleLine = true,
            isError = DiaryEntryFormFieldError.MissingCropName in uiState.fieldErrors,
            supportingText = errorSupportingText(
                condition = DiaryEntryFormFieldError.MissingCropName in uiState.fieldErrors,
                message = stringResource(R.string.field_diary_error_crop_required)
            ),
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_diary),
                    contentDescription = null
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = uiState.values.fieldName,
            onValueChange = onFieldNameChanged,
            label = { Text(stringResource(R.string.field_diary_field_optional)) },
            singleLine = true,
            trailingIcon = if (uiState.values.fieldName.isNotBlank()) {
                {
                    IconButton(onClick = { onFieldNameChanged("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.clear_filters)
                        )
                    }
                }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = uiState.values.notes,
            onValueChange = onNotesChanged,
            label = { Text(stringResource(R.string.field_diary_notes_required)) },
            minLines = 4,
            maxLines = 6,
            isError = DiaryEntryFormFieldError.MissingNotes in uiState.fieldErrors,
            supportingText = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (DiaryEntryFormFieldError.MissingNotes in uiState.fieldErrors) {
                        Text(stringResource(R.string.field_diary_error_notes_required))
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    Text(
                        text = stringResource(
                            R.string.field_diary_notes_count,
                            uiState.values.notes.length,
                            FIELD_DIARY_NOTES_MAX_LENGTH
                        )
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp)
        )

        OptionalDiaryDetailsSection(
            values = uiState.values,
            fieldErrors = uiState.fieldErrors,
            onInputNameChanged = onInputNameChanged,
            onQuantityChanged = onQuantityChanged,
            onCostAmountChanged = onCostAmountChanged
        )

        DiaryPhotoPickerRow(
            existingPhotoPaths = uiState.existingPhotoPaths,
            existingPhotoPreviews = uiState.existingPhotoPreviews,
            newPhotoUris = uiState.newPhotoUris,
            hasTooManyPhotosError = DiaryEntryFormFieldError.TooManyPhotos in uiState.fieldErrors,
            onAddPhotoClick = onAddPhotoClick,
            onExistingPhotoRemoved = onExistingPhotoRemoved,
            onNewPhotoRemoved = onNewPhotoRemoved
        )

        DiaryFormTip()

        if (uiState.errorMessage != null) {
            DiaryEntryFormErrorCard(
                message = uiState.errorMessage,
                onDismiss = onDismissError
            )
        }

        Button(
            onClick = onSave,
            enabled = !uiState.isSaving && !uiState.isDeleting,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            } else {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(stringResource(R.string.field_diary_save_entry))
        }

        TextButton(
            onClick = onSave,
            enabled = !uiState.isSaving && !uiState.isDeleting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.field_diary_add_details_later))
        }
    }
}

@Composable
private fun DiaryEntryDateRow(
    dateLabel: String,
    hasError: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.field_diary_date),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun OptionalDiaryDetailsSection(
    values: DiaryEntryFormValues,
    fieldErrors: Set<DiaryEntryFormFieldError>,
    onInputNameChanged: (String) -> Unit,
    onQuantityChanged: (String) -> Unit,
    onCostAmountChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.field_diary_optional_details),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.field_diary_optional_details_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = values.inputName,
                onValueChange = onInputNameChanged,
                label = { Text(stringResource(R.string.field_diary_input_name)) },
                singleLine = true,
                modifier = Modifier.widthIn(min = 180.dp).weight(1f)
            )
            OutlinedTextField(
                value = values.quantity,
                onValueChange = onQuantityChanged,
                label = { Text(stringResource(R.string.field_diary_quantity)) },
                singleLine = true,
                modifier = Modifier.widthIn(min = 150.dp).weight(1f)
            )
            OutlinedTextField(
                value = values.costAmountText,
                onValueChange = onCostAmountChanged,
                label = { Text(stringResource(R.string.field_diary_cost_amount)) },
                prefix = { Text("Rs") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = DiaryEntryFormFieldError.InvalidCostAmount in fieldErrors ||
                    DiaryEntryFormFieldError.NegativeCostAmount in fieldErrors,
                supportingText = when {
                    DiaryEntryFormFieldError.InvalidCostAmount in fieldErrors -> {
                        { Text(stringResource(R.string.field_diary_error_cost_invalid)) }
                    }
                    DiaryEntryFormFieldError.NegativeCostAmount in fieldErrors -> {
                        { Text(stringResource(R.string.field_diary_error_cost_negative)) }
                    }
                    else -> null
                },
                modifier = Modifier.widthIn(min = 150.dp).weight(1f)
            )
        }
    }
}

@Composable
private fun DiaryPhotoPickerRow(
    existingPhotoPaths: List<String>,
    existingPhotoPreviews: Map<String, String>,
    newPhotoUris: List<Uri>,
    hasTooManyPhotosError: Boolean,
    onAddPhotoClick: () -> Unit,
    onExistingPhotoRemoved: (String) -> Unit,
    onNewPhotoRemoved: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val photoCount = existingPhotoPaths.size + newPhotoUris.size

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.field_diary_photos_optional),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.field_diary_photo_limit_count, photoCount, FIELD_DIARY_MAX_PHOTOS),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (photoCount < FIELD_DIARY_MAX_PHOTOS) {
                AddDiaryPhotoTile(onClick = onAddPhotoClick)
            }
            existingPhotoPaths.forEach { path ->
                DiaryPhotoTile(
                    model = existingPhotoPreviews[path],
                    fallbackLabel = stringResource(R.string.field_diary_saved_photo),
                    onRemove = { onExistingPhotoRemoved(path) }
                )
            }
            newPhotoUris.forEachIndexed { index, uri ->
                DiaryPhotoTile(
                    model = uri,
                    fallbackLabel = stringResource(R.string.field_diary_new_photo),
                    onRemove = { onNewPhotoRemoved(index) }
                )
            }
        }

        if (hasTooManyPhotosError) {
            Text(
                text = stringResource(R.string.field_diary_error_too_many_photos, FIELD_DIARY_MAX_PHOTOS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun AddDiaryPhotoTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .size(width = 120.dp, height = 108.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_camera),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.field_diary_add_photo),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DiaryPhotoTile(
    model: Any?,
    fallbackLabel: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 132.dp, height = 108.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = fallbackLabel,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_camera),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = fallbackLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(32.dp)
                .background(Color.Black.copy(alpha = 0.72f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.field_diary_remove_photo),
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun DiaryFormTip(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.field_diary_tip),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DiaryEntryFormErrorCard(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.dismiss),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun DiaryEntryFormLoadError(
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
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun DeleteDiaryEntryDialog(
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.field_diary_delete_confirm_title)) },
        text = { Text(stringResource(R.string.field_diary_delete_confirm_message)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isDeleting
            ) {
                Text(
                    text = stringResource(R.string.field_diary_delete_entry),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun errorSupportingText(
    condition: Boolean,
    message: String
): (@Composable () -> Unit)? = if (condition) {
    { Text(message) }
} else {
    null
}
