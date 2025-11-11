package com.guc_proj.signaling_proj.buyer

import com.guc_proj.signaling_proj.CartItem
import com.guc_proj.signaling_proj.Product

enum class AddToCartStatus {
    ADDED,
    INCREASED,
    LIMIT_REACHED,
    OUT_OF_STOCK
}

object CartManager {
    private val cartItems = mutableMapOf<String, CartItem>()

    fun addItem(product: Product): AddToCartStatus {
        val productId = product.productId ?: return AddToCartStatus.OUT_OF_STOCK

        val maxQuantity = product.quantity ?: 0

        if (cartItems.isNotEmpty() && cartItems.values.first().product?.sellerId != product.sellerId) {
            cartItems.clear()
        }

        val cartItem = cartItems[productId]
        if (cartItem == null) {
            return if (maxQuantity > 0) {
                cartItems[productId] = CartItem(product, 1)
                AddToCartStatus.ADDED
            } else {
                AddToCartStatus.OUT_OF_STOCK
            }
        } else {
            return if (cartItem.quantityInCart < maxQuantity) {
                cartItem.quantityInCart++
                AddToCartStatus.INCREASED
            } else {
                AddToCartStatus.LIMIT_REACHED
            }
        }
    }

    /**
     * NEW: Decreases the quantity of an item in the cart.
     * Returns the new quantity, or 0 if the item is removed.
     */
    fun decreaseItem(productId: String): Int {
        val cartItem = cartItems[productId] ?: return 0

        cartItem.quantityInCart--

        if (cartItem.quantityInCart <= 0) {
            removeItem(productId)
            return 0
        }
        return cartItem.quantityInCart
    }

    /**
     * NEW: Gets the current quantity of a specific item in the cart.
     * Returns 0 if the item is not in the cart.
     */
    fun getQuantity(productId: String): Int {
        return cartItems[productId]?.quantityInCart ?: 0
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