package com.guc_proj.signaling_proj.buyer

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
import com.guc_proj.signaling_proj.databinding.FragmentBuyerOrdersBinding

class BuyerOrdersFragment : Fragment() {

    private var _binding: FragmentBuyerOrdersBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var orderAdapter: BuyerOrderAdapter
    private val orderList = mutableListOf<Order>()

    private var ordersQuery: Query? = null
    private var ordersListener: ValueEventListener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBuyerOrdersBinding.inflate(inflater, container, false)
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
        fetchBuyerOrders(currentUserId)
    }

    private fun setupRecyclerView() {
        orderAdapter = BuyerOrderAdapter(orderList)
        binding.ordersRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = orderAdapter
        }
    }

    private fun fetchBuyerOrders(buyerId: String) {
        binding.loadingIndicator.visibility = View.VISIBLE
        ordersQuery = database.orderByChild("buyerId").equalTo(buyerId)
        ordersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                orderList.clear()
                for (orderSnapshot in snapshot.children) {
                    val order = orderSnapshot.getValue(Order::class.java)
                    order?.let { orderList.add(it) }
                }
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
                if (_binding != null && isAdded && activity != null && !requireActivity().isFinishing) {
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