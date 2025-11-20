package com.guc_proj.signaling_proj.seller

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.guc_proj.signaling_proj.BuildConfig
import com.guc_proj.signaling_proj.Product
import com.guc_proj.signaling_proj.R
import com.guc_proj.signaling_proj.SellerHomeActivity
import com.guc_proj.signaling_proj.databinding.FragmentSellerProductsBinding
import java.net.URL

class SellerProductsFragment : Fragment() {

    private var _binding: FragmentSellerProductsBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var productAdapter: ProductAdapter
    private val productList = mutableListOf<Product>()

    private var productsQuery: Query? = null
    private var productsListener: ValueEventListener? = null

    private lateinit var s3Client: AmazonS3Client
    private val AWS_ACCESS_KEY = BuildConfig.AWS_ACCESS_KEY
    private val AWS_SECRET_KEY = BuildConfig.AWS_SECRET_KEY
    private val S3_BUCKET_NAME = BuildConfig.S3_BUCKET_NAME

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSellerProductsBinding.inflate(inflater, container, false)
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
        database = FirebaseDatabase.getInstance().getReference("Products")

        initS3Client()
        setupRecyclerView()
        fetchSellerProducts(currentUserId)

        // --- MODIFIED: Check Address before adding product ---
        binding.fabAddProduct.setOnClickListener {
            checkAddressAndNavigate(currentUserId)
        }
    }

    private fun checkAddressAndNavigate(userId: String) {
        // Disable button to prevent multiple clicks
        binding.fabAddProduct.isEnabled = false

        val userRef = FirebaseDatabase.getInstance().getReference("Users").child(userId)
        userRef.child("address").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Re-enable button
                if (_binding != null) binding.fabAddProduct.isEnabled = true

                val address = snapshot.getValue(String::class.java)

                if (address.isNullOrEmpty()) {
                    // Address is missing -> Block access and show dialog
                    showAddressRequiredDialog()
                } else {
                    // Address exists -> Proceed to Add Product
                    startActivity(Intent(activity, ProductActivity::class.java))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding != null) binding.fabAddProduct.isEnabled = true
                Toast.makeText(context, "Error verifying profile: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showAddressRequiredDialog() {
        if (context == null) return

        AlertDialog.Builder(requireContext())
            .setTitle("Address Required")
            .setMessage("You must add a shop address in your Profile before you can add products.")
            .setPositiveButton("Go to Profile") { _, _ ->
                // Navigate to the Profile Tab (Index 3 in ViewPager)
                (activity as? SellerHomeActivity)?.let {
                    val viewPager = it.findViewById<ViewPager2>(R.id.seller_view_pager)
                    viewPager.currentItem = 3
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun initS3Client() {
        try {
            val credentials = BasicAWSCredentials(AWS_ACCESS_KEY, AWS_SECRET_KEY)
            s3Client = AmazonS3Client(credentials, Region.getRegion(Regions.EU_NORTH_1))
        } catch (e: Exception) {
            Log.e("SellerProducts_S3", "Error initializing S3 client: ${e.message}")
        }
    }

    private fun setupRecyclerView() {
        productAdapter = ProductAdapter(productList,
            { product -> // On Edit Click
                val intent = Intent(activity, ProductActivity::class.java)
                intent.putExtra(ProductActivity.EXTRA_PRODUCT, product)
                startActivity(intent)
            },
            { product -> // On Delete Click
                showDeleteConfirmationDialog(product)
            }
        )
        binding.productsRecyclerView.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = productAdapter
        }
    }

    private fun fetchSellerProducts(sellerId: String) {
        productsQuery = database.orderByChild("sellerId").equalTo(sellerId)
        productsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return
                productList.clear()
                for (productSnapshot in snapshot.children) {
                    val product = productSnapshot.getValue(Product::class.java)
                    product?.let { productList.add(it) }
                }
                productAdapter.notifyDataSetChanged()
                binding.loadingIndicator.visibility = View.GONE
                if (productList.isEmpty()) {
                    binding.emptyView.visibility = View.VISIBLE
                } else {
                    binding.emptyView.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding != null && isAdded && activity != null && !requireActivity().isFinishing) {
                    Toast.makeText(
                        context,
                        "Failed to load products: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.loadingIndicator.visibility = View.GONE
                }
            }
        }
        productsQuery?.addValueEventListener(productsListener!!)
    }

    private fun showDeleteConfirmationDialog(product: Product) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Product")
            .setMessage("Are you sure you want to delete '${product.name}'?")
            .setPositiveButton("Delete") { _, _ -> deleteProduct(product) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteProduct(product: Product) {
        val productId = product.productId
        if (productId.isNullOrEmpty()) {
            Toast.makeText(context, "Cannot delete product: Invalid ID", Toast.LENGTH_SHORT).show()
            return
        }

        deleteProductImageFromS3(product.photoUrl)

        database.child(productId).removeValue()
            .addOnSuccessListener {
                if (_binding == null) return@addOnSuccessListener
                Toast.makeText(context?.applicationContext, "Product deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                Toast.makeText(
                    context?.applicationContext,
                    "Failed to delete product: ${it.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun deleteProductImageFromS3(photoUrl: String?) {
        if (photoUrl.isNullOrEmpty() || !::s3Client.isInitialized) {
            Log.e("SellerProducts_S3", "S3 client not init or photoUrl is null.")
            return
        }

        try {
            val objectKey = URL(photoUrl).path.substring(1) // remove leading '/'
            if (objectKey.isNotBlank()) {
                Thread {
                    try {
                        s3Client.deleteObject(S3_BUCKET_NAME, objectKey)
                        Log.d("SellerProducts_S3", "Deleted image $objectKey from S3")
                    } catch (e: Exception) {
                        Log.e(
                            "SellerProducts_S3",
                            "Error deleting $objectKey from S3: ${e.message}",
                            e
                        )
                    }
                }.start()
            }
        } catch (e: Exception) {
            Log.e("SellerProducts_S3", "Error parsing S3 URL: ${e.message}", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        productsListener?.let { listener ->
            productsQuery?.removeEventListener(listener)
        }
        productsQuery = null
        productsListener = null
        _binding = null
    }
}