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
                if (_binding == null) return
                orderList.clear()
                for (orderSnapshot in snapshot.children) {
                    val order = orderSnapshot.getValue(Order::class.java)
                    order?.let { orderList.add(it) }
                }
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
                if (_binding != null && isAdded && activity != null && !requireActivity().isFinishing) {
                    Toast.makeText(context, "Failed: ${error.message}", Toast.LENGTH_SHORT).show()
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
        if (order.orderId == null) return

        if (order.status != Order.STATUS_REJECTED && newStatus == Order.STATUS_REJECTED) {
            incrementStockForOrder(order)
        }

        database.child(order.orderId).child("status").setValue(newStatus)
            .addOnSuccessListener {
                if (_binding == null) return@addOnSuccessListener
                Toast.makeText(context?.applicationContext, "Order updated to $newStatus", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                Toast.makeText(context?.applicationContext, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun incrementStockForOrder(order: Order) {
        val productsRef = FirebaseDatabase.getInstance().getReference("Products")

        order.items?.forEach { (productId, cartItem) ->
            val quantityToRestore = cartItem.quantityInCart
            val productQuantityRef = productsRef.child(productId).child("quantity")

            productQuantityRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val currentQuantity = currentData.getValue(Int::class.java) ?: return Transaction.success(currentData)
                    currentData.value = currentQuantity + quantityToRestore
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    if (error != null) Log.e("SellerOrders", "Stock restore failed for $productId")
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