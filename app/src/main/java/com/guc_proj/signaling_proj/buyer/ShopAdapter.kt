package com.guc_proj.signaling_proj.buyer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.guc_proj.signaling_proj.R
import com.guc_proj.signaling_proj.User
import com.guc_proj.signaling_proj.databinding.ItemShopBinding

class ShopAdapter(
    private val shopList: List<Pair<String, User>>,
    private val onClick: (Pair<String, User>) -> Unit
) : RecyclerView.Adapter<ShopAdapter.ShopViewHolder>() {

    inner class ShopViewHolder(val binding: ItemShopBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShopViewHolder {
        val binding = ItemShopBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ShopViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShopViewHolder, position: Int) {
        val (sellerId, seller) = shopList[position]
        with(holder.binding) {
            shopName.text = seller.name
            Glide.with(root.context)
                .load(seller.photoUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .into(shopImage)

            root.setOnClickListener {
                onClick(Pair(sellerId, seller))
            }
        }
    }

    override fun getItemCount(): Int = shopList.size
}