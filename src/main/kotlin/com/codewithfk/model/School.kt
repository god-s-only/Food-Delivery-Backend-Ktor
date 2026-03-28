package com.codewithfk.model

import kotlinx.serialization.Serializable

@Serializable
data class School(
    val id: String,
    val ownerId: String,
    val name: String,
    val address: String,
    val imageUrl: String,
    val latitude: Double,
    val longitude: Double,
    val createdAt: String,
    val distance: Double? = null
)
