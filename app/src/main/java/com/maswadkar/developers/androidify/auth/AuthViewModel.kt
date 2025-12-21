package com.maswadkar.developers.androidify.auth

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AuthViewModel"
    }

    private lateinit var authRepository: AuthRepository

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Store verification ID for OTP verification
    private var storedVerificationId: String? = null
    private var storedPhoneNumber: String? = null

    fun initRepository(authRepository: AuthRepository) {
        this.authRepository = authRepository
        checkAuthState()
    }

    private fun checkAuthState() {
        val currentUser = authRepository.currentUser
        _authState.value = if (currentUser != null) {
            AuthState.Authenticated(currentUser)
        } else {
            AuthState.Unauthenticated
        }
    }

    fun signInWithGoogle() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            authRepository.signInWithGoogle()
                .onSuccess { user ->
                    _authState.value = AuthState.Authenticated(user)
                }
                .onFailure { exception ->
                    _authState.value = AuthState.Error(
                        exception.message ?: "Sign-in failed"
                    )
                }
        }
    }

    // Phone Authentication Methods

    fun signInWithPhone(phoneNumber: String, activity: Activity) {
        _authState.value = AuthState.Loading
        storedPhoneNumber = phoneNumber

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Auto-verification (instant verification or auto-retrieval)
                Log.d(TAG, "Phone verification completed automatically")
                viewModelScope.launch {
                    authRepository.signInWithPhoneCredential(credential)
                        .onSuccess { user ->
                            _authState.value = AuthState.Authenticated(user)
                        }
                        .onFailure { exception ->
                            _authState.value = AuthState.Error(
                                exception.message ?: "Phone sign-in failed"
                            )
                        }
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.e(TAG, "Phone verification failed: ${e.message}")
                _authState.value = AuthState.Error(
                    e.message ?: "Phone verification failed"
                )
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                Log.d(TAG, "OTP code sent to $phoneNumber")
                storedVerificationId = verificationId
                _authState.value = AuthState.AwaitingOtp(verificationId, phoneNumber)
            }
        }

        authRepository.sendVerificationCode(phoneNumber, activity, callbacks)
    }

    fun verifyOtp(code: String) {
        val verificationId = storedVerificationId
        if (verificationId == null) {
            _authState.value = AuthState.Error("Verification ID not found. Please try again.")
            return
        }

        _authState.value = AuthState.OtpVerificationInProgress

        viewModelScope.launch {
            authRepository.verifyOtpCode(verificationId, code)
                .onSuccess { user ->
                    storedVerificationId = null
                    storedPhoneNumber = null
                    _authState.value = AuthState.Authenticated(user)
                }
                .onFailure { exception ->
                    _authState.value = AuthState.Error(
                        exception.message ?: "OTP verification failed"
                    )
                }
        }
    }

    fun cancelPhoneSignIn() {
        storedVerificationId = null
        storedPhoneNumber = null
        _authState.value = AuthState.Unauthenticated
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Unauthenticated
        }
    }
}

