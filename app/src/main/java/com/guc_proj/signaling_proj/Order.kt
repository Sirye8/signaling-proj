package com.guc_proj.signaling_proj

data class Order(
    val orderId: String? = null,
    val buyerId: String? = null,
    val sellerId: String? = null,
    val buyerName: String? = null,
    val sellerName: String? = null,
    val items: Map<String, CartItem>? = null,
    val totalPrice: Double? = null,
    val status: String? = STATUS_PENDING
) {
    companion object {
        const val STATUS_PENDING = "Pending"
        const val STATUS_REJECTED = "Rejected"
        const val STATUS_ACCEPTED = "Accepted"
        const val STATUS_PREPARING = "Preparing"
        const val STATUS_OUT_FOR_DELIVERY = "Out for Delivery"
        const val STATUS_DELIVERED = "Delivered"
    }
}