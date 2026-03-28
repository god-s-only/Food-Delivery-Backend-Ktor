package com.codewithfk.services

import com.codewithfk.database.KekeVehiclesTable
import com.codewithfk.model.KekeVehicle
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object KekeVehicleService {

    fun getKekeVehiclesBySchool(schoolId: UUID): List<KekeVehicle> {
        return transaction {
            KekeVehiclesTable.select { KekeVehiclesTable.schoolId eq schoolId }
                .map {
                    KekeVehicle(
                        id = it[KekeVehiclesTable.id].toString(),
                        schoolId = it[KekeVehiclesTable.schoolId].toString(),
                        name = it[KekeVehiclesTable.name],
                        driverName = it[KekeVehiclesTable.driverName],
                        description = it[KekeVehiclesTable.description],
                        price = it[KekeVehiclesTable.price],
                        imageUrl = it[KekeVehiclesTable.imageUrl],
                        isAvailable = it[KekeVehiclesTable.isAvailable],
                        createdAt = it[KekeVehiclesTable.createdAt].toString()
                    )
                }
        }
    }

    fun addKekeVehicle(vehicle: KekeVehicle): UUID {
        return transaction {
            KekeVehiclesTable.insert {
                it[this.schoolId] = UUID.fromString(vehicle.schoolId)
                it[this.name] = vehicle.name
                it[this.driverName] = vehicle.driverName
                it[this.description] = vehicle.description
                it[this.price] = vehicle.price
                it[this.imageUrl] = vehicle.imageUrl
                it[this.isAvailable] = vehicle.isAvailable
            } get KekeVehiclesTable.id
        }
    }

    fun updateKekeVehicle(vehicleId: UUID, updatedFields: Map<String, Any?>): Boolean {
        return transaction {
            KekeVehiclesTable.update({ KekeVehiclesTable.id eq vehicleId }) { row ->
                updatedFields["name"]?.let { row[KekeVehiclesTable.name] = it as String }
                updatedFields["driverName"]?.let { row[KekeVehiclesTable.driverName] = it as String }
                updatedFields["description"]?.let { row[KekeVehiclesTable.description] = it as String }
                updatedFields["price"]?.let { row[KekeVehiclesTable.price] = it as Double }
                updatedFields["imageUrl"]?.let { row[KekeVehiclesTable.imageUrl] = it as String }
                updatedFields["isAvailable"]?.let { row[KekeVehiclesTable.isAvailable] = it as Boolean }
            } > 0
        }
    }

    fun deleteKekeVehicle(vehicleId: UUID): Boolean {
        return transaction {
            KekeVehiclesTable.deleteWhere { KekeVehiclesTable.id eq vehicleId } > 0
        }
    }
}
