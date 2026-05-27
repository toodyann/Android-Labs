package com.example.lab3.auth

data class GoogleUser(
    val id: String,
    val displayName: String,
    val email: String?,
    val photoUrl: String?,
)

