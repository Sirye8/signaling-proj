package com.guc_proj.signaling_proj

data class PaymentCard(
    val cardId: String? = null,
    val cardNumber: String? = null, // Store last 4 digits or masked for security in real app
    val cardHolder: String? = null,
    val expiryDate: String? = null
)