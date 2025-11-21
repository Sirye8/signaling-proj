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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
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
        binding.fabAddProduct.setOnClickListener { checkAddressAndNavigate(currentUserId) }
    }

    private fun checkAddressAndNavigate(userId: String) {
        binding.fabAddProduct.isEnabled = false
        val userRef = FirebaseDatabase.getInstance().getReference("Users").child(userId)

        // RENAMED: savedAddresses -> Addresses
        userRef.child("Addresses").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding != null) binding.fabAddProduct.isEnabled = true
                if (snapshot.exists() && snapshot.childrenCount > 0) {
                    startActivity(Intent(activity, ProductActivity::class.java))
                } else {
                    showAddressRequiredDialog()
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
        } catch (e: Exception) { Log.e("SellerProducts_S3", "Error init S3: ${e.message}") }
    }

    private fun setupRecyclerView() {
        productAdapter = ProductAdapter(productList,
            { product ->
                val intent = Intent(activity, ProductActivity::class.java)
                intent.putExtra(ProductActivity.EXTRA_PRODUCT, product)
                startActivity(intent)
            },
            { product -> showDeleteConfirmationDialog(product) }
        )
        binding.productsRecyclerView.apply { layoutManager = GridLayoutManager(context, 2); adapter = productAdapter }
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
                binding.emptyView.visibility = if (productList.isEmpty()) View.VISIBLE else View.GONE
            }
            override fun onCancelled(error: DatabaseError) {
                if (_binding != null) binding.loadingIndicator.visibility = View.GONE
            }
        }
        productsQuery?.addValueEventListener(productsListener!!)
    }

    private fun showDeleteConfirmationDialog(product: Product) {
        AlertDialog.Builder(requireContext()).setTitle("Delete Product").setMessage("Are you sure?").setPositiveButton("Delete") { _, _ -> deleteProduct(product) }.setNegativeButton("Cancel", null).show()
    }

    private fun deleteProduct(product: Product) {
        val productId = product.productId ?: return
        deleteProductImageFromS3(product.photoUrl)
        database.child(productId).removeValue()
    }

    private fun deleteProductImageFromS3(photoUrl: String?) {
        if (photoUrl.isNullOrEmpty() || !::s3Client.isInitialized) return
        try {
            val objectKey = URL(photoUrl).path.substring(1)
            if (objectKey.isNotBlank()) { Thread { try { s3Client.deleteObject(S3_BUCKET_NAME, objectKey) } catch (e: Exception) {} }.start() }
        } catch (e: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        productsListener?.let { productsQuery?.removeEventListener(it) }
        _binding = null
    }
}