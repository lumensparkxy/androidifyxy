package com.maswadkar.developers.androidify

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctionsException
import com.maswadkar.developers.androidify.data.AgenticChatHistoryMessage
import com.maswadkar.developers.androidify.data.AgenticChatRepository
import com.maswadkar.developers.androidify.data.AgenticChatRequest
import com.maswadkar.developers.androidify.data.ChatRepository
import com.maswadkar.developers.androidify.data.Conversation
import com.maswadkar.developers.androidify.data.FarmerProfile
import com.maswadkar.developers.androidify.data.FarmerProfileRepository
import com.maswadkar.developers.androidify.data.ImageStorageRepository
import com.maswadkar.developers.androidify.data.LeadProfileDraft
import com.maswadkar.developers.androidify.data.Message
import com.maswadkar.developers.androidify.data.ProductRecommendation
import com.maswadkar.developers.androidify.data.SalesLeadRepository
import com.maswadkar.developers.androidify.data.SalesLeadRequest
import com.maswadkar.developers.androidify.data.sanitizeLeadMobileInput
import com.maswadkar.developers.androidify.data.withLeadContactFallbacks
import com.maswadkar.developers.androidify.util.ImageUtils
import com.maswadkar.developers.androidify.util.AppConfigManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

class ChatViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChatViewModel"
        private const val MESSAGES_KEY = "chat_messages"
        private const val CONVERSATION_ID_KEY = "conversation_id"
        private const val CONVERSATION_SAVED_KEY = "conversation_saved"
        private const val CONVERSATION_DIRTY_KEY = "conversation_dirty"
        private const val LEAD_REQUEST_SOURCE = "chat_recommendation"
    }

    private val chatRepository = ChatRepository.getInstance()
    private val imageStorageRepository = ImageStorageRepository.getInstance()
    private val agenticChatRepository = AgenticChatRepository.getInstance()
    private val farmerProfileRepository = FarmerProfileRepository.getInstance()
    private val salesLeadRepository = SalesLeadRepository.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val currentUserId: String?
        get() = auth.currentUser?.uid

    private val _messages = MutableStateFlow(
        savedStateHandle.get<ArrayList<ChatMessage>>(MESSAGES_KEY)?.toList() ?: emptyList()
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private var currentConversationId: String? = savedStateHandle.get<String>(CONVERSATION_ID_KEY)
    private var isConversationSaved: Boolean = savedStateHandle.get<Boolean>(CONVERSATION_SAVED_KEY) ?: false
    private var hasUnsavedConversationChanges: Boolean = savedStateHandle.get<Boolean>(CONVERSATION_DIRTY_KEY)
        ?: (!isConversationSaved && _messages.value.any { !it.isLoading })

    private val _userIdFlow = MutableStateFlow(currentUserId)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val conversationsFlow: StateFlow<List<Conversation>> = _userIdFlow
        .flatMapLatest { userId ->
            if (userId != null) chatRepository.getConversationsFlow(userId) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _leadUiState = MutableStateFlow(LeadRequestUiState())
    val leadUiState: StateFlow<LeadRequestUiState> = _leadUiState.asStateFlow()

    private val deviceLocale: String = Locale.getDefault().toLanguageTag()
    private val model = Firebase.ai(backend = GenerativeBackend.vertexAI("global"))
        .generativeModel(
            modelName = AppConstants.AI_MODEL_NAME,
            systemInstruction = content { text(AppConstants.getSystemInstruction(deviceLocale)) }
        )

    private var chat: Chat? = null
    private var currentRequestJob: Job? = null
    private val saveMutex = Mutex()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _userIdFlow.value = firebaseAuth.currentUser?.uid
            Log.d(TAG, "Auth state changed, userId: ${firebaseAuth.currentUser?.uid}")
        }

        val cleanedMessages = _messages.value.map { msg ->
            if (msg.isLoading) msg.copy(text = AppConstants.REQUEST_INTERRUPTED_MESSAGE, isLoading = false)
            else msg
        }
        if (cleanedMessages != _messages.value) {
            updateMessages(cleanedMessages)
        }
    }

    private fun buildChatHistory(): List<Content> {
        return _messages.value
            .filter { !it.isLoading }
            .map { msg ->
                content(role = if (msg.isUser) "user" else "model") {
                    val messageText = if (msg.imageUri != null && msg.text == "[Image attached]") {
                        "[User shared an image]"
                    } else {
                        msg.text
                    }
                    text(messageText)
                }
            }
    }

    private fun buildAgenticRecentMessages(): List<AgenticChatHistoryMessage> {
        return _messages.value
            .dropLast(2)
            .filter { !it.isLoading }
            .map { msg ->
                AgenticChatHistoryMessage(
                    role = if (msg.isUser) "user" else "model",
                    text = if (msg.imageUri != null && msg.text == "[Image attached]") {
                        "[User shared an image]"
                    } else {
                        msg.text
                    },
                    imageUrl = msg.imageUrl
                )
            }
    }

    private fun shouldUseAgenticChat(): Boolean = AppConfigManager.shouldUseAgenticChat(currentUserId)

    private fun initializeChatWithHistory() {
        val history = buildChatHistory()
        chat = model.startChat(history = history)
        Log.d(TAG, "Initialized chat with ${history.size} history entries")
    }

    fun refreshUserState() {
        _userIdFlow.value = currentUserId
    }

    private fun setConversationDirty(isDirty: Boolean) {
        hasUnsavedConversationChanges = isDirty
        savedStateHandle[CONVERSATION_DIRTY_KEY] = isDirty
    }

    private fun updateMessages(newMessages: List<ChatMessage>, markDirty: Boolean = true) {
        val limitedMessages = if (newMessages.size > AppConstants.MAX_MESSAGES) {
            newMessages.takeLast(AppConstants.MAX_MESSAGES)
        } else {
            newMessages
        }
        _messages.value = limitedMessages
        savedStateHandle[MESSAGES_KEY] = ArrayList(limitedMessages)
        setConversationDirty(markDirty)
    }

    fun startNewConversation() {
        saveCurrentConversation()
        currentConversationId = imageStorageRepository.generateConversationId()
        savedStateHandle[CONVERSATION_ID_KEY] = currentConversationId
        isConversationSaved = false
        savedStateHandle[CONVERSATION_SAVED_KEY] = false
        updateMessages(emptyList(), markDirty = false)
        chat = model.startChat()
        Log.d(TAG, "Started new conversation with pre-generated ID: $currentConversationId")
    }

    private fun clearCurrentConversation() {
        currentConversationId = imageStorageRepository.generateConversationId()
        savedStateHandle[CONVERSATION_ID_KEY] = currentConversationId
        isConversationSaved = false
        savedStateHandle[CONVERSATION_SAVED_KEY] = false
        updateMessages(emptyList(), markDirty = false)
        chat = model.startChat()
        Log.d(TAG, "Cleared current conversation, new pre-generated ID: $currentConversationId")
    }

    fun loadConversation(conversationId: String) {
        val isReloadingCurrentConversation = conversationId == currentConversationId
        if (!isReloadingCurrentConversation) {
            saveCurrentConversation()
        }

        viewModelScope.launch {
            try {
                val conversation = chatRepository.loadConversation(conversationId)
                if (conversation != null) {
                    currentConversationId = conversationId
                    savedStateHandle[CONVERSATION_ID_KEY] = conversationId
                    isConversationSaved = true
                    savedStateHandle[CONVERSATION_SAVED_KEY] = true

                    val chatMessages = conversation.messages.map { msg ->
                        ChatMessage(
                            text = msg.text,
                            isUser = msg.isUser,
                            isLoading = false,
                            imageUri = null,
                            imageUrl = msg.imageUrl
                        )
                    }
                    updateMessages(chatMessages, markDirty = false)
                    initializeChatWithHistory()
                    Log.d(TAG, "Loaded conversation: $conversationId with ${chatMessages.size} messages")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading conversation: ${e.message}")
            }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                chatRepository.deleteConversation(conversationId)
                if (conversationId == currentConversationId) {
                    clearCurrentConversation()
                }
                Log.d(TAG, "Deleted conversation: $conversationId")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting conversation: ${e.message}")
            }
        }
    }

    fun refreshConversations() {
        val userId = currentUserId
        _userIdFlow.value = null
        _userIdFlow.value = userId
    }

    fun getCurrentConversationForExport(): Conversation? {
        val currentMessages = _messages.value
        if (currentMessages.isEmpty() || currentMessages.all { it.isLoading }) return null

        val userId = currentUserId ?: return null
        val title = Conversation.generateTitle(
            currentMessages.firstOrNull { it.isUser && !it.isLoading }?.text ?: "Conversation"
        )

        return Conversation(
            id = currentConversationId ?: "",
            userId = userId,
            title = title,
            messages = currentMessages
                .filter { !it.isLoading }
                .map { msg ->
                    Message(
                        text = msg.text,
                        isUser = msg.isUser
                    )
                }
        )
    }

    fun saveCurrentConversation() {
        val userId = currentUserId ?: return
        val conversationIdToSave = currentConversationId
        val messagesToSave = _messages.value.toList()
        val wasAlreadySaved = isConversationSaved

        if (!hasUnsavedConversationChanges) return
        if (messagesToSave.isEmpty() || messagesToSave.all { it.isLoading }) return

        viewModelScope.launch {
            saveConversationInternal(userId, conversationIdToSave, messagesToSave, wasAlreadySaved)
        }
    }

    suspend fun saveCurrentConversationSync() {
        val userId = currentUserId ?: return
        val conversationIdToSave = currentConversationId
        val messagesToSave = _messages.value.toList()
        val wasAlreadySaved = isConversationSaved

        if (!hasUnsavedConversationChanges) return
        if (messagesToSave.isEmpty() || messagesToSave.all { it.isLoading }) return

        saveConversationInternal(userId, conversationIdToSave, messagesToSave, wasAlreadySaved)
    }

    private suspend fun saveConversationInternal(
        userId: String,
        conversationIdToSave: String?,
        messagesToSave: List<ChatMessage>,
        wasAlreadySaved: Boolean
    ) {
        withContext(NonCancellable) {
            saveMutex.withLock {
                if (messagesToSave.isEmpty() || messagesToSave.all { it.isLoading }) return@withLock

                try {
                    val firestoreMessages = messagesToSave.filter { !it.isLoading }.map { msg ->
                        Message(
                            text = msg.text,
                            isUser = msg.isUser,
                            timestamp = Timestamp.now(),
                            imageUrl = msg.imageUrl
                        )
                    }

                    val title = messagesToSave.firstOrNull { it.isUser }?.let {
                        Conversation.generateTitle(it.text)
                    } ?: "New Conversation"

                    val conversation = Conversation(
                        id = conversationIdToSave ?: "",
                        userId = userId,
                        title = title,
                        messages = firestoreMessages
                    )

                    val isNew = !wasAlreadySaved && conversationIdToSave != null
                    chatRepository.saveConversation(conversation, isNew = isNew)

                    if (!wasAlreadySaved) {
                        isConversationSaved = true
                        savedStateHandle[CONVERSATION_SAVED_KEY] = true
                    }
                    setConversationDirty(false)

                    Log.d(TAG, "Saved conversation: ${conversationIdToSave ?: "new"} (isNew: $isNew)")
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving conversation: ${e.message}")
                }
            }
        }
    }

    private fun ensureCurrentConversationId(): String {
        if (currentConversationId == null) {
            currentConversationId = imageStorageRepository.generateConversationId()
            savedStateHandle[CONVERSATION_ID_KEY] = currentConversationId
            Log.d(TAG, "Generated conversation ID on demand: $currentConversationId")
        }
        return currentConversationId!!
    }

    fun onRecommendationLeadClick(recommendation: ProductRecommendation, chatMessageText: String) {
        val uid = currentUserId ?: run {
            _leadUiState.update { it.copy(errorMessage = "Please sign in to continue") }
            return
        }

        val pendingRequest = PendingLeadRequest(
            recommendation = recommendation,
            chatMessageText = chatMessageText,
            conversationId = ensureCurrentConversationId()
        )

        viewModelScope.launch {
            _leadUiState.update {
                it.copy(
                    isSubmitting = true,
                    errorMessage = null,
                    confirmationRequestNumber = null,
                    pendingRequest = pendingRequest
                )
            }

            try {
                val existingProfile = farmerProfileRepository.getUserProfile(uid)
                val profile = (existingProfile ?: FarmerProfile()).copy(
                    name = existingProfile?.name ?: auth.currentUser?.displayName
                ).withLeadContactFallbacks(
                    auth.currentUser?.phoneNumber,
                    auth.currentUser?.email,
                )

                if (!profile.hasLeadRequiredFields()) {
                    _leadUiState.update {
                        it.copy(
                            isSubmitting = false,
                            showProfileDialog = true,
                            profileDraft = LeadProfileDraft(
                                name = profile.name.orEmpty(),
                                mobileNumber = profile.mobileNumber.orEmpty(),
                                village = profile.village.orEmpty(),
                                tehsil = profile.tehsil.orEmpty(),
                                district = profile.district,
                                totalFarmAcres = profile.totalFarmAcres?.let { acres ->
                                    if (acres % 1.0 == 0.0) acres.toInt().toString() else acres.toString()
                                }.orEmpty()
                            )
                        )
                    }
                    return@launch
                }

                submitLeadInternal(pendingRequest)
            } catch (e: Exception) {
                _leadUiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = e.message ?: "Unable to load your profile right now"
                    )
                }
            }
        }
    }

    fun onLeadNameChanged(value: String) {
        _leadUiState.update { it.copy(profileDraft = it.profileDraft.copy(name = value), nameError = null) }
    }

    fun onLeadMobileNumberChanged(value: String) {
        _leadUiState.update {
            it.copy(
                profileDraft = it.profileDraft.copy(mobileNumber = sanitizeLeadMobileInput(value)),
                mobileNumberError = null,
            )
        }
    }

    fun onLeadVillageChanged(value: String) {
        _leadUiState.update { it.copy(profileDraft = it.profileDraft.copy(village = value), villageError = null) }
    }

    fun onLeadTehsilChanged(value: String) {
        _leadUiState.update { it.copy(profileDraft = it.profileDraft.copy(tehsil = value), tehsilError = null) }
    }

    fun onLeadDistrictChanged(value: String) {
        _leadUiState.update { it.copy(profileDraft = it.profileDraft.copy(district = value), districtError = null) }
    }

    fun onLeadTotalFarmAcresChanged(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        _leadUiState.update {
            it.copy(
                profileDraft = it.profileDraft.copy(totalFarmAcres = filtered),
                totalFarmAcresError = null
            )
        }
    }

    fun dismissLeadProfileDialog() {
        _leadUiState.update {
            it.copy(
                isSubmitting = false,
                showProfileDialog = false,
                pendingRequest = null,
                nameError = null,
                mobileNumberError = null,
                villageError = null,
                tehsilError = null,
                districtError = null,
                totalFarmAcresError = null
            )
        }
    }

    fun dismissLeadConfirmation() {
        _leadUiState.update { it.copy(confirmationRequestNumber = null) }
    }

    fun clearLeadError() {
        _leadUiState.update { it.copy(errorMessage = null, isSubmitting = false) }
    }

    fun submitLeadAfterProfileUpdate() {
        val uid = currentUserId ?: run {
            _leadUiState.update { it.copy(errorMessage = "Please sign in to continue") }
            return
        }
        val pendingRequest = _leadUiState.value.pendingRequest ?: run {
            _leadUiState.update { it.copy(errorMessage = "No active request found") }
            return
        }

        val draft = _leadUiState.value.profileDraft.normalized()
        val nameError = if (draft.name.isBlank()) "Name is required" else null
        val mobileNumberError = when {
            draft.mobileNumber.isBlank() -> "Mobile number is required"
            draft.mobileNumber.length != 10 -> "Enter a valid 10-digit mobile number"
            else -> null
        }
        val villageError = if (draft.village.isBlank()) "Village is required" else null
        val tehsilError = if (draft.tehsil.isBlank()) "Tehsil is required" else null
        val districtError = if (draft.district.isBlank()) "District is required" else null
        val totalFarmAcresError = when {
            draft.totalFarmAcres.isBlank() -> "Land size is required"
            draft.totalFarmAcres.toDoubleOrNull()?.let { it > 0 } != true -> "Enter a valid land size"
            else -> null
        }

        if (listOf(nameError, mobileNumberError, villageError, tehsilError, districtError, totalFarmAcresError).any { it != null }) {
            _leadUiState.update {
                it.copy(
                    nameError = nameError,
                    mobileNumberError = mobileNumberError,
                    villageError = villageError,
                    tehsilError = tehsilError,
                    districtError = districtError,
                    totalFarmAcresError = totalFarmAcresError
                )
            }
            return
        }

        viewModelScope.launch {
            _leadUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            try {
                val existingProfile = (farmerProfileRepository.getUserProfile(uid) ?: FarmerProfile())
                    .withLeadContactFallbacks(auth.currentUser?.phoneNumber, auth.currentUser?.email)
                val mergedProfile = existingProfile.copy(
                    name = draft.name,
                    mobileNumber = draft.mobileNumber,
                    village = draft.village,
                    tehsil = draft.tehsil,
                    district = draft.district,
                    totalFarmAcres = draft.totalFarmAcres.toDoubleOrNull()
                )

                val saveSuccess = farmerProfileRepository.saveUserProfile(uid, mergedProfile)
                if (!saveSuccess) {
                    _leadUiState.update {
                        it.copy(isSubmitting = false, errorMessage = "Failed to save your details")
                    }
                    return@launch
                }

                submitLeadInternal(pendingRequest)
            } catch (e: Exception) {
                _leadUiState.update {
                    it.copy(isSubmitting = false, errorMessage = e.message ?: "Failed to save your details")
                }
            }
        }
    }


    private fun mapLeadSubmissionError(e: FirebaseFunctionsException): String = when (e.code) {
        FirebaseFunctionsException.Code.FAILED_PRECONDITION -> "Please complete your profile to continue"
        FirebaseFunctionsException.Code.INVALID_ARGUMENT -> "We could not register this request. Please try again."
        FirebaseFunctionsException.Code.NOT_FOUND,
        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
        FirebaseFunctionsException.Code.PERMISSION_DENIED -> "We could not register your request right now. Please try again in a moment."
        else -> "Unable to register your request right now"
    }

    private fun mapLeadSubmissionErrorMessage(message: String?): String {
        val normalized = message?.trim().orEmpty()
        return when {
            normalized.contains("NOT_FOUND", ignoreCase = true) -> "We could not register your request right now. Please try again in a moment."
            normalized.contains("PERMISSION_DENIED", ignoreCase = true) -> "We could not register your request right now. Please try again in a moment."
            normalized.equals("Please complete your profile to continue", ignoreCase = true) -> "Please complete your profile to continue"
            normalized.isBlank() -> "Unable to register your request right now"
            else -> normalized
        }
    }

    private suspend fun submitLeadInternal(pendingRequest: PendingLeadRequest) {
        try {
            val result = salesLeadRepository.submitLead(
                SalesLeadRequest(
                    conversationId = pendingRequest.conversationId,
                    productName = pendingRequest.recommendation.productName,
                    quantity = pendingRequest.recommendation.quantity,
                    unit = pendingRequest.recommendation.unit,
                    chatMessageText = pendingRequest.chatMessageText,
                    source = LEAD_REQUEST_SOURCE
                )
            )

            _leadUiState.update {
                it.copy(
                    isSubmitting = false,
                    showProfileDialog = false,
                    pendingRequest = null,
                    confirmationRequestNumber = result.requestNumber,
                    errorMessage = null,
                    nameError = null,
                    mobileNumberError = null,
                    villageError = null,
                    tehsilError = null,
                    districtError = null,
                    totalFarmAcresError = null
                )
            }
        } catch (e: FirebaseFunctionsException) {
            val message = mapLeadSubmissionError(e)
            _leadUiState.update { it.copy(isSubmitting = false, errorMessage = message) }
        } catch (e: Exception) {
            _leadUiState.update {
                it.copy(
                    isSubmitting = false,
                    errorMessage = mapLeadSubmissionErrorMessage(e.message)
                )
            }
        }
    }

    private suspend fun sendLegacyMessage(userText: String, imageUri: Uri?): String {
        if (chat == null) {
            val historyMessages = _messages.value.dropLast(2)
            val history = historyMessages
                .filter { !it.isLoading }
                .map { msg ->
                    content(role = if (msg.isUser) "user" else "model") {
                        val messageText = if (msg.imageUri != null && msg.text == "[Image attached]") {
                            "[User shared an image]"
                        } else {
                            msg.text
                        }
                        text(messageText)
                    }
                }
            chat = model.startChat(history = history)
        }

        val response = if (imageUri != null) {
            val bitmap = ImageUtils.loadAndCompressBitmap(
                getApplication<Application>().applicationContext,
                imageUri
            )

            if (bitmap != null) {
                chat!!.sendMessage(
                    content {
                        image(bitmap)
                        if (userText.isNotBlank()) {
                            text(userText)
                        }
                    }
                )
            } else {
                chat!!.sendMessage(userText.ifBlank { "Please describe what you see." })
            }
        } else {
            chat!!.sendMessage(userText)
        }

        return response.text ?: AppConstants.NO_RESPONSE_MESSAGE
    }

    fun sendMessage(userText: String, imageUri: Uri? = null) {
        if (userText.isBlank() && imageUri == null) return

        val userId = currentUserId
        val conversationId = ensureCurrentConversationId()

        val userMessage = ChatMessage(
            text = userText.ifBlank { "[Image attached]" },
            isUser = true,
            imageUri = imageUri?.toString()
        )
        val currentMessages = _messages.value.toMutableList()
        val userMessageIndex = currentMessages.size
        currentMessages.add(userMessage)

        val loadingMessage = ChatMessage(AppConstants.LOADING_MESSAGES.first(), isUser = false, isLoading = true)
        currentMessages.add(loadingMessage)
        updateMessages(currentMessages)

        val loadingIndex = _messages.value.size - 1
        currentRequestJob?.cancel()

        currentRequestJob = viewModelScope.launch {
            var uploadedImageUrl: String? = null
            if (imageUri != null && userId != null) {
                try {
                    uploadedImageUrl = imageStorageRepository.uploadImage(
                        getApplication<Application>().applicationContext,
                        userId,
                        conversationId,
                        imageUri
                    )

                    val updatedMessages = _messages.value.toMutableList()
                    if (userMessageIndex < updatedMessages.size) {
                        updatedMessages[userMessageIndex] = updatedMessages[userMessageIndex].copy(
                            imageUrl = uploadedImageUrl
                        )
                        updateMessages(updatedMessages)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to upload image: ${e.message}", e)
                }
            }

            val animationJob = launch {
                while (isActive) {
                    delay(AppConstants.LOADING_MESSAGE_INTERVAL_MS)
                    val updatedMessages = _messages.value.toMutableList()
                    if (loadingIndex < updatedMessages.size && updatedMessages[loadingIndex].isLoading) {
                        updatedMessages[loadingIndex] = updatedMessages[loadingIndex].copy(
                            text = AppConstants.LOADING_MESSAGES.random()
                        )
                        updateMessages(updatedMessages)
                    }
                }
            }

            try {
                val modelText = if (shouldUseAgenticChat()) {
                    try {
                        val agenticResponse = agenticChatRepository.sendMessage(
                            AgenticChatRequest(
                                conversationId = conversationId,
                                message = userText,
                                locale = deviceLocale,
                                imageUrl = uploadedImageUrl,
                                recentMessages = buildAgenticRecentMessages()
                            )
                        )

                        if (!agenticResponse.metadata.requestNumber.isNullOrBlank()) {
                            _leadUiState.update {
                                it.copy(
                                    confirmationRequestNumber = agenticResponse.metadata.requestNumber,
                                    errorMessage = null
                                )
                            }
                        }

                        agenticResponse.text.ifBlank { AppConstants.NO_RESPONSE_MESSAGE }
                    } catch (e: Exception) {
                        Log.w(TAG, "Agentic chat path failed, falling back to legacy model: ${e.message}", e)
                        sendLegacyMessage(userText = userText, imageUri = imageUri)
                    }
                } else {
                    sendLegacyMessage(userText = userText, imageUri = imageUri)
                }

                animationJob.cancel()

                val updatedMessages = _messages.value.toMutableList()
                if (loadingIndex < updatedMessages.size) {
                    updatedMessages[loadingIndex] = updatedMessages[loadingIndex].copy(
                        text = modelText,
                        isLoading = false
                    )
                    updateMessages(updatedMessages)
                    saveCurrentConversation()
                }
            } catch (e: Exception) {
                animationJob.cancel()
                val updatedMessages = _messages.value.toMutableList()
                if (loadingIndex < updatedMessages.size) {
                    updatedMessages[loadingIndex] = updatedMessages[loadingIndex].copy(
                        text = "Error: ${e.message}",
                        isLoading = false
                    )
                    updateMessages(updatedMessages)
                }
            }
        }
    }
}
