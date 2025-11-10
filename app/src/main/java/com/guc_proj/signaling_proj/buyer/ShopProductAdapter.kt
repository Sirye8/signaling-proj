package com.guc_proj.signaling_proj.buyer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.guc_proj.signaling_proj.Product
import com.guc_proj.signaling_proj.R
import com.guc_proj.signaling_proj.databinding.ItemProductBuyerBinding

class ShopProductAdapter(
    private val productList: List<Product>,
    private val onAddToCartClick: (Product) -> Unit
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
        with(holder.binding) {
            productName.text = product.name
            productPrice.text = String.format("$%.2f", product.price)

            Glide.with(root.context)
                .load(product.photoUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .into(productImage)

            addToCartButton.setOnClickListener { onAddToCartClick(product) }
        }
    }

    override fun getItemCount(): Int = productList.size
}