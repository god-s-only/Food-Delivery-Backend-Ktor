package com.codewithfk.routs

import com.codewithfk.services.CategoryService
import com.codewithfk.utils.respondError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

fun Route.categoryRoutes() {
    route("/categories") {

        /**
         * Get all categories (open to everyone).
         */
        get {
            val categories = CategoryService.getAllCategories()
            call.respond(mapOf("data" to categories))
        }

        /**
         * Add a new category (admin-only functionality).
         */
        authenticate {
            post {
                val params = call.receive<Map<String, String>>()
                val name = params["name"] ?: return@post call.respondError(
                    "Name is required",
                    status = HttpStatusCode.BadRequest
                )
                val categoryId = CategoryService.addCategory(name)
                call.respond(mapOf("id" to categoryId.toString(), "message" to "Category added successfully"))
            }
        }
    }
}