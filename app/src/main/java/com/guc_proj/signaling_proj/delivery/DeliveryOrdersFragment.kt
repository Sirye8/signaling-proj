package com.guc_proj.signaling_proj.delivery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.guc_proj.signaling_proj.Order
import com.guc_proj.signaling_proj.databinding.FragmentDeliveryOrdersBinding

class DeliveryOrdersFragment : Fragment() {

    private var _binding: FragmentDeliveryOrdersBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    private lateinit var availableAdapter: DeliveryOrderAdapter
    private lateinit var myJobsAdapter: DeliveryOrderAdapter

    private val availableList = mutableListOf<Order>()
    private val myJobsList = mutableListOf<Order>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDeliveryOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Orders")

        setupRecyclerViews()
        fetchAllOrders()
    }

    private fun setupRecyclerViews() {
        availableAdapter = DeliveryOrderAdapter(availableList) { order ->
            acceptOrder(order)
        }
        binding.availableRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = availableAdapter
        }

        myJobsAdapter = DeliveryOrderAdapter(myJobsList) { order ->
            if (order.status == Order.STATUS_OUT_FOR_DELIVERY) {
                completeDelivery(order)
            }
        }
        binding.myJobsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = myJobsAdapter
        }
    }

    private fun fetchAllOrders() {
        binding.loadingIndicator.visibility = View.VISIBLE
        val currentDriverId = auth.currentUser?.uid ?: return

        // Listen to all orders and filter client-side for complexity reasons
        // (Firebase complex queries require indices)
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return
                availableList.clear()
                myJobsList.clear()

                for (child in snapshot.children) {
                    val order = child.getValue(Order::class.java) ?: continue

                    // Only care about DELIVERY type orders
                    if (order.deliveryType == Order.TYPE_DELIVERY) {
                        // 1. My Active Jobs
                        if (order.deliveryPersonId == currentDriverId && order.status != Order.STATUS_DELIVERED) {
                            myJobsList.add(order)
                        }
                        // 2. Available Jobs (Ready for Pickup AND Unassigned)
                        else if (order.status == Order.STATUS_READY_FOR_PICKUP && order.deliveryPersonId == null) {
                            availableList.add(order)
                        }
                    }
                }

                availableAdapter.updateList(availableList)
                myJobsAdapter.updateList(myJobsList)

                binding.loadingIndicator.visibility = View.GONE

                // UI Toggles
                binding.myJobsTitle.visibility = if (myJobsList.isNotEmpty()) View.VISIBLE else View.GONE
                binding.emptyView.visibility = if (availableList.isEmpty()) View.VISIBLE else View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding != null) binding.loadingIndicator.visibility = View.GONE
            }
        })
    }

    private fun acceptOrder(order: Order) {
        val driverId = auth.currentUser?.uid ?: return
        val updates = mapOf<String, Any>(
            "status" to Order.STATUS_OUT_FOR_DELIVERY,
            "deliveryPersonId" to driverId
        )

        order.orderId?.let { id ->
            database.child(id).updateChildren(updates)
                .addOnSuccessListener {
                    Toast.makeText(context, "Delivery Accepted!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Failed to accept.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun completeDelivery(order: Order) {
        order.orderId?.let { id ->
            database.child(id).child("status").setValue(Order.STATUS_DELIVERED)
                .addOnSuccessListener {
                    Toast.makeText(context, "Order Delivered!", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}