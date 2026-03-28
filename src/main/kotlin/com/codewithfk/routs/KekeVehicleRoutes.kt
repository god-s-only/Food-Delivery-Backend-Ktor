package com.codewithfk.routs

import com.codewithfk.model.KekeVehicle
import com.codewithfk.services.KekeVehicleService
import com.codewithfk.utils.respondError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.kekeVehicleRoutes() {
    route("/schools/{id}/keke-vehicles") {
        /**
         * Fetch all keke vehicles for a school
         */
        get {
            val schoolId = call.parameters["id"] ?: return@get call.respondError(
                "School ID is required.", HttpStatusCode.BadRequest
            )
            val kekeVehicles = KekeVehicleService.getKekeVehiclesBySchool(UUID.fromString(schoolId))
            call.respond(mapOf("kekeVehicles" to kekeVehicles))
        }

        /**
         * Add a new keke vehicle to a school
         */
        post {
            val schoolId = call.parameters["id"] ?: return@post call.respondError(
                "School ID is required.", HttpStatusCode.BadRequest
            )
            val kekeVehicle = call.receive<KekeVehicle>().copy(schoolId = schoolId)
            val vehicleId = KekeVehicleService.addKekeVehicle(kekeVehicle)
            call.respond(mapOf("id" to vehicleId.toString(), "message" to "Keke vehicle added successfully"))
        }
    }

    route("/keke-vehicles/{vehicleId}") {
        /**
         * Update a keke vehicle
         */
        patch {
            val vehicleId = call.parameters["vehicleId"] ?: return@patch call.respondError(
                "Keke vehicle ID is required.", HttpStatusCode.BadRequest
            )
            val updatedFields = call.receive<Map<String, Any?>>()
            val success = KekeVehicleService.updateKekeVehicle(UUID.fromString(vehicleId), updatedFields)
            if (success) call.respond(mapOf("message" to "Keke vehicle updated successfully"))
            else call.respondError("Keke vehicle not found", HttpStatusCode.NotFound)
        }

        /**
         * Delete a keke vehicle
         */
        delete {
            val vehicleId = call.parameters["vehicleId"] ?: return@delete call.respondError(
                "Keke vehicle ID is required.", HttpStatusCode.BadRequest
            )
            val success = KekeVehicleService.deleteKekeVehicle(UUID.fromString(vehicleId))
            if (success) call.respond(mapOf("message" to "Keke vehicle deleted successfully"))
            else call.respondError("Keke vehicle not found", HttpStatusCode.NotFound)
        }
    }
}
