package com.guc_proj.signaling_proj.seller

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
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

            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            dateTextView.text = sdf.format(Date(order.timestamp))

            // Status Display
            var displayStatus = order.status
            if (order.deliveryType == Order.TYPE_DELIVERY && order.status == Order.STATUS_READY_FOR_PICKUP) {
                if (order.isVolunteerRequested) displayStatus = "Waiting for Vol." else displayStatus = "Ready"
            }

            statusTextView.text = displayStatus
            val statusColor = when (order.status) {
                Order.STATUS_PENDING -> context.getColor(R.color.status_orange)
                Order.STATUS_REJECTED -> context.getColor(R.color.md_theme_error)
                Order.STATUS_DELIVERED, Order.STATUS_COMPLETED -> context.getColor(R.color.status_green)
                else -> context.getColor(R.color.md_theme_primary)
            }
            statusTextView.setTextColor(statusColor)

            deliveryTypeTextView.text = order.deliveryType
            if (order.deliveryType == Order.TYPE_DELIVERY) {
                deliveryAddressTextView.visibility = View.VISIBLE
                deliveryAddressTextView.text = order.deliveryAddress ?: "No Address Provided"
            } else {
                deliveryAddressTextView.visibility = View.GONE
            }

            paymentInfoTextView.text = order.paymentMethod

            // Items
            itemsContainer.removeAllViews()
            order.items?.values?.forEach { cartItem ->
                val product = cartItem.product
                if (product != null) {
                    val itemView = LayoutInflater.from(context).inflate(R.layout.view_order_item_row, itemsContainer, false)
                    itemView.findViewById<TextView>(R.id.itemQty).text = "${cartItem.quantityInCart}x"
                    itemView.findViewById<TextView>(R.id.itemName).text = product.name
                    val itemTotal = (product.price ?: 0.0) * cartItem.quantityInCart
                    itemView.findViewById<TextView>(R.id.itemPrice).text = String.format(Locale.US, "$%.2f", itemTotal)
                    Glide.with(context).load(product.photoUrl).placeholder(R.drawable.ic_launcher_foreground).into(itemView.findViewById(R.id.itemThumb))
                    itemsContainer.addView(itemView)
                }
            }

            pendingActionsLayout.visibility = View.GONE
            acceptedActionsScrollView.visibility = View.GONE

            // --- "Ask for Volunteer" Button Logic ---
            // Only visible if Delivery type AND Ready for Pickup
            val volunteerButton = holder.itemView.findViewById<Chip>(R.id.askVolunteerButton)
            if (order.deliveryType == Order.TYPE_DELIVERY && order.status == Order.STATUS_READY_FOR_PICKUP) {
                volunteerButton.visibility = View.VISIBLE
                if (order.isVolunteerRequested) {
                    volunteerButton.text = "Volunteer Requested"
                    volunteerButton.isEnabled = false
                    volunteerButton.setChipIconTintResource(R.color.status_green)
                } else {
                    volunteerButton.text = "Ask for Volunteer"
                    volunteerButton.isEnabled = true
                    volunteerButton.setChipIconTintResource(R.color.black)
                }
            } else {
                volunteerButton.visibility = View.GONE
            }

            // --- State Transitions ---
            when (order.status) {
                Order.STATUS_PENDING -> {
                    pendingActionsLayout.visibility = View.VISIBLE
                }
                Order.STATUS_ACCEPTED -> {
                    acceptedActionsScrollView.visibility = View.VISIBLE
                    setPreparingButton.visibility = View.VISIBLE
                    setOutForDeliveryButton.visibility = View.GONE
                    setDeliveredButton.visibility = View.GONE
                }
                Order.STATUS_PREPARING -> {
                    acceptedActionsScrollView.visibility = View.VISIBLE
                    setPreparingButton.visibility = View.GONE
                    setOutForDeliveryButton.visibility = View.VISIBLE
                    setOutForDeliveryButton.text = "Ready for Pickup" // Next Step
                    setOutForDeliveryButton.setOnClickListener { onActionClick(order, Order.STATUS_READY_FOR_PICKUP) }
                    setDeliveredButton.visibility = View.GONE
                }
                Order.STATUS_READY_FOR_PICKUP -> {
                    acceptedActionsScrollView.visibility = View.VISIBLE
                    setPreparingButton.visibility = View.GONE

                    if (order.deliveryType == Order.TYPE_PICKUP) {
                        // Pickup Flow: Show "Mark Picked Up"
                        setOutForDeliveryButton.visibility = View.GONE
                        setDeliveredButton.visibility = View.VISIBLE
                        setDeliveredButton.text = "Mark Picked Up"
                        setDeliveredButton.setOnClickListener { onActionClick(order, Order.STATUS_COMPLETED) }
                    } else {
                        // Delivery Flow: Seller can still click "Dispatch" to do it themselves
                        setOutForDeliveryButton.visibility = View.VISIBLE
                        setOutForDeliveryButton.text = "Dispatch"
                        setOutForDeliveryButton.setOnClickListener { onActionClick(order, Order.STATUS_OUT_FOR_DELIVERY) }

                        setDeliveredButton.visibility = View.GONE
                    }
                }
                Order.STATUS_OUT_FOR_DELIVERY -> {
                    // Seller delivered it themselves -> Show "Mark Delivered"
                    acceptedActionsScrollView.visibility = View.VISIBLE
                    setPreparingButton.visibility = View.GONE
                    setOutForDeliveryButton.visibility = View.GONE

                    setDeliveredButton.visibility = View.VISIBLE
                    setDeliveredButton.text = "Mark Delivered"
                    setDeliveredButton.setOnClickListener { onActionClick(order, Order.STATUS_DELIVERED) }
                }
            }

            // Click Listeners for simple actions
            acceptButton.setOnClickListener { onActionClick(order, Order.STATUS_ACCEPTED) }
            rejectButton.setOnClickListener { onActionClick(order, Order.STATUS_REJECTED) }
            setPreparingButton.setOnClickListener { onActionClick(order, Order.STATUS_PREPARING) }
            // setOutForDeliveryButton & setDeliveredButton listeners are set inside 'when' for specific context

            volunteerButton.setOnClickListener { onActionClick(order, "ACTION_ASK_VOLUNTEER") }
        }
    }

    override fun getItemCount(): Int = orderList.size

    fun updateOrders(newOrders: List<Order>) {
        orderList = newOrders
        notifyDataSetChanged()
    }
}