package com.maswadkar.developers.androidify.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.maswadkar.developers.androidify.ChatViewModel
import com.maswadkar.developers.androidify.auth.AuthState
import com.maswadkar.developers.androidify.auth.AuthViewModel
import com.maswadkar.developers.androidify.ui.screens.ChatScreen
import com.maswadkar.developers.androidify.ui.screens.HistoryScreen
import com.maswadkar.developers.androidify.ui.screens.LoginScreen
import com.maswadkar.developers.androidify.ui.screens.MandiPreferencesScreen
import com.maswadkar.developers.androidify.ui.screens.MandiPricesScreen

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

    // Handle auth state changes
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> {
                navController.navigate(Screen.Chat.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }
            is AuthState.Unauthenticated -> {
                navController.navigate(Screen.Login.route) {
                    popUpTo(0) { inclusive = true }
                }
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
            LoginScreen(
                isLoading = authState is AuthState.Loading,
                onGoogleSignInClick = { authViewModel.signInWithGoogle() }
            )
        }

        composable(Screen.Chat.route) {
            ChatScreen(
                messages = messages,
                onSendMessage = { message, imageUri -> chatViewModel.sendMessage(message, imageUri) },
                onNewChat = { chatViewModel.startNewConversation() },
                onHistoryClick = { navController.navigate(Screen.History.route) },
                onMandiPricesClick = { navController.navigate(Screen.MandiPrices.route) },
                onMandiSettingsClick = { navController.navigate(Screen.MandiSettings.route) },
                onSignOut = { authViewModel.signOut() }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                conversations = conversations,
                onConversationClick = { conversation ->
                    chatViewModel.loadConversation(conversation.id)
                    navController.popBackStack()
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

        composable(Screen.MandiSettings.route) {
            MandiPreferencesScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

