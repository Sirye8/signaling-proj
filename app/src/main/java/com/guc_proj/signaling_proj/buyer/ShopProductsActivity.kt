package com.guc_proj.signaling_proj.buyer

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.database.*
import com.guc_proj.signaling_proj.Product
import com.guc_proj.signaling_proj.databinding.ActivityShopProductsBinding

class ShopProductsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShopProductsBinding
    private lateinit var database: DatabaseReference
    private lateinit var productAdapter: ShopProductAdapter
    private val productList = mutableListOf<Product>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShopProductsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sellerId = intent.getStringExtra("SELLER_ID")
        val sellerName = intent.getStringExtra("SELLER_NAME")

        binding.shopNameTextView.text = sellerName ?: "Products"

        if (sellerId == null) {
            Toast.makeText(this, "Shop not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        database = FirebaseDatabase.getInstance().getReference("Products")
        setupRecyclerView()
        fetchProducts(sellerId)
    }

    private fun setupRecyclerView() {
        productAdapter = ShopProductAdapter(productList) { product ->
            // Add item to cart
            CartManager.addItem(product)
            Toast.makeText(this, "${product.name} added to cart.", Toast.LENGTH_SHORT).show()
        }
        binding.productsRecyclerView.apply {
            layoutManager = GridLayoutManager(this@ShopProductsActivity, 2)
            adapter = productAdapter
        }
    }

    private fun fetchProducts(sellerId: String) {
        binding.loadingIndicator.visibility = View.VISIBLE
        database.orderByChild("sellerId").equalTo(sellerId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    productList.clear()
                    for (productSnapshot in snapshot.children) {
                        val product = productSnapshot.getValue(Product::class.java)
                        product?.let { productList.add(it) }
                    }
                    productAdapter.notifyDataSetChanged()
                    binding.loadingIndicator.visibility = View.GONE
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@ShopProductsActivity, "Failed to load products.", Toast.LENGTH_SHORT).show()
                    binding.loadingIndicator.visibility = View.GONE
                }
            })
    }
}