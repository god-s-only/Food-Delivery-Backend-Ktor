package com.codewithfk.routs

import com.codewithfk.services.SchoolService
import com.codewithfk.utils.respondError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.schoolRoutes() {
    route("/schools") {

        // Add a new school (owner only)
        authenticate {
            post {
                val params = call.receive<Map<String, String>>()
                val ownerId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respondError("Unauthorized.", HttpStatusCode.Unauthorized)

                val name = params["name"] ?: return@post call.respondError("School name is required.", HttpStatusCode.BadRequest)
                val address = params["address"] ?: return@post call.respondError("School address is required.", HttpStatusCode.BadRequest)
                val latitude = params["latitude"]?.toDoubleOrNull() ?: return@post call.respondError("Latitude is required.", HttpStatusCode.BadRequest)
                val longitude = params["longitude"]?.toDoubleOrNull() ?: return@post call.respondError("Longitude is required.", HttpStatusCode.BadRequest)

                val schoolId = SchoolService.addSchool(UUID.fromString(ownerId), name, address, latitude, longitude)
                call.respond(mapOf("id" to schoolId.toString(), "message" to "School added successfully"))
            }
        }

        // Fetch nearby schools
        get {
            val lat = call.request.queryParameters["lat"]?.toDoubleOrNull()
            val lon = call.request.queryParameters["lon"]?.toDoubleOrNull()

            if (lat == null || lon == null) {
                call.respondError("Latitude and longitude are required.", HttpStatusCode.BadRequest)
                return@get
            }
            val schools = SchoolService.getNearbySchools(lat, lon)
            call.respond(HttpStatusCode.OK, mapOf("data" to schools))
        }

        // Get a specific school
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respondError("School ID is required.", HttpStatusCode.BadRequest)
            val school = SchoolService.getSchoolById(UUID.fromString(id))
                ?: return@get call.respondError("School not found.", HttpStatusCode.NotFound)
            call.respond(HttpStatusCode.OK, mapOf("data" to school))
        }
    }
}
