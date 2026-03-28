package com.codewithfk.services

import com.codewithfk.database.*
import com.codewithfk.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*
import org.jetbrains.exposed.sql.DoubleColumnType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.times

object SchoolOwnerService {

    fun getSchoolOrders(ownerId: UUID, status: String? = null): List<Order> {
        return transaction {
            val query = (OrdersTable
                .join(SchoolsTable, JoinType.INNER, OrdersTable.schoolId, SchoolsTable.id)
                .join(UsersTable, JoinType.INNER, OrdersTable.userId, UsersTable.id)
                .join(AddressesTable, JoinType.LEFT, OrdersTable.addressId, AddressesTable.id)
                .select { SchoolsTable.ownerId eq ownerId })

            status?.let { query.andWhere { OrdersTable.status eq status } }

            query.orderBy(OrdersTable.createdAt, SortOrder.DESC).map { row ->
                val orderId = row[OrdersTable.id]

                val items = OrderItemsTable
                    .join(KekeVehiclesTable, JoinType.INNER, OrderItemsTable.kekeVehicleId, KekeVehiclesTable.id)
                    .select { OrderItemsTable.orderId eq orderId }
                    .map { itemRow ->
                        OrderItem(
                            id = itemRow[OrderItemsTable.id].toString(),
                            orderId = orderId.toString(),
                            kekeVehicleId = itemRow[OrderItemsTable.kekeVehicleId].toString(),
                            quantity = itemRow[OrderItemsTable.quantity],
                            kekeVehicleName = itemRow[KekeVehiclesTable.name]
                        )
                    }

                val address = if (row.getOrNull(AddressesTable.id) != null) {
                    Address(
                        id = row[AddressesTable.id].toString(),
                        userId = row[AddressesTable.userId].toString(),
                        addressLine1 = row[AddressesTable.addressLine1],
                        addressLine2 = row[AddressesTable.addressLine2],
                        city = row[AddressesTable.city],
                        state = row[AddressesTable.state],
                        zipCode = row[AddressesTable.zipCode],
                        country = row[AddressesTable.country],
                        latitude = row[AddressesTable.latitude],
                        longitude = row[AddressesTable.longitude]
                    )
                } else null

                Order(
                    id = orderId.toString(),
                    userId = row[OrdersTable.userId].toString(),
                    schoolId = row[OrdersTable.schoolId].toString(),
                    address = address,
                    status = row[OrdersTable.status],
                    paymentStatus = row[OrdersTable.paymentStatus],
                    stripePaymentIntentId = row[OrdersTable.stripePaymentIntentId],
                    totalAmount = row[OrdersTable.totalAmount],
                    items = items,
                    school = School(
                        id = row[SchoolsTable.id].toString(),
                        ownerId = row[SchoolsTable.ownerId].toString(),
                        name = row[SchoolsTable.name],
                        address = row[SchoolsTable.address],
                        imageUrl = row[SchoolsTable.imageUrl] ?: "",
                        latitude = row[SchoolsTable.latitude],
                        longitude = row[SchoolsTable.longitude],
                        createdAt = row[SchoolsTable.createdAt].toString()
                    ),
                    createdAt = row[OrdersTable.createdAt].toString(),
                    updatedAt = row[OrdersTable.updatedAt].toString(),
                    riderId = row[OrdersTable.riderId]?.toString()
                )
            }
        }
    }

    fun getSchoolStatistics(ownerId: UUID): SchoolStatistics {
        return transaction {
            val schoolId = SchoolsTable
                .select { SchoolsTable.ownerId eq ownerId }
                .map { it[SchoolsTable.id] }
                .firstOrNull() ?: throw IllegalStateException("School not found")

            val orders = OrdersTable.select {
                (OrdersTable.schoolId eq schoolId) and
                (OrdersTable.status inList listOf("DELIVERED", "COMPLETED"))
            }.toList()

            val totalOrders = orders.size
            val totalRevenue = orders.sumOf { it[OrdersTable.totalAmount] }
            val averageOrderValue = if (totalOrders > 0) totalRevenue / totalOrders else 0.0

            val ordersByStatus = OrdersTable
                .slice(OrdersTable.status, OrdersTable.id.count())
                .select { OrdersTable.schoolId eq schoolId }
                .groupBy(OrdersTable.status)
                .associate { it[OrdersTable.status] to it[OrdersTable.id.count()].toInt() }

            val revenueColumn = (OrderItemsTable.quantity.sum().castTo<Double>(DoubleColumnType()) * KekeVehiclesTable.price)
                .alias("total_revenue")

            val popularKekeVehicles = (OrderItemsTable
                .join(KekeVehiclesTable, JoinType.INNER)
                .join(OrdersTable, JoinType.INNER, OrderItemsTable.orderId, OrdersTable.id)
                .slice(KekeVehiclesTable.id, KekeVehiclesTable.name, KekeVehiclesTable.driverName, OrderItemsTable.quantity.sum(), revenueColumn)
                .select { OrdersTable.schoolId eq schoolId }
                .groupBy(KekeVehiclesTable.id, KekeVehiclesTable.name, KekeVehiclesTable.driverName, KekeVehiclesTable.price)
                .orderBy(OrderItemsTable.quantity.sum(), SortOrder.DESC)
                .limit(10)
                .map {
                    PopularKeke(
                        id = it[KekeVehiclesTable.id].toString(),
                        name = it[KekeVehiclesTable.name],
                        driverName = it[KekeVehiclesTable.driverName],
                        totalOrders = it[OrderItemsTable.quantity.sum()]?.toInt() ?: 0,
                        revenue = it[revenueColumn].toDouble() ?: 0.0
                    )
                })

            val thirtyDaysAgo = LocalDateTime.now().minusDays(30)
            val revenueByDay = OrdersTable
                .slice(OrdersTable.createdAt, OrdersTable.totalAmount.sum(), OrdersTable.id.count())
                .select {
                    (OrdersTable.schoolId eq schoolId) and
                    (OrdersTable.createdAt greaterEq thirtyDaysAgo)
                }
                .groupBy(OrdersTable.createdAt)
                .map {
                    DailyRevenue(
                        date = it[OrdersTable.createdAt].toString(),
                        revenue = it[OrdersTable.totalAmount.sum()]?.toDouble() ?: 0.0,
                        orders = it[OrdersTable.id.count()].toInt()
                    )
                }

            SchoolStatistics(
                totalOrders = totalOrders,
                totalRevenue = totalRevenue,
                averageOrderValue = averageOrderValue,
                popularKekeVehicles = popularKekeVehicles,
                ordersByStatus = ordersByStatus,
                revenueByDay = revenueByDay
            )
        }
    }

    fun getSchoolDetails(ownerId: UUID): School? {
        return transaction {
            SchoolsTable.select { SchoolsTable.ownerId eq ownerId }
                .map { row ->
                    School(
                        id = row[SchoolsTable.id].toString(),
                        ownerId = row[SchoolsTable.ownerId].toString(),
                        name = row[SchoolsTable.name],
                        address = row[SchoolsTable.address],
                        imageUrl = row[SchoolsTable.imageUrl] ?: "",
                        latitude = row[SchoolsTable.latitude],
                        longitude = row[SchoolsTable.longitude],
                        createdAt = row[SchoolsTable.createdAt].toString()
                    )
                }.firstOrNull()
        }
    }

    fun updateSchoolProfile(ownerId: UUID, request: UpdateSchoolRequest): Boolean {
        return transaction {
            SchoolsTable.update({ SchoolsTable.ownerId eq ownerId }) {
                request.name?.let { name -> it[SchoolsTable.name] = name }
                request.address?.let { addr -> it[address] = addr }
                request.imageUrl?.let { url -> it[imageUrl] = url }
                request.latitude?.let { lat -> it[latitude] = lat }
                request.longitude?.let { lon -> it[longitude] = lon }
            } > 0
        }
    }
}
