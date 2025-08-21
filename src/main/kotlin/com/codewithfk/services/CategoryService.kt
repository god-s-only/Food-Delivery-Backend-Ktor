package com.codewithfk.services

import com.codewithfk.database.CategoriesTable
import com.codewithfk.model.Category
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

object CategoryService {

    /**
     * Fetch all categories.
     */
    fun getAllCategories(): List<Category> {
        return transaction {
            CategoriesTable.selectAll()
                .map {
                    Category(
                        id = it[CategoriesTable.id].toString(),
                        name = it[CategoriesTable.name],
                        imageUrl = it[CategoriesTable.imageUrl].toString(),
                        createdAt = it[CategoriesTable.createdAt].toString()
                    )
                }
        }
    }

    /**
     * Add a new category (admin-only functionality).
     */
    fun addCategory(name: String): UUID {
        return transaction {
            CategoriesTable.insert {
                it[this.name] = name
            } get CategoriesTable.id
        }
    }
}