package com.maswadkar.developers.androidify.auth

import com.google.firebase.auth.FirebaseUser

sealed class AuthState {
    data object Loading : AuthState()
    data class Authenticated(val user: FirebaseUser) : AuthState()
    data object Unauthenticated : AuthState()
    data class Error(val message: String) : AuthState()

    // Phone authentication states
    data class AwaitingOtp(val verificationId: String, val phoneNumber: String) : AuthState()
    data object OtpVerificationInProgress : AuthState()
}
