package com.guc_proj.signaling_proj.buyer

import com.guc_proj.signaling_proj.CartItem
import com.guc_proj.signaling_proj.Product

object CartManager {
    private val cartItems = mutableMapOf<String, CartItem>()

    fun addItem(product: Product) {
        val productId = product.productId ?: return

        if (cartItems.isNotEmpty() && cartItems.values.first().product?.sellerId != product.sellerId) {
            cartItems.clear()
        }

        val cartItem = cartItems[productId]
        if (cartItem == null) {
            // Add new item
            cartItems[productId] = CartItem(product, 1)
        } else {
            if (cartItem.quantityInCart < (product.quantity ?: 1)) {
                cartItem.quantityInCart++
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