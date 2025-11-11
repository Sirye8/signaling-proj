package com.guc_proj.signaling_proj.buyer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.guc_proj.signaling_proj.CartItem
import com.guc_proj.signaling_proj.R
import com.guc_proj.signaling_proj.databinding.ItemCartBinding
import java.util.Locale // <-- Import Locale

class CartAdapter(
    private var cartItems: List<CartItem>,
    private val onQuantityChanged: (CartItem) -> Unit,
    private val onRemove: (CartItem) -> Unit
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    class CartViewHolder(val binding: ItemCartBinding) :
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
        val product = cartItem.product

        with(holder.binding) {
            productName.text = product?.name ?: "Unknown Item"
            productPrice.text = String.format(Locale.US, "$%.2f", product?.price ?: 0.0)
            quantityTextView.text = cartItem.quantityInCart.toString()

            Glide.with(root.context)
                .load(product?.photoUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .into(productImage)

            // --- MODIFIED LOGIC ---

            // 1. Get the maximum stock quantity from the product.
            //    If quantity is null, assume no limit (Int.MAX_VALUE).
            val maxQuantity = product?.quantity ?: Int.MAX_VALUE

            // 2. Set the initial state of the increase button when the view is bound
            //    It's enabled only if the current cart quantity is LESS than the max stock
            increaseButton.isEnabled = cartItem.quantityInCart < maxQuantity

            // 3. Set OnClickListener for Remove Button
            removeButton.setOnClickListener {
                onRemove(cartItem)
            }

            // 4. Set OnClickListener for Decrease Button
            decreaseButton.setOnClickListener {
                if (cartItem.quantityInCart > 1) {
                    cartItem.quantityInCart--
                    quantityTextView.text = cartItem.quantityInCart.toString()
                    onQuantityChanged(cartItem)
                } else {
                    onRemove(cartItem)
                }

                // After decreasing, the quantity is definitely less than the max,
                // so we must ensure the increase button is re-enabled.
                increaseButton.isEnabled = true
            }

            // 5. Set OnClickListener for Increase Button
            increaseButton.setOnClickListener {
                // The button is only clickable if it's enabled,
                // so we know cartItem.quantityInCart < maxQuantity at this point.

                cartItem.quantityInCart++
                quantityTextView.text = cartItem.quantityInCart.toString()
                onQuantityChanged(cartItem)

                // After incrementing, check again. If the new quantity
                // has reached the limit, disable the button.
                increaseButton.isEnabled = cartItem.quantityInCart < maxQuantity
            }
            // --- END MODIFIED LOGIC ---
        }
    }

    override fun getItemCount(): Int = cartItems.size

    fun updateItems(newItems: List<CartItem>) {
        cartItems = newItems
        notifyDataSetChanged()
    }
}