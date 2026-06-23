package com.maswadkar.developers.androidify.ui.screens

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.maswadkar.developers.androidify.data.FieldDiaryRepository
import kotlinx.coroutines.launch

data class DiaryEntryFormUiState(
    val mode: DiaryEntryFormMode = DiaryEntryFormMode.Add,
    val entryId: String = "",
    val values: DiaryEntryFormValues = DiaryEntryFormValues(
        activityDateMillis = todayDiaryEntryFormDateMillis()
    ),
    val existingCreatedAt: Timestamp? = null,
    val existingPhotoPaths: List<String> = emptyList(),
    val removedExistingPhotoPaths: Set<String> = emptySet(),
    val existingPhotoPreviews: Map<String, String> = emptyMap(),
    val newPhotoUris: List<Uri> = emptyList(),
    val fieldErrors: Set<DiaryEntryFormFieldError> = emptySet(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
    val saveCompleted: Boolean = false,
    val deleteCompleted: Boolean = false,
    val showDeleteDialog: Boolean = false
) {
    val currentPhotoCount: Int
        get() = existingPhotoPaths.size + newPhotoUris.size
}

class DiaryEntryFormViewModel(
    private val repository: FieldDiaryRepository = FieldDiaryRepository.getInstance()
) : ViewModel() {

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(DiaryEntryFormUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<DiaryEntryFormUiState> = _uiState

    private var activeRouteKey: String? = null

    fun start(entryId: String?, forceReload: Boolean = false) {
        val normalizedEntryId = entryId?.trim()?.takeIf { it.isNotBlank() }
        val routeKey = normalizedEntryId ?: ADD_ROUTE_KEY
        if (!forceReload && activeRouteKey == routeKey) {
            return
        }

        activeRouteKey = routeKey
        if (normalizedEntryId == null) {
            _uiState.value = DiaryEntryFormUiState(
                mode = DiaryEntryFormMode.Add,
                values = DiaryEntryFormValues(activityDateMillis = todayDiaryEntryFormDateMillis())
            )
            return
        }

        _uiState.value = DiaryEntryFormUiState(
            mode = DiaryEntryFormMode.Edit,
            entryId = normalizedEntryId,
            isLoading = true
        )

        viewModelScope.launch {
            try {
                val entry = repository.getEntry(normalizedEntryId)
                    ?: throw IllegalStateException("Diary entry not found")
                _uiState.value = DiaryEntryFormUiState(
                    mode = DiaryEntryFormMode.Edit,
                    entryId = entry.id,
                    values = DiaryEntryFormValues(
                        activityDateMillis = todayDiaryEntryFormDateMillis()
                    ).withEntry(entry, fallbackMillis = todayDiaryEntryFormDateMillis()),
                    existingCreatedAt = entry.createdAt,
                    existingPhotoPaths = entry.photoPaths
                )
                resolvePhotoPreviews(entry.photoPaths)
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Unable to load diary entry"
                )
            }
        }
    }

    fun onActivityDateSelected(millis: Long) {
        updateValues { copy(activityDateMillis = millis) }
    }

    fun onActivityTypeSelected(activityType: com.maswadkar.developers.androidify.data.DiaryActivityType) {
        updateValues { copy(activityType = activityType) }
    }

    fun onCropNameChanged(value: String) {
        updateValues { copy(cropName = value) }
    }

    fun onFieldNameChanged(value: String) {
        updateValues { copy(fieldName = value) }
    }

    fun onNotesChanged(value: String) {
        updateValues { withBoundedNotes(value) }
    }

    fun onInputNameChanged(value: String) {
        updateValues { copy(inputName = value) }
    }

    fun onQuantityChanged(value: String) {
        updateValues { copy(quantity = value) }
    }

    fun onCostAmountChanged(value: String) {
        updateValues { copy(costAmountText = value) }
    }

    fun onPhotoSelected(uri: Uri) {
        val state = _uiState.value
        if (state.currentPhotoCount >= FIELD_DIARY_MAX_PHOTOS) {
            _uiState.value = state.copy(
                fieldErrors = state.fieldErrors + DiaryEntryFormFieldError.TooManyPhotos
            )
            return
        }

        _uiState.value = state.copy(
            newPhotoUris = state.newPhotoUris + uri,
            fieldErrors = state.fieldErrors - DiaryEntryFormFieldError.TooManyPhotos,
            errorMessage = null
        )
    }

    fun onNewPhotoRemoved(index: Int) {
        val state = _uiState.value
        if (index !in state.newPhotoUris.indices) {
            return
        }

        _uiState.value = state.copy(
            newPhotoUris = state.newPhotoUris.filterIndexed { itemIndex, _ -> itemIndex != index },
            fieldErrors = state.fieldErrors - DiaryEntryFormFieldError.TooManyPhotos,
            errorMessage = null
        )
    }

    fun onExistingPhotoRemoved(path: String) {
        val state = _uiState.value
        if (path !in state.existingPhotoPaths) {
            return
        }

        _uiState.value = state.copy(
            existingPhotoPaths = state.existingPhotoPaths - path,
            removedExistingPhotoPaths = state.removedExistingPhotoPaths + path,
            fieldErrors = state.fieldErrors - DiaryEntryFormFieldError.TooManyPhotos,
            errorMessage = null
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun showDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteDialog = true)
    }

    fun dismissDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteDialog = false)
    }

    fun save(context: Context) {
        val state = _uiState.value
        if (state.isSaving || state.isDeleting) {
            return
        }

        val validation = validateDiaryEntryForm(
            values = state.values,
            photoCount = state.currentPhotoCount
        )
        if (validation.errors.isNotEmpty() || validation.normalizedValues == null) {
            _uiState.value = state.copy(
                fieldErrors = validation.errors,
                errorMessage = null
            )
            return
        }

        viewModelScope.launch {
            val uploadedPaths = mutableListOf<String>()
            val applicationContext = context.applicationContext
            _uiState.value = _uiState.value.copy(
                isSaving = true,
                errorMessage = null,
                fieldErrors = emptySet()
            )

            try {
                val entryId = state.entryId.ifBlank { repository.generateEntryId() }
                for (uri in state.newPhotoUris) {
                    uploadedPaths += repository.uploadPhoto(applicationContext, entryId, uri)
                }

                val photoPaths = state.existingPhotoPaths + uploadedPaths
                val entry = buildDiaryEntryFromFormValues(
                    entryId = entryId,
                    existingCreatedAt = state.existingCreatedAt,
                    values = validation.normalizedValues,
                    photoPaths = photoPaths
                )

                if (state.mode == DiaryEntryFormMode.Edit) {
                    repository.updateEntry(entryId, entry)
                } else {
                    repository.createEntry(entry)
                }

                deletePhotosBestEffort(state.removedExistingPhotoPaths)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    entryId = entryId,
                    saveCompleted = true,
                    errorMessage = null
                )
            } catch (error: Exception) {
                deletePhotosBestEffort(uploadedPaths)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = error.message ?: "Unable to save diary entry"
                )
            }
        }
    }

    fun delete() {
        val state = _uiState.value
        val entryId = state.entryId.trim()
        if (state.mode != DiaryEntryFormMode.Edit || entryId.isBlank() || state.isDeleting) {
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(
                isDeleting = true,
                showDeleteDialog = false,
                errorMessage = null
            )
            try {
                repository.deleteEntry(entryId)
                deletePhotosBestEffort(state.existingPhotoPaths + state.removedExistingPhotoPaths)
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    deleteCompleted = true
                )
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    errorMessage = error.message ?: "Unable to delete diary entry"
                )
            }
        }
    }

    private fun updateValues(update: DiaryEntryFormValues.() -> DiaryEntryFormValues) {
        val state = _uiState.value
        _uiState.value = state.copy(
            values = state.values.update(),
            fieldErrors = emptySet(),
            errorMessage = null
        )
    }

    private fun resolvePhotoPreviews(paths: List<String>) {
        paths.forEach { path ->
            viewModelScope.launch {
                try {
                    val url = repository.resolvePhotoUrl(path)
                    val state = _uiState.value
                    if (path in state.existingPhotoPaths) {
                        _uiState.value = state.copy(
                            existingPhotoPreviews = state.existingPhotoPreviews + (path to url)
                        )
                    }
                } catch (error: Exception) {
                    Log.w(TAG, "Unable to resolve diary photo preview: $path", error)
                }
            }
        }
    }

    private suspend fun deletePhotosBestEffort(paths: Iterable<String>) {
        paths.forEach { path ->
            try {
                repository.deletePhoto(path)
            } catch (error: Exception) {
                Log.w(TAG, "Unable to delete diary photo: $path", error)
            }
        }
    }

    private companion object {
        private const val TAG = "DiaryEntryFormViewModel"
        private const val ADD_ROUTE_KEY = "add"
    }
}
