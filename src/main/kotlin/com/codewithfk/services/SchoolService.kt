package com.codewithfk.services

import com.codewithfk.database.SchoolsTable
import com.codewithfk.model.School
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.math.*

object SchoolService {

    private const val EARTH_RADIUS_KM = 6371.0

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * atan2(sqrt(a), sqrt(1 - a))
    }

    fun addSchool(ownerId: UUID, name: String, address: String, latitude: Double, longitude: Double): UUID {
        return transaction {
            SchoolsTable.insert {
                it[this.ownerId] = ownerId
                it[this.name] = name
                it[this.address] = address
                it[this.latitude] = latitude
                it[this.longitude] = longitude
            } get SchoolsTable.id
        }
    }

    fun getNearbySchools(lat: Double, lon: Double): List<School> {
        return transaction {
            SchoolsTable.selectAll().mapNotNull {
                val distance = haversine(lat, lon, it[SchoolsTable.latitude], it[SchoolsTable.longitude])
                if (distance <= 5.0) {
                    School(
                        id = it[SchoolsTable.id].toString(),
                        ownerId = it[SchoolsTable.ownerId].toString(),
                        name = it[SchoolsTable.name],
                        address = it[SchoolsTable.address],
                        imageUrl = it[SchoolsTable.imageUrl] ?: "",
                        latitude = it[SchoolsTable.latitude],
                        longitude = it[SchoolsTable.longitude],
                        createdAt = it[SchoolsTable.createdAt].toString(),
                        distance = distance
                    )
                } else null
            }
        }
    }

    fun getSchoolById(id: UUID): School? {
        return transaction {
            SchoolsTable.select { SchoolsTable.id eq id }.map {
                School(
                    id = it[SchoolsTable.id].toString(),
                    ownerId = it[SchoolsTable.ownerId].toString(),
                    name = it[SchoolsTable.name],
                    address = it[SchoolsTable.address],
                    imageUrl = it[SchoolsTable.imageUrl] ?: "",
                    latitude = it[SchoolsTable.latitude],
                    longitude = it[SchoolsTable.longitude],
                    createdAt = it[SchoolsTable.createdAt].toString(),
                    distance = null
                )
            }.singleOrNull()
        }
    }
}
