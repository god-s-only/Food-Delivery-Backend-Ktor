package com.codewithfk.model

import kotlinx.serialization.Serializable

@Serializable
data class CartItem(
    val id: String,
    val userId: String,
    val schoolId: String,
    val kekeVehicleId: KekeVehicle?,
    val quantity: Int,
    val addedAt: String
)
