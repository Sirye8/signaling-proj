package com.guc_proj.signaling_proj.delivery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.guc_proj.signaling_proj.Order
import com.guc_proj.signaling_proj.databinding.ItemOrderDeliveryHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeliveryHistoryAdapter(
    private var orderList: List<Order>
) : RecyclerView.Adapter<DeliveryHistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(val binding: ItemOrderDeliveryHistoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemOrderDeliveryHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val order = orderList[position]

        with(holder.binding) {
            shopNameTextView.text = order.sellerName ?: "Unknown Shop"

            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            dateTextView.text = sdf.format(Date(order.timestamp))

            feeTextView.text = String.format(Locale.US, "+EGP%.2f", order.deliveryFee)

            buyerNameTextView.text = order.buyerName ?: "Unknown Buyer"
            addressTextView.text = order.deliveryAddress ?: "No address"

            paymentInfoTextView.text = "Payment: ${order.paymentMethod}"
        }
    }

    override fun getItemCount(): Int = orderList.size

    fun updateList(newList: List<Order>) {
        orderList = newList
        notifyDataSetChanged()
    }
}