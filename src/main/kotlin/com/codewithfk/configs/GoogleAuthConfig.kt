package com.codewithfk.configs


object GoogleAuthConfig {
    val clientId = "your google client id"
    val clientSecret = " your google client secret"
    const val redirectUri = "http://localhost:8080/auth/google/callback"
    const val authorizeUrl = "https://accounts.google.com/o/oauth2/auth"
    const val tokenUrl = "https://oauth2.googleapis.com/token"
}