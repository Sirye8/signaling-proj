package com.guc_proj.signaling_proj

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Order(
    val orderId: String? = null,
    val buyerId: String? = null,
    val sellerId: String? = null,
    val deliveryPersonId: String? = null,
    val buyerName: String? = null,
    val sellerName: String? = null,
    val items: Map<String, CartItem>? = null,
    val totalPrice: Double? = null,
    val status: String? = STATUS_PENDING,
    // Delivery Info
    val deliveryType: String = "Pickup",
    val deliveryAddress: String? = null,
    val deliveryFee: Double = 0.0,
    // Order Date
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable {
    companion object {
        const val STATUS_PENDING = "Pending"
        const val STATUS_REJECTED = "Rejected"
        const val STATUS_ACCEPTED = "Accepted"
        const val STATUS_PREPARING = "Preparing"
        const val STATUS_READY_FOR_PICKUP = "Ready for Pickup"
        const val STATUS_OUT_FOR_DELIVERY = "Out for Delivery"
        const val STATUS_DELIVERED = "Delivered"
        const val STATUS_COMPLETED = "Completed"
        const val TYPE_PICKUP = "Pickup"
        const val TYPE_DELIVERY = "Delivery"
    }
}