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
import com.guc_proj.signaling_proj.databinding.FragmentDeliveryHistoryBinding
import java.util.Locale

class DeliveryHistoryFragment : Fragment() {

    private var _binding: FragmentDeliveryHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var historyAdapter: DeliveryHistoryAdapter
    private val historyList = mutableListOf<Order>()

    // 1. Promote Query and Listener to class variables for proper cleanup
    private var historyQuery: Query? = null
    private var historyListener: ValueEventListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDeliveryHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Orders")

        setupRecyclerView()
        fetchHistory()
    }

    private fun setupRecyclerView() {
        historyAdapter = DeliveryHistoryAdapter(historyList)
        binding.historyRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = historyAdapter
        }
    }

    private fun fetchHistory() {
        binding.loadingIndicator.visibility = View.VISIBLE
        val currentDriverId = auth.currentUser?.uid ?: return

        // Query orders by this delivery person
        historyQuery = database.orderByChild("deliveryPersonId").equalTo(currentDriverId)

        historyListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // 2. Check if view is still valid before updating UI
                if (_binding == null) return

                historyList.clear()
                var totalEarnings = 0.0

                for (child in snapshot.children) {
                    val order = child.getValue(Order::class.java) ?: continue

                    // Only show COMPLETED deliveries in history
                    if (order.status == Order.STATUS_DELIVERED) {
                        historyList.add(order)
                        totalEarnings += order.deliveryFee
                    }
                }

                historyList.reverse()
                historyAdapter.updateList(historyList)

                binding.totalEarningsText.text = String.format(Locale.US, "$%.2f", totalEarnings)
                binding.totalJobsText.text = "${historyList.size} Jobs Completed"

                binding.loadingIndicator.visibility = View.GONE
                binding.emptyView.visibility = if (historyList.isEmpty()) View.VISIBLE else View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                // 3. Strict checks to prevent Toast during logout
                if (_binding != null && isAdded && activity != null && !requireActivity().isFinishing) {
                    binding.loadingIndicator.visibility = View.GONE
                    Toast.makeText(context, "Failed to load history", Toast.LENGTH_SHORT).show()
                }
            }
        }

        historyQuery?.addValueEventListener(historyListener!!)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 4. Remove the listener when the view is destroyed
        historyListener?.let { listener ->
            historyQuery?.removeEventListener(listener)
        }
        historyQuery = null
        historyListener = null
        _binding = null
    }
}