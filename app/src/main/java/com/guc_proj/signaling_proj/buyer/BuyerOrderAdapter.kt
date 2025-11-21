package com.guc_proj.signaling_proj.buyer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.guc_proj.signaling_proj.Order
import com.guc_proj.signaling_proj.R
import com.guc_proj.signaling_proj.databinding.ItemOrderBuyerBinding
import java.text.SimpleDateFormat
import java.util.Date
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
            sellerNameTextView.text = order.sellerName ?: "Unknown Shop"
            totalPriceTextView.text = String.format(Locale.US, "$%.2f", order.totalPrice ?: 0.0)

            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            dateTextView.text = sdf.format(Date(order.timestamp))

            // Logic for Status Text
            var displayStatus = order.status

            if (order.deliveryType == Order.TYPE_DELIVERY && order.status == Order.STATUS_READY_FOR_PICKUP) {
                displayStatus = "Preparing"
            }

            // Progress Logic
            val (progress, statusColor) = when (order.status) {
                Order.STATUS_PENDING -> Pair(10, context.getColor(R.color.status_orange))
                Order.STATUS_ACCEPTED -> Pair(35, context.getColor(R.color.md_theme_primary))
                Order.STATUS_PREPARING -> Pair(50, context.getColor(R.color.md_theme_primary))

                Order.STATUS_READY_FOR_PICKUP -> {
                    if (order.deliveryType == Order.TYPE_PICKUP) {
                        Pair(85, context.getColor(R.color.status_green)) // Ready for you to pick up
                    } else {
                        Pair(60, context.getColor(R.color.md_theme_primary)) // Still in "Preparing" phase for buyer
                    }
                }

                Order.STATUS_OUT_FOR_DELIVERY -> Pair(80, context.getColor(R.color.md_theme_primary))
                Order.STATUS_DELIVERED, Order.STATUS_COMPLETED -> Pair(100, context.getColor(R.color.status_green))
                Order.STATUS_REJECTED -> Pair(0, context.getColor(R.color.md_theme_error))
                else -> Pair(0, context.getColor(R.color.md_theme_outline))
            }

            statusTextView.text = displayStatus
            statusTextView.setTextColor(statusColor)

            orderProgressBar.progress = progress
            if (order.status == Order.STATUS_REJECTED) {
                orderProgressBar.setIndicatorColor(context.getColor(R.color.md_theme_error))
            } else {
                orderProgressBar.setIndicatorColor(context.getColor(R.color.md_theme_primary))
            }

            if (order.deliveryType == Order.TYPE_DELIVERY) {
                deliveryInfoTextView.text = String.format(Locale.US, "Delivery ($%.2f)", order.deliveryFee)
                addressTextView.visibility = View.VISIBLE
                addressTextView.text = order.deliveryAddress ?: "Address info unavailable"
            } else {
                deliveryInfoTextView.text = "Pick-up (Free)"
                addressTextView.visibility = View.GONE
            }

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
        }
    }

    override fun getItemCount(): Int = orderList.size

    fun updateOrders(newOrders: List<Order>) {
        orderList = newOrders
        notifyDataSetChanged()
    }
}