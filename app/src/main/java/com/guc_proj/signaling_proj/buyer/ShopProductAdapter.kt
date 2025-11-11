package com.guc_proj.signaling_proj.buyer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.guc_proj.signaling_proj.Product
import com.guc_proj.signaling_proj.R
import com.guc_proj.signaling_proj.databinding.ItemProductBuyerBinding

class ShopProductAdapter(
    private val productList: List<Product>
    // We remove "onAddToCartClick: (Product) -> Unit" from here
    // as the logic is now too complex for a simple callback.
) : RecyclerView.Adapter<ShopProductAdapter.ProductViewHolder>() {

    inner class ProductViewHolder(val binding: ItemProductBuyerBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBuyerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = productList[position]
        val productId = product.productId ?: return // Don't bind if product has no ID
        val context = holder.binding.root.context

        with(holder.binding) {
            productName.text = product.name
            productPrice.text = String.format("$%.2f", product.price)

            Glide.with(context)
                .load(product.photoUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .into(productImage)

            // --- ALL NEW LOGIC ---

            val maxQuantity = product.quantity ?: 0

            // 1. Get the current quantity from CartManager
            var currentQuantity = CartManager.getQuantity(productId)

            // 2. Set the correct UI state based on quantity
            if (currentQuantity == 0) {
                // Not in cart: Show "Add" button, hide stepper
                addToCartButton.visibility = View.VISIBLE
                quantityStepperLayout.visibility = View.GONE
                // Ensure "Add to Cart" is enabled if there is stock
                addToCartButton.isEnabled = maxQuantity > 0
                if (maxQuantity <= 0) {
                    addToCartButton.text = "Out of Stock"
                } else {
                    addToCartButton.text = "Add to Cart"
                }
            } else {
                // Already in cart: Hide "Add" button, show stepper
                addToCartButton.visibility = View.GONE
                quantityStepperLayout.visibility = View.VISIBLE
                quantityTextView.text = currentQuantity.toString()
                // Disable "+" button if limit is reached
                increaseButton.isEnabled = currentQuantity < maxQuantity
            }

            // 3. Set click listener for "Add to Cart"
            addToCartButton.setOnClickListener {
                val status = CartManager.addItem(product)
                if (status == AddToCartStatus.ADDED) {
                    // Item added (quantity is 1), so switch UI
                    addToCartButton.visibility = View.GONE
                    quantityStepperLayout.visibility = View.VISIBLE
                    quantityTextView.text = "1"
                    // Check if max quantity is 1, disable "increase" immediately
                    increaseButton.isEnabled = 1 < maxQuantity
                    Toast.makeText(context, "${product.name} added.", Toast.LENGTH_SHORT).show()
                } else if (status == AddToCartStatus.OUT_OF_STOCK) {
                    Toast.makeText(context, "${product.name} is out of stock.", Toast.LENGTH_SHORT).show()
                }
            }

            // 4. Set click listener for "Increase" button (+)
            increaseButton.setOnClickListener {
                val status = CartManager.addItem(product)
                currentQuantity = CartManager.getQuantity(productId)

                if (status == AddToCartStatus.INCREASED) {
                    quantityTextView.text = currentQuantity.toString()
                    // Disable button if we just reached the limit
                    increaseButton.isEnabled = currentQuantity < maxQuantity
                } else if (status == AddToCartStatus.LIMIT_REACHED) {
                    Toast.makeText(context, "No more ${product.name} in stock.", Toast.LENGTH_SHORT).show()
                    increaseButton.isEnabled = false
                }
            }

            // 5. Set click listener for "Decrease" button (-)
            decreaseButton.setOnClickListener {
                val newQuantity = CartManager.decreaseItem(productId)

                if (newQuantity == 0) {
                    // Quantity is zero, switch UI back to "Add" button
                    addToCartButton.visibility = View.VISIBLE
                    quantityStepperLayout.visibility = View.GONE
                } else {
                    // Update quantity text
                    quantityTextView.text = newQuantity.toString()
                }

                // We just decreased, so the "+" button must be enabled
                increaseButton.isEnabled = true
            }
        }
    }

    override fun getItemCount(): Int = productList.size
}