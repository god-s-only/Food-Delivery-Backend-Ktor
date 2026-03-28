package com.codewithfk.model

import kotlinx.serialization.Serializable

@Serializable
data class KekeVehicle(
    val id: String? = null,
    val schoolId: String,
    val name: String,
    val driverName: String,
    val description: String? = null,
    val price: Double,
    val imageUrl: String? = null,
    val isAvailable: Boolean = true,
    val createdAt: String? = null
)
