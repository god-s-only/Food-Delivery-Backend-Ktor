package com.codewithfk.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaceOrderRequest(
    val addressId: String
)

@Serializable
data class Order(
    val id: String,
    val userId: String,
    val schoolId: String,
    val riderId: String?,
    val address: Address?,
    val status: String,
    val paymentStatus: String,
    val stripePaymentIntentId: String?,
    val totalAmount: Double,
    val items: List<OrderItem>? = null,
    val school: School? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class OrderItem(
    val id: String,
    val orderId: String,
    val kekeVehicleId: String,
    val quantity: Int,
    val kekeVehicleName: String?
)

@Serializable
data class AddToCartRequest(
    val schoolId: String,
    val kekeVehicleId: String,
    val quantity: Int
)

enum class OrderStatus {
    PENDING_ACCEPTANCE,
    ACCEPTED,
    PREPARING,
    READY,
    ASSIGNED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    DELIVERY_FAILED,
    REJECTED,
    CANCELLED
}

@Serializable
data class OrderActionRequest(
    val action: String, // "ACCEPT", "REJECT"
    val reason: String? = null
)

@Serializable
data class UpdateOrderStatusRequest(
    val status: String
)

@Serializable
data class SchoolStatistics(
    val totalOrders: Int,
    val totalRevenue: Double,
    val averageOrderValue: Double,
    val popularKekeVehicles: List<PopularItem>,
    val ordersByStatus: Map<String, Int>,
    val revenueByDay: List<DailyRevenue>
)

@Serializable
data class PopularItem(
    val id: String,
    val name: String,
    val totalOrders: Int,
    val revenue: Double
)

@Serializable
data class DailyRevenue(
    val date: String,
    val revenue: Double,
    val orders: Int
)

@Serializable
data class UpdateSchoolRequest(
    val name: String? = null,
    val address: String? = null,
    val imageUrl: String? = null,
    val categoryId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)
