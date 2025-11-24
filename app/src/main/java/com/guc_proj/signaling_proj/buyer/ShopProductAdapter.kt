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
import java.util.Locale

class ShopProductAdapter(
    private val productList: List<Product>
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
        val productId = product.productId ?: return
        val context = holder.binding.root.context

        with(holder.binding) {
            productName.text = product.name
            productPrice.text = String.format(Locale.US, "EGP%.2f", product.price ?: 0.0)

            Glide.with(context)
                .load(product.photoUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .centerCrop()
                .into(productImage)

            val maxQuantity = product.quantity ?: 0
            var currentQuantity = CartManager.getQuantity(productId)

            // Toggle between "Add" button and Stepper layout
            if (currentQuantity == 0) {
                addToCartButton.visibility = View.VISIBLE
                quantityStepperLayout.visibility = View.GONE

                addToCartButton.isEnabled = maxQuantity > 0
                addToCartButton.text = if (maxQuantity > 0) "Add" else "Out of Stock"
            } else {
                addToCartButton.visibility = View.GONE
                quantityStepperLayout.visibility = View.VISIBLE
                quantityTextView.text = currentQuantity.toString()

                increaseButton.isEnabled = currentQuantity < maxQuantity
                increaseButton.alpha = if (increaseButton.isEnabled) 1.0f else 0.5f
            }

            // "Add to Cart" Click
            addToCartButton.setOnClickListener {
                val status = CartManager.addItem(product)
                if (status == AddToCartStatus.ADDED) {
                    addToCartButton.visibility = View.GONE
                    quantityStepperLayout.visibility = View.VISIBLE
                    quantityTextView.text = "1"

                    increaseButton.isEnabled = 1 < maxQuantity
                    increaseButton.alpha = if (increaseButton.isEnabled) 1.0f else 0.5f

                    Toast.makeText(context, "${product.name} added", Toast.LENGTH_SHORT).show()
                } else if (status == AddToCartStatus.OUT_OF_STOCK) {
                    Toast.makeText(context, "Out of stock", Toast.LENGTH_SHORT).show()
                }
            }

            // Increase (+) Click
            increaseButton.setOnClickListener {
                val status = CartManager.addItem(product)
                currentQuantity = CartManager.getQuantity(productId)

                if (status == AddToCartStatus.INCREASED) {
                    quantityTextView.text = currentQuantity.toString()
                    increaseButton.isEnabled = currentQuantity < maxQuantity
                    increaseButton.alpha = if (increaseButton.isEnabled) 1.0f else 0.5f
                } else if (status == AddToCartStatus.LIMIT_REACHED) {
                    Toast.makeText(context, "Max stock reached", Toast.LENGTH_SHORT).show()
                    increaseButton.isEnabled = false
                    increaseButton.alpha = 0.5f
                }
            }

            // Decrease (-) Click
            decreaseButton.setOnClickListener {
                val newQuantity = CartManager.decreaseItem(productId)

                if (newQuantity == 0) {
                    addToCartButton.visibility = View.VISIBLE
                    quantityStepperLayout.visibility = View.GONE
                } else {
                    quantityTextView.text = newQuantity.toString()
                }
                // Since we decreased, we are definitely below max
                increaseButton.isEnabled = true
                increaseButton.alpha = 1.0f
            }
        }
    }

    override fun getItemCount(): Int = productList.size
}