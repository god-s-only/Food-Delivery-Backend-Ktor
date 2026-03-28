package com.codewithfk.services

import com.codewithfk.database.*
import com.codewithfk.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object OrderService {

    fun getCheckoutDetails(userId: UUID): CheckoutModel {
        return transaction {
            val cartItems = CartTable.select { CartTable.userId eq userId }

            if (cartItems.empty()) {
                return@transaction CheckoutModel(subTotal = 0.0, totalAmount = 0.0, tax = 0.0, deliveryFee = 0.0)
            }

            val totalAmount = cartItems.sumOf {
                val quantity = it[CartTable.quantity]
                val price = KekeVehiclesTable.select { KekeVehiclesTable.id eq it[CartTable.kekeVehicleId] }
                    .single()[KekeVehiclesTable.price]
                quantity * price
            }

            val tax = totalAmount * 0.1
            val deliveryFee = 1.0
            CheckoutModel(subTotal = totalAmount, totalAmount = totalAmount + tax + deliveryFee, tax = tax, deliveryFee = deliveryFee)
        }
    }

    fun placeOrder(userId: UUID, request: PlaceOrderRequest, paymentIntentId: String? = null): UUID {
        return transaction {
            val address = AddressService.getAddressById(UUID.fromString(request.addressId))
                ?: throw IllegalStateException("Address not found")

            if (address.userId != userId.toString()) {
                throw IllegalStateException("Address does not belong to user")
            }

            val cartItems = CartTable.select { CartTable.userId eq userId }
            if (cartItems.empty()) throw IllegalStateException("Cart is empty")

            val schoolId = cartItems.first()[CartTable.schoolId]
            if (!cartItems.all { it[CartTable.schoolId] == schoolId }) {
                throw IllegalStateException("All items must be from the same school")
            }

            val totalAmount = cartItems.sumOf {
                val quantity = it[CartTable.quantity]
                val price = KekeVehiclesTable.select { KekeVehiclesTable.id eq it[CartTable.kekeVehicleId] }
                    .single()[KekeVehiclesTable.price]
                quantity * price
            }

            val orderId = OrdersTable.insert {
                it[this.userId] = userId
                it[this.schoolId] = schoolId
                it[this.addressId] = UUID.fromString(request.addressId)
                it[this.totalAmount] = totalAmount
                it[this.status] = OrderStatus.PENDING_ACCEPTANCE.name
                it[this.paymentStatus] = if (paymentIntentId != null) "Paid" else "Pending"
                it[this.stripePaymentIntentId] = paymentIntentId
                it[this.riderId] = null
            } get OrdersTable.id

            val schoolOwnerId = SchoolsTable
                .select { SchoolsTable.id eq schoolId }
                .map { it[SchoolsTable.ownerId] }
                .single()

            NotificationService.createNotification(
                userId = schoolOwnerId,
                title = "New Order Received",
                message = "New order #${orderId.toString().take(8)} worth ₦${totalAmount} is waiting for acceptance",
                type = "order",
                orderId = orderId
            )

            cartItems.forEach { cartItem ->
                OrderItemsTable.insert {
                    it[this.orderId] = orderId
                    it[this.kekeVehicleId] = cartItem[CartTable.kekeVehicleId]
                    it[this.quantity] = cartItem[CartTable.quantity]
                }
            }

            CartTable.deleteWhere { CartTable.userId eq userId }
            orderId
        }
    }

    fun getOrdersByUser(userId: UUID): List<Order> {
        return transaction {
            (OrdersTable.join(SchoolsTable, JoinType.LEFT, OrdersTable.schoolId, SchoolsTable.id)
                .select { OrdersTable.userId eq userId })
                .map { orderRow ->
                    val orderId = orderRow[OrdersTable.id]
                    Order(
                        id = orderId.toString(),
                        userId = orderRow[OrdersTable.userId].toString(),
                        schoolId = orderRow[OrdersTable.schoolId].toString(),
                        riderId = orderRow[OrdersTable.riderId]?.toString(),
                        address = getOrderAddress(orderRow[OrdersTable.addressId]),
                        status = orderRow[OrdersTable.status],
                        paymentStatus = orderRow[OrdersTable.paymentStatus],
                        stripePaymentIntentId = orderRow[OrdersTable.stripePaymentIntentId],
                        totalAmount = orderRow[OrdersTable.totalAmount],
                        items = getOrderItems(orderId),
                        school = School(
                            id = orderRow[SchoolsTable.id].toString(),
                            ownerId = orderRow[SchoolsTable.ownerId].toString(),
                            name = orderRow[SchoolsTable.name],
                            address = orderRow[SchoolsTable.address],
                            categoryId = orderRow[SchoolsTable.categoryId].toString(),
                            latitude = orderRow[SchoolsTable.latitude],
                            longitude = orderRow[SchoolsTable.longitude],
                            imageUrl = orderRow[SchoolsTable.imageUrl] ?: "",
                            createdAt = orderRow[SchoolsTable.createdAt].toString()
                        ),
                        createdAt = orderRow[OrdersTable.createdAt].toString(),
                        updatedAt = orderRow[OrdersTable.updatedAt].toString()
                    )
                }
        }
    }

    fun getOrderDetails(orderId: UUID): Order {
        return transaction {
            val order = OrdersTable.select { OrdersTable.id eq orderId }
                .firstOrNull() ?: throw IllegalStateException("Order not found")
            Order(
                id = order[OrdersTable.id].toString(),
                userId = order[OrdersTable.userId].toString(),
                schoolId = order[OrdersTable.schoolId].toString(),
                riderId = order[OrdersTable.riderId]?.toString(),
                address = getOrderAddress(order[OrdersTable.addressId]),
                status = order[OrdersTable.status],
                paymentStatus = order[OrdersTable.paymentStatus],
                stripePaymentIntentId = order[OrdersTable.stripePaymentIntentId],
                totalAmount = order[OrdersTable.totalAmount],
                items = getOrderItems(orderId),
                school = getSchoolDetails(order[OrdersTable.schoolId]),
                createdAt = order[OrdersTable.createdAt].toString(),
                updatedAt = order[OrdersTable.updatedAt].toString()
            )
        }
    }

    fun updateOrderStatus(orderId: UUID, status: String): Boolean {
        return transaction {
            val updated = OrdersTable.update({ OrdersTable.id eq orderId }) {
                it[OrdersTable.status] = status
                it[OrdersTable.updatedAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
            } > 0
            if (updated) {
                val userId = OrdersTable.select { OrdersTable.id eq orderId }.map { it[OrdersTable.userId] }.single()
                NotificationService.createNotification(
                    userId = userId,
                    title = "Order Status Updated",
                    message = "Your order #${orderId.toString().take(8)} status has been updated to $status",
                    type = "order",
                    orderId = orderId
                )
            }
            updated
        }
    }

    fun getOrderByPaymentIntentId(paymentIntentId: String): Order? {
        return transaction {
            OrdersTable.join(SchoolsTable, JoinType.LEFT, OrdersTable.schoolId, SchoolsTable.id)
                .select { OrdersTable.stripePaymentIntentId eq paymentIntentId }
                .map { row ->
                    Order(
                        id = row[OrdersTable.id].toString(),
                        userId = row[OrdersTable.userId].toString(),
                        schoolId = row[OrdersTable.schoolId].toString(),
                        riderId = row[OrdersTable.riderId]?.toString(),
                        status = row[OrdersTable.status],
                        paymentStatus = row[OrdersTable.paymentStatus],
                        stripePaymentIntentId = row[OrdersTable.stripePaymentIntentId],
                        totalAmount = row[OrdersTable.totalAmount],
                        createdAt = row[OrdersTable.createdAt].toString(),
                        updatedAt = row[OrdersTable.updatedAt].toString(),
                        address = getOrderAddress(row[OrdersTable.addressId]),
                        items = getOrderItems(row[OrdersTable.id]),
                        school = School(
                            id = row[SchoolsTable.id].toString(),
                            ownerId = row[SchoolsTable.ownerId].toString(),
                            name = row[SchoolsTable.name],
                            address = row[SchoolsTable.address],
                            categoryId = row[SchoolsTable.categoryId].toString(),
                            latitude = row[SchoolsTable.latitude],
                            longitude = row[SchoolsTable.longitude],
                            imageUrl = row[SchoolsTable.imageUrl] ?: "",
                            createdAt = row[SchoolsTable.createdAt].toString()
                        )
                    )
                }.singleOrNull()
        }
    }

    fun handleOrderAction(orderId: UUID, ownerId: UUID, action: String, reason: String? = null): Boolean {
        return transaction {
            val order = OrdersTable
                .join(SchoolsTable, JoinType.INNER, OrdersTable.schoolId, SchoolsTable.id)
                .select { (OrdersTable.id eq orderId) and (SchoolsTable.ownerId eq ownerId) }
                .firstOrNull() ?: throw IllegalStateException("Order not found or unauthorized")

            val currentStatus = order[OrdersTable.status]
            if (currentStatus != OrderStatus.PENDING_ACCEPTANCE.name) {
                throw IllegalStateException("Order cannot be ${action.lowercase()} in status: $currentStatus")
            }

            val newStatus = when (action.uppercase()) {
                "ACCEPT" -> OrderStatus.ACCEPTED
                "REJECT" -> OrderStatus.REJECTED
                else -> throw IllegalArgumentException("Invalid action: $action")
            }

            OrdersTable.update({ OrdersTable.id eq orderId }) { it[status] = newStatus.name }

            val customerId = order[OrdersTable.userId]
            val message = when (newStatus) {
                OrderStatus.ACCEPTED -> "Your order has been accepted and will be prepared soon"
                OrderStatus.REJECTED -> "Your order was rejected${reason?.let { " - $it" } ?: ""}"
                else -> throw IllegalStateException("Unexpected status")
            }
            NotificationService.createNotification(userId = customerId, title = "Order Update", message = message, type = "ORDER_STATUS", orderId = orderId)
            true
        }
    }

    fun getOrderAddress(addressId: UUID?): Address? {
        if (addressId == null) return null
        return transaction {
            AddressesTable.select { AddressesTable.id eq addressId }
                .map { row ->
                    Address(
                        id = row[AddressesTable.id].toString(),
                        userId = row[AddressesTable.userId].toString(),
                        addressLine1 = row[AddressesTable.addressLine1],
                        addressLine2 = row[AddressesTable.addressLine2],
                        city = row[AddressesTable.city],
                        state = row[AddressesTable.state],
                        country = row[AddressesTable.country],
                        zipCode = row[AddressesTable.zipCode],
                        latitude = row[AddressesTable.latitude],
                        longitude = row[AddressesTable.longitude]
                    )
                }.firstOrNull()
        }
    }

    private fun getOrderItems(orderId: UUID): List<OrderItem> {
        return OrderItemsTable.select { OrderItemsTable.orderId eq orderId }
            .map { row ->
                val vehicle = KekeVehiclesTable.select { KekeVehiclesTable.id eq row[OrderItemsTable.kekeVehicleId] }.single()
                OrderItem(
                    id = row[OrderItemsTable.id].toString(),
                    orderId = orderId.toString(),
                    kekeVehicleId = row[OrderItemsTable.kekeVehicleId].toString(),
                    quantity = row[OrderItemsTable.quantity],
                    kekeVehicleName = vehicle[KekeVehiclesTable.name]
                )
            }
    }

    private fun getSchoolDetails(schoolId: UUID): School {
        return transaction {
            SchoolsTable.select { SchoolsTable.id eq schoolId }
                .map { row ->
                    School(
                        id = row[SchoolsTable.id].toString(),
                        ownerId = row[SchoolsTable.ownerId].toString(),
                        name = row[SchoolsTable.name],
                        address = row[SchoolsTable.address],
                        categoryId = row[SchoolsTable.categoryId].toString(),
                        latitude = row[SchoolsTable.latitude],
                        longitude = row[SchoolsTable.longitude],
                        imageUrl = row[SchoolsTable.imageUrl] ?: "",
                        createdAt = row[SchoolsTable.createdAt].toString()
                    )
                }.first()
        }
    }
}
