package com.guc_proj.signaling_proj.buyer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.guc_proj.signaling_proj.Order
import com.guc_proj.signaling_proj.R
import com.guc_proj.signaling_proj.databinding.ItemOrderBuyerBinding
import java.util.Locale

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
        val context = holder.binding.root.context

        with(holder.binding) {
            sellerNameTextView.text = order.sellerName ?: "Unknown Seller"
            orderIdTextView.text = "ID: ${order.orderId}"
            totalPriceTextView.text = String.format(Locale.US, "$%.2f", order.totalPrice ?: 0.0)

            if (order.deliveryType == Order.TYPE_DELIVERY) {
                deliveryInfoTextView.text = String.format(Locale.US, "Delivery ($%.2f)", order.deliveryFee)
            } else {
                deliveryInfoTextView.text = "Pick-up"
            }

            statusTextView.text = order.status

            when (order.status) {
                Order.STATUS_PENDING -> {
                    statusTextView.setTextColor(context.getColor(R.color.status_orange))
                    statusTextView.setBackgroundResource(R.drawable.bg_role_badge)
                }
                Order.STATUS_REJECTED -> {
                    statusTextView.setTextColor(context.getColor(R.color.md_theme_error))
                    statusTextView.setBackgroundResource(R.drawable.bg_role_badge)
                }
                Order.STATUS_DELIVERED -> {
                    statusTextView.setTextColor(context.getColor(R.color.status_green))
                    statusTextView.setBackgroundResource(R.drawable.bg_role_badge)
                }
                else -> {
                    statusTextView.setTextColor(context.getColor(R.color.md_theme_primary))
                    statusTextView.setBackgroundResource(R.drawable.bg_role_badge)
                }
            }

            val itemsSummary = order.items?.values?.mapNotNull { cartItem ->
                cartItem.product?.let { product ->
                    "${product.name ?: "Unknown Item"} x ${cartItem.quantityInCart}"
                }
            }?.joinToString(", ") ?: "No items"

            itemsSummaryTextView.text = itemsSummary
        }
    }

    override fun getItemCount(): Int = orderList.size

    fun updateOrders(newOrders: List<Order>) {
        orderList = newOrders
        notifyDataSetChanged()
    }
}