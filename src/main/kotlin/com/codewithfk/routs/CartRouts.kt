package com.codewithfk.routs

import com.codewithfk.model.AddToCartRequest
import com.codewithfk.model.CartItem
import com.codewithfk.model.CheckoutModel
import com.codewithfk.model.UpdateCartItemRequest
import com.codewithfk.services.CartService
import com.codewithfk.services.OrderService
import com.codewithfk.utils.respondError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class CartResponse(
    val items: List<CartItem>,
    val checkoutDetails: CheckoutModel
)

fun Route.cartRoutes() {
    route("/cart") {

        // Get all items in cart
        get {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: return@get call.respondError("Unauthorized.", HttpStatusCode.Unauthorized)
            val cartItems = CartService.getCartItems(UUID.fromString(userId))
            val checkoutDetails = OrderService.getCheckoutDetails(UUID.fromString(userId))
            call.respond(CartResponse(items = cartItems, checkoutDetails = checkoutDetails))
        }

        // Add a keke vehicle to cart
        post {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: return@post call.respondError(HttpStatusCode.Unauthorized, "Unauthorized.")
            val request = call.receive<AddToCartRequest>()

            val schoolId = UUID.fromString(request.schoolId)
            val kekeVehicleId = UUID.fromString(request.kekeVehicleId)

            val cartItemId = CartService.addToCart(UUID.fromString(userId), schoolId, kekeVehicleId, request.quantity)
            call.respond(mapOf("id" to cartItemId.toString(), "message" to "Keke vehicle added to cart"))
        }

        // Update quantity
        patch {
            val cartItem = call.receive<UpdateCartItemRequest>()
            if (cartItem.quantity == 0) {
                call.respondError(HttpStatusCode.BadRequest, "Quantity cannot be zero")
                return@patch
            }
            val success = CartService.updateCartItemQuantity(UUID.fromString(cartItem.cartItemId), cartItem.quantity)
            if (success) call.respond(mapOf("message" to "Cart item updated successfully"))
            else call.respondError(HttpStatusCode.NotFound, "Cart item not found")
        }

        // Remove one item
        delete("/{cartItemId}") {
            val cartItemId = call.parameters["cartItemId"]
                ?: return@delete call.respondError(HttpStatusCode.BadRequest, "Cart item ID is required.")
            val success = CartService.removeCartItem(UUID.fromString(cartItemId))
            if (success) call.respond(mapOf("message" to "Cart item removed successfully"))
            else call.respondError(HttpStatusCode.NotFound, "Cart item not found")
        }

        // Clear entire cart
        delete {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: return@delete call.respondError(HttpStatusCode.Unauthorized, "Unauthorized.")
            val success = CartService.clearCart(UUID.fromString(userId))
            if (success) call.respond(mapOf("message" to "Cart cleared successfully"))
            else call.respondError(HttpStatusCode.BadRequest, "Failed to clear the cart")
        }
    }
}
