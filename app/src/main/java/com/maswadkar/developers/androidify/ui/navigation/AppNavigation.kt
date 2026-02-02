package com.maswadkar.developers.androidify.ui.navigation

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.maswadkar.developers.androidify.ChatViewModel
import com.maswadkar.developers.androidify.R
import com.maswadkar.developers.androidify.auth.AuthState
import com.maswadkar.developers.androidify.auth.AuthViewModel
import com.maswadkar.developers.androidify.ui.screens.ChatScreen
import com.maswadkar.developers.androidify.ui.screens.CarbonCreditsScreen
import com.maswadkar.developers.androidify.ui.screens.HistoryScreen
import com.maswadkar.developers.androidify.ui.screens.HomeScreen
import com.maswadkar.developers.androidify.ui.screens.KnowledgeBaseScreen
import com.maswadkar.developers.androidify.ui.screens.KnowledgeDocumentsScreen
import com.maswadkar.developers.androidify.ui.screens.LoginScreen
import com.maswadkar.developers.androidify.ui.screens.MandiPreferencesScreen
import com.maswadkar.developers.androidify.ui.screens.MandiPricesScreen
import com.maswadkar.developers.androidify.ui.screens.OffersScreen
import com.maswadkar.developers.androidify.ui.screens.PlantDiagnosisScreen
import com.maswadkar.developers.androidify.ui.screens.WeatherScreen
import com.maswadkar.developers.androidify.util.PdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppNavigation(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    startDestination: String,
    modifier: Modifier = Modifier
) {
    val authState by authViewModel.authState.collectAsState()
    val messages by chatViewModel.messages.collectAsState()
    val conversations by chatViewModel.conversationsFlow.collectAsState()

    // Track if this is initial composition to avoid overriding deep link navigation
    var isInitialLoad by remember { mutableStateOf(true) }

    // Handle auth state changes (only after initial load)
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> {
                if (!isInitialLoad) {
                    // Only navigate to Home on auth state change (e.g., after login)
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
                isInitialLoad = false
            }
            is AuthState.Unauthenticated -> {
                navController.navigate(Screen.Login.route) {
                    popUpTo(0) { inclusive = true }
                }
                isInitialLoad = false
            }
            else -> {}
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Login.route) {
            val activity = LocalActivity.current
            LoginScreen(
                authState = authState,
                onGoogleSignInClick = { authViewModel.signInWithGoogle() },
                onPhoneSignInClick = { phoneNumber ->
                    activity?.let { authViewModel.signInWithPhone(phoneNumber, it) }
                },
                onOtpSubmit = { otp -> authViewModel.verifyOtp(otp) },
                onCancelPhoneSignIn = { authViewModel.cancelPhoneSignIn() },
                onClearError = { authViewModel.clearError() }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onChatClick = {
                    chatViewModel.startNewConversation()
                    navController.navigate(Screen.Chat.route)
                },
                onPlantDiagnosisClick = { navController.navigate(Screen.PlantDiagnosis.route) },
                onHistoryClick = { navController.navigate(Screen.History.route) },
                onMandiPricesClick = { navController.navigate(Screen.MandiPrices.route) },
                onWeatherClick = { navController.navigate(Screen.Weather.route) },
                onOffersClick = { navController.navigate(Screen.Offers.route) },
                onCarbonCreditsClick = { navController.navigate(Screen.CarbonCredits.route) },
                onKnowledgeBaseClick = { navController.navigate(Screen.KnowledgeBase.route) },
                onMandiSettingsClick = { navController.navigate(Screen.MandiSettings.route) },
                onSignOut = { authViewModel.signOut() }
            )
        }

        composable(Screen.Chat.route) {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            // Cache string resources using stringResource composable
            val exportInProgressText = stringResource(R.string.export_in_progress)
            val exportErrorText = stringResource(R.string.export_error)

            ChatScreen(
                messages = messages,
                onSendMessage = { message, imageUri -> chatViewModel.sendMessage(message, imageUri) },
                onHomeClick = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onNewChat = { chatViewModel.startNewConversation() },
                onPlantDiagnosisClick = { navController.navigate(Screen.PlantDiagnosis.route) },
                onHistoryClick = { navController.navigate(Screen.History.route) },
                onMandiPricesClick = { navController.navigate(Screen.MandiPrices.route) },
                onWeatherClick = { navController.navigate(Screen.Weather.route) },
                onOffersClick = { navController.navigate(Screen.Offers.route) },
                onCarbonCreditsClick = { navController.navigate(Screen.CarbonCredits.route) },
                onKnowledgeBaseClick = { navController.navigate(Screen.KnowledgeBase.route) },
                onMandiSettingsClick = { navController.navigate(Screen.MandiSettings.route) },
                onSignOut = { authViewModel.signOut() },
                onExportConversation = {
                    val conversation = chatViewModel.getCurrentConversationForExport()
                    if (conversation != null) {
                        scope.launch {
                            Toast.makeText(context, exportInProgressText, Toast.LENGTH_SHORT).show()

                            val pdfFile = withContext(Dispatchers.IO) {
                                PdfGenerator.generatePdf(context, conversation)
                            }

                            if (pdfFile != null) {
                                try {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "com.maswadkar.developers.androidify.fileprovider",
                                        pdfFile
                                    )
                                    // Open PDF directly in viewer
                                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/pdf")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(viewIntent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, exportErrorText, Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, exportErrorText, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        }

        composable(Screen.Weather.route) {
            WeatherScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.PlantDiagnosis.route) {
            val diagnosisPrompt = stringResource(R.string.plant_diagnosis_prompt)

            PlantDiagnosisScreen(
                onBackClick = { navController.popBackStack() },
                onAnalyze = { imageUri ->
                    // Start a new conversation for the diagnosis
                    chatViewModel.startNewConversation()
                    // Send the image with the diagnosis prompt
                    chatViewModel.sendMessage(diagnosisPrompt, imageUri)
                    // Navigate to chat to see the result
                    navController.navigate(Screen.Chat.route) {
                        // Pop up to Home to avoid building up a large back stack
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                conversations = conversations,
                onConversationClick = { conversation ->
                    chatViewModel.loadConversation(conversation.id)
                    navController.navigate(Screen.Chat.route) {
                        // Pop up to Home to avoid building up a large back stack
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onDeleteConversation = { conversation ->
                    chatViewModel.deleteConversation(conversation.id)
                },
                onRefresh = {
                    // Force refresh conversations from Firebase
                    chatViewModel.refreshConversations()
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.MandiPrices.route) {
            MandiPricesScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Offers.route) {
            OffersScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.MandiSettings.route) {
            MandiPreferencesScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.CarbonCredits.route) {
            CarbonCreditsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.KnowledgeBase.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "krishiai://app/knowledge_base" },
                navDeepLink { uriPattern = "https://maswadkar.com/app/knowledge_base" }
            )
        ) {
            KnowledgeBaseScreen(
                onBackClick = { navController.popBackStack() },
                onCropClick = { cropId, cropName ->
                    navController.navigate(Screen.KnowledgeDocuments.createRoute(cropId, cropName))
                }
            )
        }

        composable(
            route = Screen.KnowledgeDocuments.route,
            arguments = listOf(
                navArgument("cropId") { type = NavType.StringType },
                navArgument("cropName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val cropId = backStackEntry.arguments?.getString("cropId") ?: ""
            val cropName = backStackEntry.arguments?.getString("cropName")?.let {
                Screen.KnowledgeDocuments.decodeCropName(it)
            } ?: ""
            KnowledgeDocumentsScreen(
                cropId = cropId,
                cropName = cropName,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
