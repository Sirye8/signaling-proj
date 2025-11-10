package com.guc_proj.signaling_proj.buyer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.guc_proj.signaling_proj.CartItem
import com.guc_proj.signaling_proj.R
import com.guc_proj.signaling_proj.databinding.ItemCartBinding

class CartAdapter(
    private var cartItems: List<CartItem>,
    private val onQuantityChanged: (CartItem) -> Unit,
    private val onRemove: (CartItem) -> Unit
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    inner class CartViewHolder(val binding: ItemCartBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val binding = ItemCartBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        val cartItem = cartItems[position]
        with(holder.binding) {
            productName.text = cartItem.product.name
            productPrice.text = String.format("$%.2f", cartItem.product.price)
            quantityTextView.text = cartItem.quantityInCart.toString()

            Glide.with(root.context)
                .load(cartItem.product.photoUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .into(productImage)

            removeButton.setOnClickListener {
                onRemove(cartItem)
            }

            decreaseButton.setOnClickListener {
                if (cartItem.quantityInCart > 1) {
                    cartItem.quantityInCart--
                    quantityTextView.text = cartItem.quantityInCart.toString()
                    onQuantityChanged(cartItem)
                } else {
                    onRemove(cartItem)
                }
            }

            increaseButton.setOnClickListener {
                if (cartItem.quantityInCart < (cartItem.product.quantity ?: Int.MAX_VALUE)) {
                    cartItem.quantityInCart++
                    quantityTextView.text = cartItem.quantityInCart.toString()
                    onQuantityChanged(cartItem)
                }
            }
        }
    }

    override fun getItemCount(): Int = cartItems.size

    fun updateItems(newItems: List<CartItem>) {
        cartItems = newItems
        notifyDataSetChanged()
    }
}