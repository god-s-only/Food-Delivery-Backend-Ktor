package com.codewithfk.model

import kotlinx.serialization.Serializable

@Serializable
data class MenuItem(
    val id: String? = null,
    val restaurantId: String,
    val name: String,
    val description: String? = null,
    val price: Double,
    val imageUrl: String? = null,
    val arModelUrl: String? = null,
    val createdAt: String? = null
)