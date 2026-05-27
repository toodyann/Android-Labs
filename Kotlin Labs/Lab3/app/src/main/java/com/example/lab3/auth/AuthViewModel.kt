package com.example.lab3.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AuthUiState(
    val user: GoogleUser? = null,
    val error: String? = null,
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val client: GoogleSignInClient by lazy { buildClient() }

    fun signInIntent() = client.signInIntent

    fun signOut() {
        client.signOut().addOnCompleteListener {
            _uiState.update { it.copy(user = null) }
        }
    }

    fun refreshFromLastSignedIn() {
        val account = GoogleSignIn.getLastSignedInAccount(getApplication())
        _uiState.update { it.copy(user = account?.toUser(), error = null) }
    }

    fun onSignInSucceeded(account: GoogleSignInAccount) {
        _uiState.update { it.copy(user = account.toUser(), error = null) }
    }

    fun onSignInFailed(message: String?) {
        _uiState.update { it.copy(error = message ?: "Google sign-in failed") }
    }

    private fun buildClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(getApplication(), gso)
    }

    private fun GoogleSignInAccount.toUser(): GoogleUser =
        GoogleUser(
            id = id.orEmpty(),
            displayName = displayName ?: email ?: "Google user",
            email = email,
            photoUrl = photoUrl?.toString(),
        )
}

