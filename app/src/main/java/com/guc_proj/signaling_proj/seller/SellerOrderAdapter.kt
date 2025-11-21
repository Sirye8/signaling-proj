package com.guc_proj.signaling_proj.seller

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.guc_proj.signaling_proj.Order
import com.guc_proj.signaling_proj.R
import com.guc_proj.signaling_proj.databinding.ItemOrderSellerBinding
import java.text.SimpleDateFormat
import java.util.Date
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

            // Date
            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            dateTextView.text = sdf.format(Date(order.timestamp))

            statusTextView.text = order.status
            val statusColor = when (order.status) {
                Order.STATUS_PENDING -> context.getColor(R.color.status_orange)
                Order.STATUS_REJECTED -> context.getColor(R.color.md_theme_error)
                Order.STATUS_DELIVERED -> context.getColor(R.color.status_green)
                else -> context.getColor(R.color.md_theme_primary)
            }
            statusTextView.setTextColor(statusColor)

            // Delivery Info
            deliveryTypeTextView.text = order.deliveryType
            if (order.deliveryType == Order.TYPE_DELIVERY) {
                deliveryAddressTextView.visibility = View.VISIBLE
                deliveryAddressTextView.text = order.deliveryAddress ?: "No Address Provided"
            } else {
                deliveryAddressTextView.visibility = View.GONE
            }

            // Dynamic Items
            itemsContainer.removeAllViews()
            order.items?.values?.forEach { cartItem ->
                val product = cartItem.product
                if (product != null) {
                    val itemView = LayoutInflater.from(context).inflate(R.layout.view_order_item_row, itemsContainer, false)

                    val thumb = itemView.findViewById<ImageView>(R.id.itemThumb)
                    val qty = itemView.findViewById<TextView>(R.id.itemQty)
                    val name = itemView.findViewById<TextView>(R.id.itemName)
                    val price = itemView.findViewById<TextView>(R.id.itemPrice)

                    name.text = product.name
                    qty.text = "${cartItem.quantityInCart}x"
                    val itemTotal = (product.price ?: 0.0) * cartItem.quantityInCart
                    price.text = String.format(Locale.US, "$%.2f", itemTotal)

                    Glide.with(context)
                        .load(product.photoUrl)
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .centerCrop()
                        .into(thumb)

                    itemsContainer.addView(itemView)
                }
            }

            // Actions Visibility
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