package com.codewithfk.utils

import com.stripe.Stripe
import com.stripe.model.PaymentIntent
import com.stripe.param.PaymentIntentCreateParams

object StripeUtils {
    init {
        Stripe.apiKey = System.getenv("STRIPE_API_KEY") // Use environment variable or application.conf
    }

    fun createPaymentIntent(amount: Long, currency: String = "usd"): String {
        val params = PaymentIntentCreateParams.builder()
            .setAmount(amount) // Amount in cents
            .setCurrency(currency)
            .build()
        val paymentIntent = PaymentIntent.create(params)
        return paymentIntent.id
    }
}