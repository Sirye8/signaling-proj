package com.guc_proj.signaling_proj

data class CartItem(
    val product: Product,
    var quantityInCart: Int = 1
)