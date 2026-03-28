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
                        description = it[KekeVehiclesTable.description],
                        price = it[KekeVehiclesTable.price],
                        imageUrl = it[KekeVehiclesTable.imageUrl],
                        arModelUrl = it[KekeVehiclesTable.arModelUrl],
                        createdAt = it[KekeVehiclesTable.createdAt].toString()
                    )
                }
        }
    }

    fun addKekeVehicle(kekeVehicle: KekeVehicle): UUID {
        return transaction {
            KekeVehiclesTable.insert {
                it[this.schoolId] = UUID.fromString(kekeVehicle.schoolId)
                it[this.name] = kekeVehicle.name
                it[this.description] = kekeVehicle.description
                it[this.price] = kekeVehicle.price
                it[this.imageUrl] = kekeVehicle.imageUrl
                it[this.arModelUrl] = kekeVehicle.arModelUrl
            } get KekeVehiclesTable.id
        }
    }

    fun updateKekeVehicle(kekeVehicleId: UUID, updatedFields: Map<String, Any?>): Boolean {
        return transaction {
            KekeVehiclesTable.update({ KekeVehiclesTable.id eq kekeVehicleId }) { row ->
                updatedFields["name"]?.let { row[KekeVehiclesTable.name] = it as String }
                updatedFields["description"]?.let { row[KekeVehiclesTable.description] = it as String }
                updatedFields["price"]?.let { row[KekeVehiclesTable.price] = it as Double }
                updatedFields["imageUrl"]?.let { row[KekeVehiclesTable.imageUrl] = it as String }
                updatedFields["arModelUrl"]?.let { row[KekeVehiclesTable.arModelUrl] = it as String }
            } > 0
        }
    }

    fun deleteKekeVehicle(kekeVehicleId: UUID): Boolean {
        return transaction {
            KekeVehiclesTable.deleteWhere { KekeVehiclesTable.id eq kekeVehicleId } > 0
        }
    }
}
