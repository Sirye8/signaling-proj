package com.guc_proj.signaling_proj.seller

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.guc_proj.signaling_proj.Product
import com.guc_proj.signaling_proj.R
import com.guc_proj.signaling_proj.databinding.ItemProductSellerBinding

class ProductAdapter(
    private val productList: List<Product>,
    private val onEditClick: (Product) -> Unit,
    private val onDeleteClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    inner class ProductViewHolder(val binding: ItemProductSellerBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductSellerBinding.inflate(
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
            productQuantity.text = "Qty: ${product.quantity}"

            Glide.with(root.context)
                .load(product.photoUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .into(productImage)

            editButton.setOnClickListener { onEditClick(product) }
            deleteButton.setOnClickListener { onDeleteClick(product) }
        }
    }

    override fun getItemCount(): Int = productList.size
}