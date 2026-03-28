package com.codewithfk.services

import com.codewithfk.database.*
import com.codewithfk.model.*
import com.codewithfk.services.OrderService.getOrderAddress
import com.google.maps.DirectionsApi
import com.google.maps.model.TravelMode
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.math.*

object RiderService {
    private const val SEARCH_RADIUS_KM = 6371.0
    private const val EARTH_RADIUS_KM = 6371.0

    fun updateRiderLocation(riderId: UUID, latitude: Double, longitude: Double) {
        transaction {
            val existingLocation = RiderLocationsTable
                .select { RiderLocationsTable.riderId eq riderId }.firstOrNull()

            if (existingLocation != null) {
                RiderLocationsTable.update({ RiderLocationsTable.riderId eq riderId }) {
                    it[this.latitude] = latitude
                    it[this.longitude] = longitude
                    it[this.lastUpdated] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
                }
            } else {
                RiderLocationsTable.insert {
                    it[this.riderId] = riderId
                    it[this.latitude] = latitude
                    it[this.longitude] = longitude
                    it[this.isAvailable] = true
                    it[this.lastUpdated] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
                }
            }
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val originLat = Math.toRadians(lat1)
        val destinationLat = Math.toRadians(lat2)
        val a = sin(dLat / 2).pow(2) + cos(originLat) * cos(destinationLat) * sin(dLon / 2).pow(2)
        return EARTH_RADIUS_KM * 2 * asin(sqrt(a))
    }

    fun findNearbyRiders(schoolLat: Double, schoolLng: Double): List<RiderLocation> {
        return transaction {
            RiderLocationsTable.select { RiderLocationsTable.isAvailable eq true }
                .map {
                    RiderLocation(
                        id = it[RiderLocationsTable.riderId].toString(),
                        latitude = it[RiderLocationsTable.latitude],
                        longitude = it[RiderLocationsTable.longitude],
                        isAvailable = it[RiderLocationsTable.isAvailable],
                        lastUpdated = it[RiderLocationsTable.lastUpdated].toString()
                    )
                }
                .filter { rider ->
                    calculateDistance(schoolLat, schoolLng, rider.latitude, rider.longitude) <= SEARCH_RADIUS_KM
                }
        }
    }

    fun createDeliveryRequest(orderId: UUID): Boolean {
        return transaction {
            try {
                val order = OrdersTable
                    .join(SchoolsTable, JoinType.INNER, OrdersTable.schoolId, SchoolsTable.id)
                    .select { OrdersTable.id eq orderId }
                    .firstOrNull() ?: throw IllegalStateException("Order not found")

                val schoolLat = order[SchoolsTable.latitude]
                val schoolLng = order[SchoolsTable.longitude]
                val nearbyRiders = findNearbyRiders(schoolLat, schoolLng)

                if (nearbyRiders.isEmpty()) return@transaction false

                nearbyRiders.forEach { rider ->
                    DeliveryRequestsTable.insert {
                        it[this.orderId] = orderId
                        it[this.riderId] = UUID.fromString(rider.id)
                        it[this.status] = "PENDING"
                        it[this.createdAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
                    }
                    notifyRider(UUID.fromString(rider.id), orderId)
                }
                true
            } catch (e: Exception) { false }
        }
    }

    private fun notifyRider(riderId: UUID, orderId: UUID) {
        val riderFcmToken = transaction {
            UsersTable.select { UsersTable.id eq riderId }.map { it[UsersTable.fcmToken] }.firstOrNull()
        }
        riderFcmToken?.let { token ->
            FirebaseService.sendNotification(
                token = token,
                title = "New Keke Delivery Request",
                body = "New keke delivery request available near you",
                data = mapOf("type" to "DELIVERY_REQUEST", "orderId" to orderId.toString())
            )
        }
    }

    fun acceptDeliveryRequest(riderId: UUID, orderId: UUID): Boolean {
        return transaction {
            val order = OrdersTable.select {
                (OrdersTable.id eq orderId) and
                (OrdersTable.status eq OrderStatus.READY.name) and
                (OrdersTable.riderId.isNull())
            }.firstOrNull() ?: return@transaction false

            val updated = OrdersTable.update({ OrdersTable.id eq orderId }) {
                it[OrdersTable.riderId] = riderId
                it[OrdersTable.status] = OrderStatus.ASSIGNED.name
                it[OrdersTable.updatedAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
            } > 0

            if (updated) {
                NotificationService.createNotification(
                    userId = order[OrdersTable.userId],
                    title = "Rider Assigned",
                    message = "A rider has been assigned to your keke booking",
                    type = "DELIVERY_STATUS",
                    orderId = orderId
                )
                val schoolOwnerId = SchoolsTable
                    .select { SchoolsTable.id eq order[OrdersTable.schoolId] }
                    .map { it[SchoolsTable.ownerId] }.single()
                NotificationService.createNotification(
                    userId = schoolOwnerId,
                    title = "Rider Assigned",
                    message = "A rider has been assigned to order #${orderId.toString().take(8)}",
                    type = "DELIVERY_STATUS",
                    orderId = orderId
                )
            }
            updated
        }
    }

    fun rejectDeliveryRequest(riderId: UUID, orderId: UUID): Boolean {
        return transaction {
            val orderExists = OrdersTable.select {
                (OrdersTable.id eq orderId) and
                (OrdersTable.status eq OrderStatus.READY.name) and
                (OrdersTable.riderId.isNull())
            }.count() > 0

            if (!orderExists) return@transaction false

            RiderRejectionsTable.insert {
                it[this.riderId] = riderId
                it[this.orderId] = orderId
                it[this.createdAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
            }
            true
        }
    }

    fun getDeliveryPath(riderId: UUID, orderId: UUID): DeliveryPath {
        val order = OrderService.getOrderDetails(orderId)
        val riderLocation = getRiderLocation(riderId)
        val school = SchoolService.getSchoolById(UUID.fromString(order.schoolId))
            ?: throw IllegalStateException("School not found")
        val customerAddress = order.address ?: throw IllegalStateException("Customer address not found")
        val isPickedUp = order.status == OrderStatus.OUT_FOR_DELIVERY.name

        val directions = if (!isPickedUp) {
            DirectionsApi.newRequest(GeocodingService.geoApiContext)
                .mode(TravelMode.DRIVING)
                .origin(com.google.maps.model.LatLng(riderLocation.latitude, riderLocation.longitude))
                .destination(com.google.maps.model.LatLng(customerAddress.latitude!!, customerAddress.longitude!!))
                .waypoints(com.google.maps.model.LatLng(school.latitude, school.longitude))
                .await()
        } else {
            DirectionsApi.newRequest(GeocodingService.geoApiContext)
                .mode(TravelMode.DRIVING)
                .origin(com.google.maps.model.LatLng(riderLocation.latitude, riderLocation.longitude))
                .destination(com.google.maps.model.LatLng(customerAddress.latitude!!, customerAddress.longitude!!))
                .await()
        }

        return DeliveryPath(
            currentLocation = Location(riderLocation.latitude, riderLocation.longitude, "Rider's current location"),
            nextStop = if (!isPickedUp)
                Location(school.latitude, school.longitude, school.address)
            else
                Location(customerAddress.latitude!!, customerAddress.longitude!!, customerAddress.addressLine1),
            finalDestination = Location(customerAddress.latitude!!, customerAddress.longitude!!, customerAddress.addressLine1),
            polyline = directions.routes[0].overviewPolyline.encodedPath,
            estimatedTime = directions.routes[0].legs.sumOf { it.duration.inSeconds.toInt() } / 60,
            deliveryPhase = if (isPickedUp) DeliveryPhase.TO_CUSTOMER else DeliveryPhase.TO_SCHOOL
        )
    }

    fun getRiderLocation(riderId: UUID): RiderLocation {
        return transaction {
            RiderLocationsTable
                .select { RiderLocationsTable.riderId eq riderId }
                .orderBy(RiderLocationsTable.lastUpdated, SortOrder.DESC)
                .limit(1)
                .map {
                    RiderLocation(
                        id = it[RiderLocationsTable.riderId].toString(),
                        latitude = it[RiderLocationsTable.latitude],
                        longitude = it[RiderLocationsTable.longitude],
                        isAvailable = it[RiderLocationsTable.isAvailable],
                        lastUpdated = it[RiderLocationsTable.lastUpdated].toString()
                    )
                }.firstOrNull() ?: throw IllegalStateException("Rider location not found")
        }
    }

    fun getAvailableDeliveries(riderId: UUID): List<AvailableDelivery> {
        return transaction {
            val riderLocation = getRiderLocation(riderId)
            val rejectedOrderIds = RiderRejectionsTable
                .select { RiderRejectionsTable.riderId eq riderId }
                .map { it[RiderRejectionsTable.orderId] }.toSet()

            (OrdersTable
                .join(SchoolsTable, JoinType.INNER, OrdersTable.schoolId, SchoolsTable.id)
                .select {
                    (OrdersTable.status eq OrderStatus.READY.name) and (OrdersTable.riderId.isNull())
                })
                .mapNotNull { row ->
                    val orderId = row[OrdersTable.id]
                    if (orderId in rejectedOrderIds) return@mapNotNull null

                    val distance = calculateDistance(
                        riderLocation.latitude, riderLocation.longitude,
                        row[SchoolsTable.latitude], row[SchoolsTable.longitude]
                    )

                    if (distance <= SEARCH_RADIUS_KM) {
                        val customerAddress = getOrderAddress(row[OrdersTable.addressId])
                        AvailableDelivery(
                            orderId = orderId.toString(),
                            schoolName = row[SchoolsTable.name],
                            schoolAddress = row[SchoolsTable.address],
                            customerAddress = customerAddress?.addressLine1 ?: "",
                            orderAmount = row[OrdersTable.totalAmount],
                            estimatedDistance = distance,
                            estimatedEarning = calculateEarnings(distance, row[OrdersTable.totalAmount]),
                            createdAt = row[OrdersTable.createdAt].toString()
                        )
                    } else null
                }
        }
    }

    fun updateDeliveryStatus(riderId: UUID, orderId: UUID, statusUpdate: DeliveryStatusUpdate): Boolean {
        return transaction {
            val order = OrdersTable
                .select { (OrdersTable.id eq orderId) and (OrdersTable.riderId eq riderId) }
                .firstOrNull() ?: throw IllegalStateException("Order not found or unauthorized")

            val updated = OrdersTable.update({ OrdersTable.id eq orderId }) {
                it[status] = when (statusUpdate.status) {
                    "PICKED_UP" -> OrderStatus.OUT_FOR_DELIVERY.name
                    "DELIVERED" -> OrderStatus.DELIVERED.name
                    "FAILED" -> OrderStatus.DELIVERY_FAILED.name
                    else -> throw IllegalArgumentException("Invalid status: ${statusUpdate.status}")
                }
            } > 0

            if (updated) {
                val message = when (statusUpdate.status) {
                    "PICKED_UP" -> "Your keke has been picked up and is on the way"
                    "DELIVERED" -> "Your keke has been delivered successfully"
                    "FAILED" -> "Keke delivery failed: ${statusUpdate.reason}"
                    else -> throw IllegalArgumentException("Invalid status")
                }
                NotificationService.createNotification(
                    userId = order[OrdersTable.userId],
                    title = "Delivery Update",
                    message = message,
                    type = "DELIVERY_STATUS",
                    orderId = orderId
                )
            }
            updated
        }
    }

    fun getActiveDeliveries(riderId: UUID): List<RiderDelivery> {
        return transaction {
            (OrdersTable
                .join(SchoolsTable, JoinType.INNER, OrdersTable.schoolId, SchoolsTable.id)
                .select {
                    (OrdersTable.riderId eq riderId) and
                    (OrdersTable.status inList listOf(OrderStatus.ASSIGNED.name, OrderStatus.OUT_FOR_DELIVERY.name))
                })
                .orderBy(OrdersTable.updatedAt, SortOrder.DESC)
                .map { row ->
                    val orderId = row[OrdersTable.id]
                    val customerAddress = getOrderAddress(row[OrdersTable.addressId])

                    val items = OrderItemsTable
                        .join(KekeVehiclesTable, JoinType.INNER, OrderItemsTable.kekeVehicleId, KekeVehiclesTable.id)
                        .select { OrderItemsTable.orderId eq orderId }
                        .map { itemRow ->
                            OrderItemDetail(
                                id = itemRow[OrderItemsTable.id].toString(),
                                kekeVehicleName = itemRow[KekeVehiclesTable.name],
                                driverName = itemRow[KekeVehiclesTable.driverName],
                                quantity = itemRow[OrderItemsTable.quantity],
                                price = itemRow[KekeVehiclesTable.price]
                            )
                        }

                    RiderDelivery(
                        orderId = orderId.toString(),
                        status = row[OrdersTable.status],
                        school = SchoolDetail(
                            id = row[SchoolsTable.id].toString(),
                            name = row[SchoolsTable.name],
                            address = row[SchoolsTable.address],
                            latitude = row[SchoolsTable.latitude],
                            longitude = row[SchoolsTable.longitude],
                            imageUrl = row[SchoolsTable.imageUrl] ?: ""
                        ),
                        customer = CustomerAddress(
                            addressLine1 = customerAddress?.addressLine1 ?: "",
                            addressLine2 = customerAddress?.addressLine2,
                            city = customerAddress?.city ?: "",
                            state = customerAddress?.state,
                            zipCode = customerAddress?.zipCode ?: "",
                            latitude = customerAddress?.latitude ?: 0.0,
                            longitude = customerAddress?.longitude ?: 0.0
                        ),
                        items = items,
                        totalAmount = row[OrdersTable.totalAmount],
                        estimatedEarning = calculateEarnings(
                            calculateDistance(
                                row[SchoolsTable.latitude], row[SchoolsTable.longitude],
                                customerAddress?.latitude ?: 0.0, customerAddress?.longitude ?: 0.0
                            ),
                            row[OrdersTable.totalAmount]
                        ),
                        createdAt = row[OrdersTable.createdAt].toString(),
                        updatedAt = row[OrdersTable.updatedAt].toString()
                    )
                }
        }
    }

    private fun calculateEarnings(distance: Double, orderAmount: Double): Double {
        return 2.0 + (distance * 0.5) + (orderAmount * 0.05)
    }
}
