package com.maswadkar.developers.androidify

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.ads.MobileAds
import com.google.firebase.auth.FirebaseAuth
import com.maswadkar.developers.androidify.auth.AuthRepository
import com.maswadkar.developers.androidify.auth.AuthState
import com.maswadkar.developers.androidify.auth.AuthViewModel
import com.maswadkar.developers.androidify.ui.navigation.AppNavigation
import com.maswadkar.developers.androidify.ui.navigation.Screen
import com.maswadkar.developers.androidify.ui.theme.KrishiMitraTheme
import com.maswadkar.developers.androidify.util.AppConfigManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private lateinit var authRepository: AuthRepository
    private var navController: NavHostController? = null

    // Permission launcher for POST_NOTIFICATIONS (Android 13+)
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted - notifications will work
        } else {
            // Permission denied - notifications won't show on Android 13+
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Mobile Ads SDK
        MobileAds.initialize(this) {}

        // Initialize Remote Config for app configuration
        AppConfigManager.initialize()

        // Request notification permission for Android 13+
        requestNotificationPermission()

        // Initialize auth repository with activity context
        authRepository = AuthRepository(this)
        authViewModel.initRepository(authRepository)

        // Determine start destination based on auth state
        val isLoggedIn = FirebaseAuth.getInstance().currentUser != null
        val startDestination = if (isLoggedIn) Screen.Home.route else Screen.Login.route

        // Handle intent for loading specific conversation
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
        if (conversationId != null && isLoggedIn) {
            chatViewModel.loadConversation(conversationId)
        }

        setContent {
            KrishiMitraTheme {
                val navControllerLocal = rememberNavController()
                navController = navControllerLocal
                val authState by authViewModel.authState.collectAsState()

                // Handle deep link navigation after initial composition
                LaunchedEffect(navControllerLocal) {
                    intent?.data?.let { uri ->
                        // Delay to ensure NavHost and auth state handling is ready
                        kotlinx.coroutines.delay(500)
                        // Extract route from path (works for both krishiai://app/X and https://maswadkar.com/app/X)
                        val path = uri.path ?: return@let
                        val route = path.removePrefix("/app/").removePrefix("/")
                        if (isLoggedIn && route.isNotEmpty()) {
                            navControllerLocal.navigate(route) {
                                launchSingleTop = true
                            }
                        }
                    }
                }

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
                    navController = navControllerLocal,
                    authViewModel = authViewModel,
                    chatViewModel = chatViewModel,
                    startDestination = startDestination,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle deep link when app is already running
        intent.data?.let { uri ->
            val path = uri.path ?: return@let
            val route = path.removePrefix("/app/").removePrefix("/")
            if (route.isNotEmpty()) {
                navController?.navigate(route) {
                    launchSingleTop = true
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Use lifecycleScope to save the conversation without blocking the main thread
        // The coroutine uses NonCancellable context internally to ensure save completes
        lifecycleScope.launch {
            chatViewModel.saveCurrentConversationSync()
        }
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                }
                else -> {
                    // Request permission
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}
