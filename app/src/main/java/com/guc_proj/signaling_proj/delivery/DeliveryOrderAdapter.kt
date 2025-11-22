package com.guc_proj.signaling_proj.delivery

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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

            buyerNameTextView.text = order.buyerName ?: "Unknown Buyer"
            addressTextView.text = order.deliveryAddress

            // Apply rounded background shape
            paymentStatusTextView.background = ContextCompat.getDrawable(context, R.drawable.bg_status_rounded)

            if (order.paymentMethod == Order.PAY_CASH) {
                paymentStatusTextView.visibility = View.VISIBLE
                paymentStatusTextView.text = String.format(Locale.US, "Collect Cash: $%.2f", order.finalPrice)
                paymentStatusTextView.backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.md_theme_errorContainer))
                paymentStatusTextView.setTextColor(context.getColor(R.color.md_theme_onErrorContainer))
            } else {
                paymentStatusTextView.visibility = View.VISIBLE
                paymentStatusTextView.text = "Paid Online (Do Not Collect)"
                paymentStatusTextView.backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.md_theme_primaryContainer))
                paymentStatusTextView.setTextColor(context.getColor(R.color.md_theme_onPrimaryContainer))
            }

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