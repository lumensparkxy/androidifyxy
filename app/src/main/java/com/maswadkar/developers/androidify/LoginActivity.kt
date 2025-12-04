package com.maswadkar.developers.androidify

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.maswadkar.developers.androidify.auth.AuthRepository
import com.maswadkar.developers.androidify.auth.AuthState
import com.maswadkar.developers.androidify.auth.AuthViewModel
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private lateinit var authRepository: AuthRepository

    private lateinit var btnGoogleSignIn: MaterialButton
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.loginRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize auth repository with this activity context
        authRepository = AuthRepository(this)
        authViewModel.initRepository(authRepository)

        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn)
        progressBar = findViewById(R.id.progressBar)

        btnGoogleSignIn.setOnClickListener {
            authViewModel.signInWithGoogle()
        }

        observeAuthState()
    }

    private fun observeAuthState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.authState.collect { state ->
                    when (state) {
                        is AuthState.Loading -> {
                            btnGoogleSignIn.visibility = View.INVISIBLE
                            progressBar.visibility = View.VISIBLE
                        }
                        is AuthState.Authenticated -> {
                            navigateToMain()
                        }
                        is AuthState.Unauthenticated -> {
                            btnGoogleSignIn.visibility = View.VISIBLE
                            progressBar.visibility = View.GONE
                        }
                        is AuthState.Error -> {
                            btnGoogleSignIn.visibility = View.VISIBLE
                            progressBar.visibility = View.GONE
                            Toast.makeText(
                                this@LoginActivity,
                                state.message,
                                Toast.LENGTH_LONG
                            ).show()
                            authViewModel.clearError()
                        }
                    }
                }
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

