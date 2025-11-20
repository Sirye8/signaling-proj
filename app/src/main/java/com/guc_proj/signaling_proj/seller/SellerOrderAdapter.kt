package com.guc_proj.signaling_proj.seller

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
        val context = holder.binding.root.context

        with(holder.binding) {
            buyerNameTextView.text = order.buyerName ?: "Unknown Buyer"
            totalPriceTextView.text = String.format(Locale.US, "$%.2f", order.totalPrice)
            statusTextView.text = order.status
            // Delivery Info binding
            deliveryTypeTextView.text = "Type: ${order.deliveryType}"
            if (order.deliveryType == Order.TYPE_DELIVERY) {
                deliveryAddressTextView.visibility = View.VISIBLE
                deliveryAddressTextView.text = "Addr: ${order.deliveryAddress}"
            } else {
                deliveryAddressTextView.visibility = View.GONE
            }
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

            // Items Summary
            val itemsSummary = order.items?.values?.mapNotNull { cartItem ->
                cartItem.product?.let { product ->
                    "${product.name ?: "Unknown"} x${cartItem.quantityInCart}"
                }
            }?.joinToString(", ") ?: "No items"
            itemsSummaryTextView.text = itemsSummary
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