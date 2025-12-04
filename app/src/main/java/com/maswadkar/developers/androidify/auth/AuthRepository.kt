package com.maswadkar.developers.androidify.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class AuthRepository(private val context: Context) {

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val credentialManager: CredentialManager = CredentialManager.create(context)

    // Web client ID from google-services.json (client_type: 3)
    companion object {
        private const val TAG = "AuthRepository"
        private const val WEB_CLIENT_ID = "989917723212-fnqfnfqcjjh66mqtdkp9t15uv2mf5bu4.apps.googleusercontent.com"
    }

    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    fun isUserSignedIn(): Boolean = currentUser != null

    suspend fun signInWithGoogle(): Result<FirebaseUser> {
        return try {
            // First try with saved credentials
            trySignInWithSavedCredentials()
        } catch (e: NoCredentialException) {
            // No saved credentials, show Google Sign-In button flow
            Log.d(TAG, "No saved credentials, showing sign-in UI")
            trySignInWithGoogleButton()
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Exception("Sign-in was cancelled"))
        } catch (e: GetCredentialException) {
            Log.e(TAG, "GetCredentialException: ${e.message}")
            // Fallback to sign-in button flow
            trySignInWithGoogleButton()
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in error: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun trySignInWithSavedCredentials(): Result<FirebaseUser> {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(WEB_CLIENT_ID)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(
            request = request,
            context = context as android.app.Activity
        )

        return handleSignInResult(result)
    }

    private suspend fun trySignInWithGoogleButton(): Result<FirebaseUser> {
        return try {
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context as android.app.Activity
            )

            handleSignInResult(result)
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Exception("Sign-in was cancelled"))
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in with Google button failed: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun handleSignInResult(result: GetCredentialResponse): Result<FirebaseUser> {
        val credential = result.credential

        return when {
            credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                try {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken

                    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                    val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()

                    authResult.user?.let {
                        Result.success(it)
                    } ?: Result.failure(Exception("Firebase authentication failed"))
                } catch (e: GoogleIdTokenParsingException) {
                    Log.e(TAG, "Token parsing error: ${e.message}")
                    Result.failure(Exception("Invalid Google ID token"))
                }
            }
            else -> {
                Log.e(TAG, "Unexpected credential type: ${credential.type}")
                Result.failure(Exception("Unexpected credential type"))
            }
        }
    }

    suspend fun signOut() {
        firebaseAuth.signOut()
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            // Ignore credential clear errors
        }
    }
}
