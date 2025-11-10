package com.guc_proj.signaling_proj.buyer

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.guc_proj.signaling_proj.Order
import com.guc_proj.signaling_proj.R
import com.guc_proj.signaling_proj.databinding.ItemOrderBuyerBinding

class BuyerOrderAdapter(
    private var orderList: List<Order>
) : RecyclerView.Adapter<BuyerOrderAdapter.OrderViewHolder>() {

    inner class OrderViewHolder(val binding: ItemOrderBuyerBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemOrderBuyerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = orderList[position]
        with(holder.binding) {
            sellerNameTextView.text = order.sellerName ?: "Unknown Seller"
            orderIdTextView.text = "ID: ${order.orderId}"
            totalPriceTextView.text = String.format("$%.2f", order.totalPrice)
            statusTextView.text = "Status: ${order.status}"

            // Set status color
            when (order.status) {
                Order.STATUS_PENDING -> statusTextView.setTextColor(Color.parseColor("#FFA500")) // Orange
                Order.STATUS_REJECTED -> statusTextView.setTextColor(root.context.getColor(R.color.md_theme_error))
                Order.STATUS_DELIVERED -> statusTextView.setTextColor(Color.parseColor("#008000")) // Green
                else -> statusTextView.setTextColor(root.context.getColor(R.color.md_theme_primary))
            }

            // Create items summary
            val itemsSummary = order.items?.values?.joinToString(", ") {
                "${it.product.name} x ${it.quantityInCart}"
            }
            itemsSummaryTextView.text = "Items: $itemsSummary"
        }
    }

    override fun getItemCount(): Int = orderList.size

    fun updateOrders(newOrders: List<Order>) {
        orderList = newOrders
        notifyDataSetChanged()
    }
}