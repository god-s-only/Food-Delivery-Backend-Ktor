package com.codewithfk.services

import com.codewithfk.database.RestaurantsTable
import com.codewithfk.model.Restaurant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.math.*

object RestaurantService {

    private const val EARTH_RADIUS_KM = 6371.0 // Radius of Earth in kilometers

    /**
     * Calculate distance using Haversine formula.
     */
    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Add a new restaurant.
     */
    fun addRestaurant(
        ownerId: UUID, name: String, address: String, latitude: Double, longitude: Double, categoryId: UUID
    ): UUID {
        return transaction {
            RestaurantsTable.insert {
                it[this.ownerId] = ownerId
                it[this.name] = name
                it[this.address] = address
                it[this.latitude] = latitude
                it[this.longitude] = longitude
                it[this.categoryId] = categoryId
            } get RestaurantsTable.id
        }
    }

    /**
     * Fetch restaurants within 5KM of the given location.
     */
    fun getNearbyRestaurants(lat: Double, lon: Double, categoryId: UUID? = null): List<Restaurant> {
        return transaction {
            val query = if (categoryId != null) {
                RestaurantsTable.select { RestaurantsTable.categoryId eq categoryId }
            } else {
                RestaurantsTable.selectAll()
            }

            query.mapNotNull {
                val distance = haversine(lat, lon, it[RestaurantsTable.latitude], it[RestaurantsTable.longitude])
                if (distance <= 5.0) { // Only include restaurants within 5KM
                    Restaurant(
                        id = it[RestaurantsTable.id].toString(),
                        ownerId = it[RestaurantsTable.ownerId].toString(),
                        name = it[RestaurantsTable.name],
                        address = it[RestaurantsTable.address],
                        categoryId = it[RestaurantsTable.categoryId].toString(),
                        latitude = it[RestaurantsTable.latitude],
                        longitude = it[RestaurantsTable.longitude],
                        createdAt = it[RestaurantsTable.createdAt].toString(),
                        distance = distance,
                        imageUrl = it[RestaurantsTable.imageUrl].toString()
                    )
                } else {
                    null
                }
            }
        }
    }

    /**
     * Fetch all details of a specific restaurant.
     */
    fun getRestaurantById(id: UUID): Restaurant? {
        return transaction {
            RestaurantsTable.select { RestaurantsTable.id eq id }.map {
                Restaurant(
                    id = it[RestaurantsTable.id].toString(),
                    ownerId = it[RestaurantsTable.ownerId].toString(),
                    name = it[RestaurantsTable.name],
                    address = it[RestaurantsTable.address],
                    categoryId = it[RestaurantsTable.categoryId].toString(),
                    latitude = it[RestaurantsTable.latitude],
                    longitude = it[RestaurantsTable.longitude],
                    createdAt = it[RestaurantsTable.createdAt].toString(),
                    distance = null, // Distance not needed here
                    imageUrl = it[RestaurantsTable.imageUrl].toString()
                )
            }.singleOrNull()
        }
    }
}