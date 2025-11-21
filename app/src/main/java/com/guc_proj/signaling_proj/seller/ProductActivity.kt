package com.guc_proj.signaling_proj.seller

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.guc_proj.signaling_proj.BuildConfig
import com.guc_proj.signaling_proj.Product
import com.guc_proj.signaling_proj.R
import com.guc_proj.signaling_proj.databinding.ActivityProductBinding
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ProductActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var s3Client: AmazonS3Client
    private lateinit var transferUtility: TransferUtility

    private var selectedImageUri: Uri? = null
    private var currentUserId: String? = null
    private var existingProduct: Product? = null
    private var isEditMode = false

    private val AWS_ACCESS_KEY = BuildConfig.AWS_ACCESS_KEY
    private val AWS_SECRET_KEY = BuildConfig.AWS_SECRET_KEY
    private val S3_BUCKET_NAME = BuildConfig.S3_BUCKET_NAME

    companion object {
        const val EXTRA_PRODUCT = "EXTRA_PRODUCT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            finish() // Handle Back Button
        }

        auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid

        if (currentUserId == null) {
            Toast.makeText(this, "You must be logged in.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (intent.hasExtra(EXTRA_PRODUCT)) {
            existingProduct = intent.getParcelableExtra(EXTRA_PRODUCT)
            if (existingProduct != null) {
                isEditMode = true
                populateUIForEdit()
            }
        }

        // Set dynamic title
        binding.toolbar.title = if (isEditMode) "Edit Product" else "Add Product"

        initS3Client()

        binding.selectPhotoButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.saveProductButton.setOnClickListener {
            saveProduct()
        }
    }

    private fun populateUIForEdit() {
        binding.saveProductButton.text = "Update Product"

        existingProduct?.let {
            binding.nameEditText.setText(it.name)
            binding.priceEditText.setText(it.price.toString())
            binding.quantityEditText.setText(it.quantity.toString())

            if (!it.photoUrl.isNullOrEmpty()) {
                Glide.with(this)
                    .load(it.photoUrl)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .into(binding.productImageView)
            }
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            Glide.with(this).load(uri).into(binding.productImageView)
        }
    }

    private fun initS3Client() {
        try {
            val credentials = BasicAWSCredentials(AWS_ACCESS_KEY, AWS_SECRET_KEY)
            s3Client = AmazonS3Client(credentials, Region.getRegion(Regions.EU_NORTH_1))
            transferUtility = TransferUtility.builder()
                .context(applicationContext)
                .s3Client(s3Client)
                .build()
        } catch (e: Exception) {
            Log.e("Product_S3", "Error initializing S3 client: ${e.message}")
            Toast.makeText(this, "Failed to connect to storage service.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveProduct() {
        val name = binding.nameEditText.text.toString().trim()
        val price = binding.priceEditText.text.toString().trim().toDoubleOrNull()
        val quantity = binding.quantityEditText.text.toString().trim().toIntOrNull()

        if (name.isEmpty() || price == null || quantity == null) {
            Toast.makeText(this, "Please fill all fields correctly.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.saveProductButton.isEnabled = false

        if (selectedImageUri != null) {
            Toast.makeText(this, "Uploading new image...", Toast.LENGTH_SHORT).show()
            uploadImageToS3(selectedImageUri!!, name, price, quantity)
        } else if (isEditMode) {
            val updatedProduct = existingProduct!!.copy(
                name = name,
                price = price,
                quantity = quantity
            )
            saveProductToFirebase(updatedProduct)
        } else {
            Toast.makeText(this, "Please select a product photo.", Toast.LENGTH_SHORT).show()
            binding.saveProductButton.isEnabled = true
        }
    }

    private fun uploadImageToS3(uri: Uri, name: String, price: Double, quantity: Int) {
        if (!::transferUtility.isInitialized) {
            Toast.makeText(this, "Storage service not initialized.", Toast.LENGTH_SHORT).show()
            binding.saveProductButton.isEnabled = true
            return
        }

        val file = File(cacheDir, "temp_image.jpg")
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            if (file.length() > 512 * 1024) { // 0.5 MB check
                Toast.makeText(this, "Image is too large (max 0.5 MB).", Toast.LENGTH_LONG).show()
                file.delete()
                binding.saveProductButton.isEnabled = true
                return
            }
        } catch (e: Exception) {
            Log.e("Product_S3", "File preparation error: ${e.message}")
            if (file.exists()) file.delete()
            binding.saveProductButton.isEnabled = true
            return
        }

        val productId = existingProduct?.productId ?: FirebaseDatabase.getInstance().getReference("Products").push().key ?: UUID.randomUUID().toString()
        val objectKey = "product-photos/$currentUserId/$productId.jpg"

        val transferObserver = transferUtility.upload(
            S3_BUCKET_NAME,
            objectKey,
            file,
            CannedAccessControlList.PublicRead
        )

        transferObserver.setTransferListener(object : TransferListener {
            override fun onStateChanged(id: Int, state: TransferState) {
                if (isFinishing || isDestroyed) {
                    file.delete()
                    return
                }
                if (state == TransferState.COMPLETED) {
                    val photoUrl = s3Client.getUrl(S3_BUCKET_NAME, objectKey).toString()
                    val product = Product(
                        productId = productId,
                        sellerId = currentUserId,
                        name = name,
                        price = price,
                        quantity = quantity,
                        photoUrl = photoUrl
                    )
                    saveProductToFirebase(product)
                    file.delete()
                }
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {}

            override fun onError(id: Int, ex: Exception) {
                file.delete()
                if (isFinishing || isDestroyed) return
                Log.e("Product_S3", "S3 Upload Error: ${ex.message}", ex)
                Toast.makeText(applicationContext, "Upload failed: ${ex.message}", Toast.LENGTH_LONG).show()
                binding.saveProductButton.isEnabled = true
            }
        })
    }

    private fun saveProductToFirebase(product: Product) {
        FirebaseDatabase.getInstance().getReference("Products")
            .child(product.productId!!)
            .setValue(product)
            .addOnSuccessListener {
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                val message = if (isEditMode) "Product updated!" else "Product added!"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                if (isFinishing || isDestroyed) return@addOnFailureListener
                Toast.makeText(this, "Failed to save product data.", Toast.LENGTH_SHORT).show()
                binding.saveProductButton.isEnabled = true
            }
    }
}