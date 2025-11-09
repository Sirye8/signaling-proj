package com.guc_proj.signaling_proj

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Product(
    val productId: String? = null,
    val sellerId: String? = null,
    val name: String? = null,
    val price: Double? = null,
    val quantity: Int? = null,
    val photoUrl: String? = null
) : Parcelable