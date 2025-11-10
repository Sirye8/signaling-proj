package com.guc_proj.signaling_proj.buyer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.database.*
import com.guc_proj.signaling_proj.User
import com.guc_proj.signaling_proj.databinding.FragmentShopsBinding

class ShopsFragment : Fragment() {

    private var _binding: FragmentShopsBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: DatabaseReference
    private lateinit var shopAdapter: ShopAdapter
    private val shopList = mutableListOf<Pair<String, User>>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShopsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = FirebaseDatabase.getInstance().getReference("Users")

        setupRecyclerView()
        fetchShops()
    }

    private fun setupRecyclerView() {
        shopAdapter = ShopAdapter(shopList) { (sellerId, seller) ->
            // When a shop is clicked, open the ShopProductsActivity
            val intent = Intent(activity, ShopProductsActivity::class.java)
            intent.putExtra("SELLER_ID", sellerId)
            intent.putExtra("SELLER_NAME", seller.name)
            startActivity(intent)
        }
        binding.shopsRecyclerView.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = shopAdapter
        }
    }

    private fun fetchShops() {
        binding.loadingIndicator.visibility = View.VISIBLE
        // Query Firebase for all users who have the role "Seller"
        database.orderByChild("role").equalTo("Seller")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    shopList.clear()
                    for (userSnapshot in snapshot.children) {
                        val user = userSnapshot.getValue(User::class.java)
                        val userId = userSnapshot.key
                        if (user != null && userId != null) {
                            // Add both the ID and the User object to the list
                            shopList.add(Pair(userId, user))
                        }
                    }
                    shopAdapter.notifyDataSetChanged()
                    binding.loadingIndicator.visibility = View.GONE
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(context, "Failed to load shops.", Toast.LENGTH_SHORT).show()
                    binding.loadingIndicator.visibility = View.GONE
                }
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}