package com.guc_proj.signaling_proj.buyer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.database.*
import com.guc_proj.signaling_proj.BuyerHomeActivity
import com.guc_proj.signaling_proj.Product
import com.guc_proj.signaling_proj.databinding.ActivityShopProductsBinding
import java.util.Locale

class ShopProductsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShopProductsBinding
    private lateinit var database: DatabaseReference
    private lateinit var productAdapter: ShopProductAdapter
    private val productList = mutableListOf<Product>()
    private var productsQuery: Query? = null
    private var productsListener: ValueEventListener? = null

    // Listener for Cart Updates
    private val cartListener: () -> Unit = {
        updateCartBar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShopProductsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sellerId = intent.getStringExtra("SELLER_ID")
        val sellerName = intent.getStringExtra("SELLER_NAME")
        binding.toolbar.title = sellerName ?: "Products"

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        if (sellerId == null) {
            Toast.makeText(this, "Shop not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        database = FirebaseDatabase.getInstance().getReference("Products")
        setupRecyclerView()
        fetchProducts(sellerId)
        updateCartBar() // Initial state

        // Handle View Cart Button
        binding.viewCartButton.setOnClickListener {
            // Navigate back to BuyerHomeActivity and switch to Cart Tab
            val intent = Intent(this, BuyerHomeActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.putExtra("NAVIGATE_TO", "CART")
            startActivity(intent)
        }
    }

    private fun setupRecyclerView() {
        productAdapter = ShopProductAdapter(productList)
        binding.productsRecyclerView.apply {
            layoutManager = GridLayoutManager(this@ShopProductsActivity, 2)
            adapter = productAdapter
        }
    }

    private fun updateCartBar() {
        val count = CartManager.getCartItemCount()
        if (count > 0) {
            binding.cartBarCard.visibility = View.VISIBLE
            binding.cartTotalItemsText.text = "$count items"
            binding.cartTotalPriceText.text = String.format(Locale.US, "Total: $%.2f", CartManager.getCartTotal())
        } else {
            binding.cartBarCard.visibility = View.GONE
        }
    }

    private fun fetchProducts(sellerId: String) {
        binding.loadingIndicator.visibility = View.VISIBLE
        productsQuery = database.orderByChild("sellerId").equalTo(sellerId)
        productsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isDestroyed) return
                productList.clear()
                for (productSnapshot in snapshot.children) {
                    val product = productSnapshot.getValue(Product::class.java)
                    product?.let { productList.add(it) }
                }
                productAdapter.notifyDataSetChanged()
                binding.loadingIndicator.visibility = View.GONE

                if (productList.isEmpty()) {
                    binding.emptyView.visibility = View.VISIBLE
                    binding.productsRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyView.visibility = View.GONE
                    binding.productsRecyclerView.visibility = View.VISIBLE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (isDestroyed) return
                Toast.makeText(this@ShopProductsActivity, "Failed to load products.", Toast.LENGTH_SHORT).show()
                binding.loadingIndicator.visibility = View.GONE
            }
        }
        productsQuery?.addValueEventListener(productsListener!!)
    }

    override fun onResume() {
        super.onResume()
        CartManager.addListener(cartListener)
        updateCartBar()
        if (::productAdapter.isInitialized) {
            productAdapter.notifyDataSetChanged()
        }
    }

    override fun onPause() {
        super.onPause()
        CartManager.removeListener(cartListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        productsListener?.let { listener ->
            productsQuery?.removeEventListener(listener)
        }
        productsQuery = null
        productsListener = null
    }
}