package com.codewithfk.services

import com.codewithfk.database.CartTable
import com.codewithfk.database.KekeVehiclesTable
import com.codewithfk.model.CartItem
import com.codewithfk.model.KekeVehicle
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object CartService {

    fun getCartItems(userId: UUID): List<CartItem> {
        return transaction {
            (CartTable innerJoin KekeVehiclesTable)
                .select { CartTable.userId eq userId }
                .map {
                    CartItem(
                        id = it[CartTable.id].toString(),
                        userId = it[CartTable.userId].toString(),
                        schoolId = it[CartTable.schoolId].toString(),
                        kekeVehicleId = KekeVehicle(
                            id = it[KekeVehiclesTable.id].toString(),
                            name = it[KekeVehiclesTable.name],
                            description = it[KekeVehiclesTable.description],
                            price = it[KekeVehiclesTable.price],
                            schoolId = it[KekeVehiclesTable.schoolId].toString(),
                            imageUrl = it[KekeVehiclesTable.imageUrl]
                        ),
                        quantity = it[CartTable.quantity],
                        addedAt = it[CartTable.addedAt].toString()
                    )
                }
        }
    }

    fun addToCart(userId: UUID, schoolId: UUID, kekeVehicleId: UUID, quantity: Int): UUID {
        return transaction {
            val existingItem = CartTable.select {
                (CartTable.userId eq userId) and (CartTable.kekeVehicleId eq kekeVehicleId)
            }.singleOrNull()

            if (existingItem != null) {
                CartTable.update({ CartTable.id eq existingItem[CartTable.id] }) {
                    it[CartTable.quantity] = existingItem[CartTable.quantity] + quantity
                }
                UUID.fromString(existingItem[CartTable.id].toString())
            } else {
                CartTable.insert {
                    it[this.userId] = userId
                    it[this.schoolId] = schoolId
                    it[this.kekeVehicleId] = kekeVehicleId
                    it[this.quantity] = quantity
                } get CartTable.id
            }
        }
    }

    fun updateCartItemQuantity(cartItemId: UUID, quantity: Int): Boolean {
        return transaction {
            CartTable.update({ CartTable.id eq cartItemId }) {
                it[this.quantity] = quantity
            } > 0
        }
    }

    fun removeCartItem(cartItemId: UUID): Boolean {
        return transaction {
            CartTable.deleteWhere { CartTable.id eq cartItemId } > 0
        }
    }

    fun clearCart(userId: UUID): Boolean {
        return transaction {
            CartTable.deleteWhere { CartTable.userId eq userId } > 0
        }
    }
}
