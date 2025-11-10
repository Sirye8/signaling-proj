package com.guc_proj.signaling_proj.seller

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.guc_proj.signaling_proj.Order
import com.guc_proj.signaling_proj.R
import com.guc_proj.signaling_proj.databinding.ItemOrderSellerBinding
import java.util.Locale

class SellerOrderAdapter(
    private var orderList: List<Order>,
    private val onActionClick: (Order, String) -> Unit
) : RecyclerView.Adapter<SellerOrderAdapter.OrderViewHolder>() {

    class OrderViewHolder(val binding: ItemOrderSellerBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemOrderSellerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = orderList[position]
        with(holder.binding) {
            buyerNameTextView.text = order.buyerName ?: "Unknown Buyer"
            totalPriceTextView.text = String.format(Locale.US, "$%.2f", order.totalPrice)
            statusTextView.text = "Status: ${order.status}"

            // Set status color
            when (order.status) {
                Order.STATUS_PENDING -> statusTextView.setTextColor(Color.parseColor("#FFA500"))
                Order.STATUS_REJECTED -> statusTextView.setTextColor(root.context.getColor(R.color.md_theme_error))
                Order.STATUS_DELIVERED -> statusTextView.setTextColor(Color.parseColor("#008000"))
                else -> statusTextView.setTextColor(root.context.getColor(R.color.md_theme_primary))
            }

            val itemsSummary = order.items?.values?.mapNotNull { cartItem ->
                cartItem.product?.let { product ->
                    "${product.name ?: "Unknown Item"} x ${cartItem.quantityInCart}"
                }
            }?.joinToString(", ") ?: "No items"

            itemsSummaryTextView.text = "Items: $itemsSummary"

            when (order.status) {
                Order.STATUS_PENDING -> {
                    pendingActionsLayout.visibility = View.VISIBLE
                    acceptedActionsScrollView.visibility = View.GONE
                }
                Order.STATUS_REJECTED, Order.STATUS_DELIVERED -> {
                    pendingActionsLayout.visibility = View.GONE
                    acceptedActionsScrollView.visibility = View.GONE
                }
                else -> {
                    pendingActionsLayout.visibility = View.GONE
                    acceptedActionsScrollView.visibility = View.VISIBLE
                }
            }

            acceptButton.setOnClickListener { onActionClick(order, Order.STATUS_ACCEPTED) }
            rejectButton.setOnClickListener { onActionClick(order, Order.STATUS_REJECTED) }
            setPreparingButton.setOnClickListener { onActionClick(order, Order.STATUS_PREPARING) }
            setOutForDeliveryButton.setOnClickListener { onActionClick(order, Order.STATUS_OUT_FOR_DELIVERY) }
            setDeliveredButton.setOnClickListener { onActionClick(order, Order.STATUS_DELIVERED) }
        }
    }

    override fun getItemCount(): Int = orderList.size

    fun updateOrders(newOrders: List<Order>) {
        orderList = newOrders
        notifyDataSetChanged()
    }
}