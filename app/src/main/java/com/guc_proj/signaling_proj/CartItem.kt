package com.guc_proj.signaling_proj

@kotlinx.parcelize.Parcelize
data class CartItem(
    val product: Product,
    var quantityInCart: Int = 1
) : android.os.Parcelable