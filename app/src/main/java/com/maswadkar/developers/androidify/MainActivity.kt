package com.maswadkar.developers.androidify

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.maswadkar.developers.androidify.auth.AuthRepository
import com.maswadkar.developers.androidify.auth.AuthState
import com.maswadkar.developers.androidify.auth.AuthViewModel
import com.maswadkar.developers.androidify.ui.navigation.AppNavigation
import com.maswadkar.developers.androidify.ui.navigation.Screen
import com.maswadkar.developers.androidify.ui.theme.KrishiMitraTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize auth repository with activity context
        authRepository = AuthRepository(this)
        authViewModel.initRepository(authRepository)

        // Determine start destination based on auth state
        val isLoggedIn = FirebaseAuth.getInstance().currentUser != null
        val startDestination = if (isLoggedIn) Screen.Chat.route else Screen.Login.route

        // Handle intent for loading specific conversation
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
        if (conversationId != null && isLoggedIn) {
            chatViewModel.loadConversation(conversationId)
        }

        setContent {
            KrishiMitraTheme {
                val navController = rememberNavController()
                val authState by authViewModel.authState.collectAsState()

                // Show error toasts
                LaunchedEffect(authState) {
                    if (authState is AuthState.Error) {
                        Toast.makeText(
                            this@MainActivity,
                            (authState as AuthState.Error).message,
                            Toast.LENGTH_LONG
                        ).show()
                        authViewModel.clearError()
                    }
                }

                AppNavigation(
                    navController = navController,
                    authViewModel = authViewModel,
                    chatViewModel = chatViewModel,
                    startDestination = startDestination,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        chatViewModel.saveCurrentConversation()
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }
}

