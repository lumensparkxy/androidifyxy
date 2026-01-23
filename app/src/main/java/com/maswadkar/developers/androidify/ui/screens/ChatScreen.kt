@file:Suppress("UnusedAssignment")

package com.maswadkar.developers.androidify.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.maswadkar.developers.androidify.ChatMessage
import com.maswadkar.developers.androidify.R
import com.maswadkar.developers.androidify.ui.components.AppDrawerContent
import com.maswadkar.developers.androidify.ui.components.ChatBubble
import com.maswadkar.developers.androidify.ui.components.ChatInput
import com.maswadkar.developers.androidify.ui.components.DrawerItem
import com.maswadkar.developers.androidify.ui.components.DrawerUser
import com.maswadkar.developers.androidify.ui.components.ImagePickerBottomSheet
import com.maswadkar.developers.androidify.ui.components.WelcomeScreen
import kotlinx.coroutines.launch
import java.io.File

@Suppress("UNUSED_VALUE")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    onSendMessage: (String, Uri?) -> Unit,
    onNewChat: () -> Unit,
    onPlantDiagnosisClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onMandiPricesClick: () -> Unit,
    onWeatherClick: () -> Unit,
    onOffersClick: () -> Unit,
    onCarbonCreditsClick: () -> Unit,
    onKnowledgeBaseClick: () -> Unit,
    onMandiSettingsClick: () -> Unit,
    onSignOut: () -> Unit,
    onExportConversation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    var inputText by remember { mutableStateOf("") }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showImagePicker by remember { mutableStateOf(false) }
    var showLiveConversation by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Temporary file for camera capture
    var tempCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    // Audio permission launcher for voice conversation
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showLiveConversation = true
        } else {
            Toast.makeText(context, context.getString(R.string.mic_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { attachedImageUri = it }
    }

    // Camera launcher (captures to file)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempCameraUri?.let { attachedImageUri = it }
        }
    }

    val launchCamera = {
        try {
            val tempFile = File.createTempFile(
                "camera_${System.currentTimeMillis()}",
                ".jpg",
                context.cacheDir
            )
            // Use explicit authority to avoid package name ambiguity
            val authority = "com.maswadkar.developers.androidify.fileprovider"
            val uri = FileProvider.getUriForFile(
                context,
                authority,
                tempFile
            )
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error launching camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    // Get current user info
    val currentUser = FirebaseAuth.getInstance().currentUser
    val drawerUser = currentUser?.let {
        DrawerUser(
            name = it.displayName ?: "User",
            email = it.email ?: "",
            photoUrl = it.photoUrl?.toString()
        )
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Image picker bottom sheet
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
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(context, "Error launching gallery", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onCameraClick = {
                scope.launch {
                    bottomSheetState.hide()
                    showImagePicker = false
                    
                    val permissionCheckResult = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    if (permissionCheckResult == PackageManager.PERMISSION_GRANTED) {
                        launchCamera()
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                user = drawerUser,
                selectedItem = null,
                onItemClick = { item ->
                    scope.launch { drawerState.close() }
                    when (item) {
                        DrawerItem.NewChat -> onNewChat()
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
            modifier = modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
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
                    actions = {
                        if (messages.isNotEmpty()) {
                            IconButton(onClick = onExportConversation) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = stringResource(R.string.export_conversation)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    scrollBehavior = scrollBehavior
                )
            },
            bottomBar = {
                ChatInput(
                    value = inputText,
                    onValueChange = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank() || attachedImageUri != null) {
                            onSendMessage(inputText, attachedImageUri)
                            inputText = ""
                            attachedImageUri = null
                        }
                    },
                    attachedImageUri = attachedImageUri,
                    onAttachClick = { showImagePicker = true },
                    onRemoveImage = { attachedImageUri = null },
                    onMicClick = {
                        // Check for audio recording permission
                        val permissionCheckResult = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        )
                        if (permissionCheckResult == PackageManager.PERMISSION_GRANTED) {
                            showLiveConversation = true
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.imePadding()
                )
            },
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets
                .exclude(WindowInsets.navigationBars)
                .exclude(WindowInsets.ime)
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (messages.isEmpty()) {
                    WelcomeScreen(
                        onExampleClick = { question ->
                            onSendMessage(question, null)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = messages,
                            key = { "${it.isUser}-${it.text.hashCode()}-${messages.indexOf(it)}" }
                        ) { message ->
                            ChatBubble(message = message)
                        }
                    }
                }
            }
        }
    }

    // Live Conversation overlay
    if (showLiveConversation) {
        LiveConversationScreen(
            onDismiss = { showLiveConversation = false }
        )
    }
}
