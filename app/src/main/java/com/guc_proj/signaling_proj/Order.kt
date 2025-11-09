package com.guc_proj.signaling_proj

data class Order(
    val orderId: String? = null,
    val buyerId: String? = null,
    val sellerId: String? = null,
    val items: Map<String, CartItem>? = null,
    val totalPrice: Double? = null,
    val status: String? = "Pending" // e.g., Pending, Accepted, Rejected
)