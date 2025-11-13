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
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it() }
    }

    fun addItem(product: Product): AddToCartStatus {
        val productId = product.productId ?: return AddToCartStatus.OUT_OF_STOCK
        val maxQuantity = product.quantity ?: 0

        if (cartItems.isNotEmpty() && cartItems.values.first().product?.sellerId != product.sellerId) {
            cartItems.clear()
        }

        val status: AddToCartStatus
        val cartItem = cartItems[productId]
        if (cartItem == null) {
            if (maxQuantity > 0) {
                cartItems[productId] = CartItem(product, 1)
                status = AddToCartStatus.ADDED
            } else {
                status = AddToCartStatus.OUT_OF_STOCK
            }
        } else {
            if (cartItem.quantityInCart < maxQuantity) {
                cartItem.quantityInCart++
                status = AddToCartStatus.INCREASED
            } else {
                status = AddToCartStatus.LIMIT_REACHED
            }
        }
        notifyListeners()
        return status
    }

    fun decreaseItem(productId: String): Int {
        val cartItem = cartItems[productId] ?: return 0
        cartItem.quantityInCart--

        if (cartItem.quantityInCart <= 0) {
            removeItem(productId)
            return 0
        }
        notifyListeners()
        return cartItem.quantityInCart
    }

    fun getQuantity(productId: String): Int {
        return cartItems[productId]?.quantityInCart ?: 0
    }

    fun removeItem(productId: String) {
        cartItems.remove(productId)
        notifyListeners()
    }

    fun updateQuantity(productId: String, newQuantity: Int) {
        val cartItem = cartItems[productId]
        cartItem?.let {
            if (newQuantity <= 0) {
                removeItem(productId)
            } else if (newQuantity <= (it.product?.quantity ?: newQuantity)) {
                it.quantityInCart = newQuantity
                notifyListeners()
            }
        }
    }

    fun getCartItems(): List<CartItem> = cartItems.values.toList()

    fun getCartTotal(): Double {
        return cartItems.values.sumOf { (it.product?.price ?: 0.0) * it.quantityInCart }
    }

    fun getCartItemCount(): Int {
        return cartItems.values.sumOf { it.quantityInCart }
    }

    fun getSellerId(): String? {
        return cartItems.values.firstOrNull()?.product?.sellerId
    }

    fun getCartItemsMap(): Map<String, CartItem> {
        return cartItems.toMap()
    }

    fun clearCart() {
        cartItems.clear()
        notifyListeners()
    }
}