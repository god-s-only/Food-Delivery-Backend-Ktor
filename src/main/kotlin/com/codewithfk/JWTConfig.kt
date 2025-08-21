package com.codewithfk

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.*

object JwtConfig {
    private const val secret = "your_secret_key"
    private const val issuer = "ktor.io"
    private const val audience = "ktor-audience"
    private const val validityInMs = 36_000_00 * 10 // 10 hours

    private val algorithm = Algorithm.HMAC256(secret)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    fun generateToken(userId: String): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withClaim("userId", userId)
        .withExpiresAt(Date(System.currentTimeMillis() + validityInMs))
        .sign(algorithm)
}