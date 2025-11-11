package com.guc_proj.signaling_proj.seller

import android.os.Bundle
import android.util.Log
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
                if (_binding == null) return // View destroyed, do nothing
                orderList.clear()
                for (orderSnapshot in snapshot.children) {
                    val order = orderSnapshot.getValue(Order::class.java)
                    order?.let { orderList.add(it) }
                }
                // Show newest orders first
                orderList.reverse()
                orderAdapter.updateOrders(orderList)

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

        // --- NEW LOGIC ---
        // Check if this is the specific transition from Pending to Accepted
        if (order.status == Order.STATUS_PENDING && newStatus == Order.STATUS_ACCEPTED) {
            // If so, trigger the stock decrement logic
            decrementStockForOrder(order)
        }
        // --- END NEW LOGIC ---

        database.child(order.orderId).child("status").setValue(newStatus)
            .addOnSuccessListener {
                if (_binding == null) return@addOnSuccessListener
                Toast.makeText(context?.applicationContext, "Order status updated to $newStatus", Toast.LENGTH_SHORT)
                    .show()
                // The ValueEventListener will automatically refresh the list
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                Toast.makeText(
                    context?.applicationContext,
                    "Failed to update status: ${it.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    /**
     * Iterates over items in an order and decrements their quantity in the "Products" node
     * using a Firebase Transaction to prevent race conditions.
     */
    private fun decrementStockForOrder(order: Order) {
        val productsRef = FirebaseDatabase.getInstance().getReference("Products")

        order.items?.forEach { (productId, cartItem) ->
            val orderedQuantity = cartItem.quantityInCart
            // Get a reference to the specific product's quantity
            val productQuantityRef = productsRef.child(productId).child("quantity")

            // Run a transaction to safely read and update the quantity
            productQuantityRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    // Get the current quantity
                    val currentQuantity = currentData.getValue(Int::class.java)
                    if (currentQuantity == null) {
                        // Product might have been deleted, or data is corrupt
                        // We'll just abort the transaction for this item
                        Log.w("SellerOrdersFragment", "Product quantity for $productId is null, skipping stock update.")
                        return Transaction.success(currentData)
                    }

                    // Calculate the new quantity
                    val newQuantity = currentQuantity - orderedQuantity

                    // Set the new value, ensuring it doesn't go below zero
                    currentData.value = if (newQuantity < 0) 0 else newQuantity

                    return Transaction.success(currentData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    // This is called after the transaction finishes
                    if (error != null) {
                        // The transaction failed
                        Log.e("SellerOrdersFragment", "Stock decrement transaction failed for $productId: ${error.message}")
                    } else {
                        // The transaction succeeded
                        Log.d("SellerOrdersFragment", "Stock decremented for $productId. New quantity: ${currentData?.value}")
                    }
                }
            })
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