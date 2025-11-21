package com.guc_proj.signaling_proj.delivery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.guc_proj.signaling_proj.Order
import com.guc_proj.signaling_proj.R
import com.guc_proj.signaling_proj.databinding.ItemOrderDeliveryBinding
import java.util.Locale

class DeliveryOrderAdapter(
    private var orderList: List<Order>,
    private val onActionClick: (Order) -> Unit
) : RecyclerView.Adapter<DeliveryOrderAdapter.DeliveryViewHolder>() {

    class DeliveryViewHolder(val binding: ItemOrderDeliveryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeliveryViewHolder {
        val binding = ItemOrderDeliveryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeliveryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeliveryViewHolder, position: Int) {
        val order = orderList[position]
        val context = holder.binding.root.context

        with(holder.binding) {
            shopNameTextView.text = order.sellerName
            orderIdTextView.text = "Order ID: ...${order.orderId?.takeLast(6)}"
            feeTextView.text = String.format(Locale.US, "Earn $%.2f", order.deliveryFee)
            addressTextView.text = order.deliveryAddress

            if (order.status == Order.STATUS_READY_FOR_PICKUP) {
                actionButton.text = "Pick Up Order"
                actionButton.setBackgroundColor(context.getColor(R.color.md_theme_primary))
            } else if (order.status == Order.STATUS_OUT_FOR_DELIVERY) {
                actionButton.text = "Mark Delivered"
                actionButton.setBackgroundColor(context.getColor(R.color.status_green))
            }

            actionButton.setOnClickListener { onActionClick(order) }
        }
    }

    override fun getItemCount(): Int = orderList.size

    fun updateList(newList: List<Order>) {
        orderList = newList
        notifyDataSetChanged()
    }
}