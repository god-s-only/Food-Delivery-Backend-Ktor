package com.codewithfk.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object UsersTable : Table("users") {
    val id = uuid("id").autoGenerate()
    val name = varchar("name", 255)
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255).nullable()
    val authProvider = varchar("auth_provider", 50) // "google", "facebook", "email"
    val role = varchar("role", 50) // "customer", "rider", "school"
    val createdAt = datetime("created_at").defaultExpression(
        org.jetbrains.exposed.sql.javatime.CurrentTimestamp()
    )
    val fcmToken = varchar("fcm_token", 255).nullable()

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(id)
}

object RiderRejectionsTable : Table("rider_rejections") {
    val id = uuid("id").autoGenerate()
    val riderId = uuid("rider_id").references(UsersTable.id)
    val orderId = uuid("order_id").references(OrdersTable.id)
    val createdAt = datetime("created_at").defaultExpression(org.jetbrains.exposed.sql.javatime.CurrentDateTime())

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(UsersTable.id)
}

object CategoriesTable : Table("categories") {
    val id = uuid("id").autoGenerate()
    val name = varchar("name", 255).uniqueIndex()
    val imageUrl = varchar("image_url", 500).nullable()
    val createdAt = datetime("created_at").defaultExpression(org.jetbrains.exposed.sql.javatime.CurrentTimestamp())
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(id)
}

object SchoolsTable : Table("schools") {
    val id = uuid("id").autoGenerate()
    val ownerId = uuid("owner_id").references(UsersTable.id) // User managing the school
    val name = varchar("name", 255)
    val address = varchar("address", 500)
    val categoryId = uuid("category_id").references(CategoriesTable.id)
    val imageUrl = varchar("image_url", 500).nullable()
    val latitude = double("latitude")  // School's latitude
    val longitude = double("longitude") // School's longitude
    val createdAt = datetime("created_at").defaultExpression(org.jetbrains.exposed.sql.javatime.CurrentTimestamp())
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(id)
}

object KekeVehiclesTable : Table("keke_vehicles") {
    val id = uuid("id").autoGenerate()
    val schoolId = uuid("school_id").references(SchoolsTable.id)
    val name = varchar("name", 255)
    val description = varchar("description", 1000).nullable()
    val price = double("price")
    val imageUrl = varchar("image_url", 500).nullable()
    val arModelUrl = varchar("ar_model_url", 500).nullable()
    val category = varchar("category", 100).nullable()
    val isAvailable = bool("is_available").default(true)
    val createdAt = datetime("created_at").defaultExpression(org.jetbrains.exposed.sql.javatime.CurrentTimestamp())
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(id)
}

object CartTable : Table("cart") {
    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id").references(UsersTable.id)           // Cart belongs to a user
    val schoolId = uuid("school_id").references(SchoolsTable.id)     // School associated with the cart
    val kekeVehicleId = uuid("keke_vehicle_id").references(KekeVehiclesTable.id) // KekeVehicle in cart
    val quantity = integer("quantity")
    val addedAt = datetime("added_at").defaultExpression(org.jetbrains.exposed.sql.javatime.CurrentTimestamp())
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(id)
}

object AddressesTable : Table("addresses") {
    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id").references(UsersTable.id)
    val addressLine1 = varchar("address_line1", 255)
    val addressLine2 = varchar("address_line2", 255).nullable()
    val city = varchar("city", 100)
    val state = varchar("state", 100)
    val zipCode = varchar("zip_code", 20)
    val country = varchar("country", 100)
    val latitude = double("latitude").nullable()
    val longitude = double("longitude").nullable()
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(id)
}

object OrdersTable : Table("orders") {
    val id = uuid("id").autoGenerate().uniqueIndex()
    val userId = uuid("user_id").references(UsersTable.id)
    val schoolId = uuid("school_id").references(SchoolsTable.id)
    val addressId = uuid("address_id").references(AddressesTable.id)
    val status = varchar("status", 50).default("Pending")
    val paymentStatus = varchar("payment_status", 50).default("Pending")
    val stripePaymentIntentId = varchar("stripe_payment_intent_id", 255).nullable()
    val totalAmount = double("total_amount")
    val createdAt = datetime("created_at").defaultExpression(org.jetbrains.exposed.sql.javatime.CurrentTimestamp())
    val updatedAt = datetime("updated_at").defaultExpression(org.jetbrains.exposed.sql.javatime.CurrentTimestamp())
    val riderId = uuid("rider_id").references(UsersTable.id).nullable()

    override val primaryKey = PrimaryKey(id)
}

object OrderItemsTable : Table("order_items") {
    val id = uuid("id").autoGenerate()
    val orderId = uuid("order_id").references(OrdersTable.id)
    val kekeVehicleId = uuid("keke_vehicle_id").references(KekeVehiclesTable.id)
    val quantity = integer("quantity")

    override val primaryKey = PrimaryKey(id)
}
