package com.codewithfk.configs


object FacebookAuthConfig {
    val clientId = "your client id"
    val clientSecret = "your secret"
    const val redirectUri = "http://localhost:8080/auth/facebook/callback"
    const val authorizeUrl = "https://www.facebook.com/v14.0/dialog/oauth"
    const val tokenUrl = "https://graph.facebook.com/v14.0/oauth/access_token"
}