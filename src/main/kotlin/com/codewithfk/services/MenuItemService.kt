package com.codewithfk.services


import com.codewithfk.database.MenuItemsTable
import com.codewithfk.model.MenuItem
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object MenuItemService {

    fun getMenuItemsByRestaurant(restaurantId: UUID): List<MenuItem> {
        return transaction {
            MenuItemsTable.select { MenuItemsTable.restaurantId eq restaurantId }
                .map {
                    MenuItem(
                        id = it[MenuItemsTable.id].toString(),
                        restaurantId = it[MenuItemsTable.restaurantId].toString(),
                        name = it[MenuItemsTable.name],
                        description = it[MenuItemsTable.description],
                        price = it[MenuItemsTable.price],
                        imageUrl = it[MenuItemsTable.imageUrl],
                        arModelUrl = it[MenuItemsTable.arModelUrl],
                        createdAt = it[MenuItemsTable.createdAt].toString()
                    )
                }
        }
    }

    fun addMenuItem(menuItem: MenuItem): UUID {
        return transaction {
            MenuItemsTable.insert {
                it[this.restaurantId] = UUID.fromString(menuItem.restaurantId)
                it[this.name] = menuItem.name
                it[this.description] = menuItem.description
                it[this.price] = menuItem.price
                it[this.imageUrl] = menuItem.imageUrl
                it[this.arModelUrl] = menuItem.arModelUrl
            } get MenuItemsTable.id
        }
    }

    fun updateMenuItem(itemId: UUID, updatedFields: Map<String, Any?>): Boolean {
        return transaction {
            MenuItemsTable.update({ MenuItemsTable.id eq itemId }) {row->
                updatedFields["name"]?.let { row[MenuItemsTable.name] = it as String }
                updatedFields["description"]?.let { row[MenuItemsTable.description] = it as String }
                updatedFields["price"]?.let { row[MenuItemsTable.price] = it as Double }
                updatedFields["imageUrl"]?.let { row[MenuItemsTable.imageUrl] = it as String }
                updatedFields["arModelUrl"]?.let { row[MenuItemsTable.arModelUrl] = it as String }
            } > 0
        }
    }

    fun deleteMenuItem(itemId: UUID): Boolean {
        return transaction {
            MenuItemsTable.deleteWhere { MenuItemsTable.id eq itemId } > 0
        }
    }
}