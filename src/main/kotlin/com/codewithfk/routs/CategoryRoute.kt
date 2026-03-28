package com.codewithfk.routs

import com.codewithfk.services.RestaurantService
import com.codewithfk.utils.respondError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

import java.util.*

fun Route.restaurantRoutes() {
    route("/restaurants") {

        /**
         * Add a new restaurant (admin/owner-only).
         */
        authenticate {
            post {
                val params = call.receive<Map<String, String>>()
                val ownerId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respondError("Unauthorized.", HttpStatusCode.Unauthorized)

                val name = params["name"] ?: return@post call.respondError(
                    "Restaurant name is required.",
                    HttpStatusCode.BadRequest
                )
                val address = params["address"] ?: return@post call.respondError(
                    "Restaurant address is required.",
                    HttpStatusCode.BadRequest,
                )
                val latitude = params["latitude"]?.toDoubleOrNull() ?: return@post call.respondError(
                    "Latitude is required.",
                    HttpStatusCode.BadRequest,
                )
                val longitude = params["longitude"]?.toDoubleOrNull() ?: return@post call.respondError(
                    "Longitude is required.",
                    HttpStatusCode.BadRequest,
                )
                val categoryId = params["categoryId"]
                    ?: return@post call.respondError("Valid category ID is required.", HttpStatusCode.BadRequest)

                val restaurantId = RestaurantService.addRestaurant(
                    UUID.fromString(ownerId),
                    name,
                    address,
                    latitude,
                    longitude,
                    UUID.fromString(categoryId)
                )
                call.respond(mapOf("id" to restaurantId.toString(), "message" to "Restaurant added successfully"))
            }
        }

        /**
         * Fetch nearby restaurants.
         */
        get {
            val lat = call.request.queryParameters["lat"]?.toDoubleOrNull()
            val lon = call.request.queryParameters["lon"]?.toDoubleOrNull()
            val categoryId: String? = call.request.queryParameters["categoryId"]

            if (lat == null || lon == null) {
                call.respondError("Latitude and longitude are required.", HttpStatusCode.BadRequest)
                return@get
            }
            var uuid: UUID? = null
            categoryId?.let { uuid = UUID.fromString(it) }
            val restaurants = RestaurantService.getNearbyRestaurants(lat, lon, uuid)
            call.respond(HttpStatusCode.OK, mapOf("data" to restaurants))
        }

        /**
         * Get details of a specific restaurant.
         */
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respondError(
                "Restaurant ID is required.", HttpStatusCode.BadRequest
            )
            val restaurant = RestaurantService.getRestaurantById(UUID.fromString(id))
                ?: return@get call.respondError("Restaurant not found.", HttpStatusCode.NotFound)

            call.respond(HttpStatusCode.OK, mapOf("data" to restaurant))
        }
    }
}
