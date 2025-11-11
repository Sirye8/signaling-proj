package com.guc_proj.signaling_proj.seller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.guc_proj.signaling_proj.Order
import com.guc_proj.signaling_proj.databinding.FragmentSellerOrdersBinding

class SellerOrdersFragment : Fragment() {

    private var _binding: FragmentSellerOrdersBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var orderAdapter: SellerOrderAdapter
    private val orderList = mutableListOf<Order>()

    private var ordersQuery: Query? = null
    private var ordersListener: ValueEventListener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSellerOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        database = FirebaseDatabase.getInstance().getReference("Orders")

        setupRecyclerView()
        fetchSellerOrders(currentUserId)
    }

    private fun setupRecyclerView() {
        orderAdapter = SellerOrderAdapter(orderList) { order, newStatus ->
            // Handle action click
            showUpdateConfirmationDialog(order, newStatus)
        }
        binding.ordersRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = orderAdapter
        }
    }

    private fun fetchSellerOrders(sellerId: String) {
        binding.loadingIndicator.visibility = View.VISIBLE
        ordersQuery = database.orderByChild("sellerId").equalTo(sellerId)
        ordersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                orderList.clear()
                for (orderSnapshot in snapshot.children) {
                    val order = orderSnapshot.getValue(Order::class.java)
                    order?.let { orderList.add(it) }
                }
                // Show newest orders first
                orderList.reverse()
                orderAdapter.updateOrders(orderList)

                if (_binding == null) return // View destroyed, do nothing

                binding.loadingIndicator.visibility = View.GONE
                if (orderList.isEmpty()) {
                    binding.emptyView.visibility = View.VISIBLE
                    binding.ordersRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyView.visibility = View.GONE
                    binding.ordersRecyclerView.visibility = View.VISIBLE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding != null) {
                    Toast.makeText(
                        context,
                        "Failed to load orders: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.loadingIndicator.visibility = View.GONE
                }
            }
        }
        ordersQuery?.addValueEventListener(ordersListener!!)
    }

    private fun showUpdateConfirmationDialog(order: Order, newStatus: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirm Action")
            .setMessage("Are you sure you want to change this order's status to '$newStatus'?")
            .setPositiveButton("Confirm") { _, _ -> updateOrderStatus(order, newStatus) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateOrderStatus(order: Order, newStatus: String) {
        if (order.orderId == null) {
            Toast.makeText(context, "Cannot update order: Invalid ID", Toast.LENGTH_SHORT).show()
            return
        }


        database.child(order.orderId).child("status").setValue(newStatus)
            .addOnSuccessListener {
                Toast.makeText(context, "Order status updated to $newStatus", Toast.LENGTH_SHORT)
                    .show()
                // The ValueEventListener will automatically refresh the list
            }
            .addOnFailureListener {
                Toast.makeText(
                    context,
                    "Failed to update status: ${it.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ordersListener?.let { listener ->
            ordersQuery?.removeEventListener(listener)
        }
        ordersQuery = null
        ordersListener = null
        _binding = null
    }
}