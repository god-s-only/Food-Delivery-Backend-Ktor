package com.codewithfk.model

import kotlinx.serialization.Serializable

@Serializable
data class Category(
    val id: String,
    val name: String,
    val imageUrl: String,
    val createdAt: String? = null
)