package com.guc_proj.signaling_proj.buyer

import com.guc_proj.signaling_proj.CartItem
import com.guc_proj.signaling_proj.Product

// We add an enum to report the status of the "addItem" operation
enum class AddToCartStatus {
    ADDED,
    INCREASED,
    LIMIT_REACHED,
    OUT_OF_STOCK
}

object CartManager {
    private val cartItems = mutableMapOf<String, CartItem>()

    fun addItem(product: Product): AddToCartStatus { // Modified to return a status
        val productId = product.productId ?: return AddToCartStatus.OUT_OF_STOCK // Cannot add item without ID

        // This is the maximum quantity the seller has in stock
        val maxQuantity = product.quantity ?: 0

        if (cartItems.isNotEmpty() && cartItems.values.first().product?.sellerId != product.sellerId) {
            cartItems.clear()
        }

        val cartItem = cartItems[productId]
        if (cartItem == null) {
            // This is a new item for the cart
            return if (maxQuantity > 0) {
                cartItems[productId] = CartItem(product, 1)
                AddToCartStatus.ADDED
            } else {
                // Product is out of stock
                AddToCartStatus.OUT_OF_STOCK
            }
        } else {
            // Item is already in the cart, check if we can increment
            return if (cartItem.quantityInCart < maxQuantity) {
                cartItem.quantityInCart++
                AddToCartStatus.INCREASED
            } else {
                // Cart quantity has already reached the maximum stock
                AddToCartStatus.LIMIT_REACHED
            }
        }
    }

    fun removeItem(productId: String) {
        cartItems.remove(productId)
    }

    fun updateQuantity(productId: String, newQuantity: Int) {
        val cartItem = cartItems[productId]
        cartItem?.let {
            if (newQuantity <= 0) {
                removeItem(productId)
            } else if (newQuantity <= (it.product?.quantity ?: newQuantity)) {
                it.quantityInCart = newQuantity
            }
        }
    }

    fun getCartItems(): List<CartItem> = cartItems.values.toList()

    fun getCartTotal(): Double {
        return cartItems.values.sumOf { (it.product?.price ?: 0.0) * it.quantityInCart }
    }

    fun getSellerId(): String? {
        return cartItems.values.firstOrNull()?.product?.sellerId
    }

    fun getCartItemsMap(): Map<String, CartItem> {
        return cartItems.toMap()
    }

    fun clearCart() {
        cartItems.clear()
    }
}