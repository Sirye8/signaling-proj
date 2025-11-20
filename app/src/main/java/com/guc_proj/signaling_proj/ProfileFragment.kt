package com.guc_proj.signaling_proj

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.amazonaws.AmazonClientException
import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.amazonaws.services.s3.model.ListObjectsRequest
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.guc_proj.signaling_proj.databinding.FragmentProfileBinding
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.UUID
import kotlin.concurrent.thread

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private var currentUserUid: String? = null
    private var currentUser: User? = null
    private lateinit var s3Client: AmazonS3Client
    private lateinit var transferUtility: TransferUtility
    private var selectedImageUri: Uri? = null
    private var userValueListener: ValueEventListener? = null
    private val AWS_ACCESS_KEY = BuildConfig.AWS_ACCESS_KEY
    private val AWS_SECRET_KEY = BuildConfig.AWS_SECRET_KEY
    private val S3_BUCKET_NAME = BuildConfig.S3_BUCKET_NAME

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        val firebaseUser = auth.currentUser

        if (firebaseUser == null) {
            logoutUser() // User shouldn't be here
            return
        }

        currentUserUid = firebaseUser.uid
        binding.emailEditText.setText(firebaseUser.email)
        database = FirebaseDatabase.getInstance().getReference("Users").child(currentUserUid!!)

        initS3Client()
        fetchUserData()

        binding.selectPhotoButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.saveChangesButton.setOnClickListener {
            updateUserData()
            selectedImageUri?.let { uri ->
                uploadImageToS3(uri)
            }
        }

        binding.logoutButton.setOnClickListener {
            logoutUser()
        }

        binding.deleteUserButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            if (_binding == null) return@registerForActivityResult // View destroyed
            Glide.with(this).load(uri).into(binding.profileImageView)
            Toast.makeText(context, "Photo selected. Press 'Save Changes' to upload.", Toast.LENGTH_LONG).show()
        }
    }

    private fun initS3Client() {
        try {
            if (AWS_ACCESS_KEY.isEmpty() || AWS_SECRET_KEY.isEmpty() || S3_BUCKET_NAME.isEmpty()) {
                Log.e("ProfileFragment_S3", "AWS credentials are not set in local.properties")
                Toast.makeText(context, "Storage service credentials missing.", Toast.LENGTH_LONG).show()
                return
            }

            val credentials = BasicAWSCredentials(AWS_ACCESS_KEY, AWS_SECRET_KEY)
            s3Client = AmazonS3Client(credentials, Region.getRegion(Regions.EU_NORTH_1))
            transferUtility = TransferUtility.builder()
                .context(requireContext().applicationContext)
                .s3Client(s3Client)
                .build()
        } catch (e: Exception) {
            Log.e("ProfileFragment_S3", "Error initializing S3 client: ${e.message}")
            Toast.makeText(context, "Failed to connect to storage service.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadImageToS3(uri: Uri) {
        if (!::transferUtility.isInitialized) {
            Toast.makeText(context, "Storage service not initialized.", Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(requireContext().cacheDir, "temp_image.jpg")
        try {
            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            if (file.length() > 512 * 1024) {
                Toast.makeText(context, "Image is too large (max 0.5 MB).", Toast.LENGTH_LONG).show()
                file.delete()
                return
            }
        } catch (e: Exception) {
            Log.e("ProfileFragment_S3", "File preparation error: ${e.message}")
            if (file.exists()) file.delete()
            return
        }

        val objectKey = "profile-photos/${currentUserUid}/${UUID.randomUUID()}.jpg"
        Toast.makeText(context, "Uploading photo...", Toast.LENGTH_SHORT).show()

        val transferObserver = transferUtility.upload(
            S3_BUCKET_NAME,
            objectKey,
            file,
            CannedAccessControlList.PublicRead
        )

        transferObserver.setTransferListener(object : TransferListener {
            override fun onStateChanged(id: Int, state: TransferState) {
                if (_binding == null) {
                    file.delete()
                    return
                }
                if (state == TransferState.COMPLETED) {
                    val photoUrl = s3Client.getUrl(S3_BUCKET_NAME, objectKey).toString()
                    saveImageUrlToFirebase(photoUrl)
                    file.delete()
                }
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {}

            override fun onError(id: Int, ex: Exception) {
                file.delete()
                if (_binding == null || !isAdded || (activity != null && requireActivity().isFinishing)) {
                    return
                }
                Log.e("ProfileFragment_S3", "S3 Upload Error ID $id: ${ex.message}", ex)
                val appCtx = context?.applicationContext
                when (ex) {
                    is AmazonClientException -> {
                        Toast.makeText(appCtx, "Upload failed. Please check your network connection.", Toast.LENGTH_LONG).show()
                    }
                    is AmazonServiceException -> {
                        Toast.makeText(appCtx, "Storage service error: ${ex.errorMessage}", Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        Toast.makeText(appCtx, "An unknown upload error occurred.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun saveImageUrlToFirebase(photoUrl: String) {
        database.child("photoUrl").setValue(photoUrl)
            .addOnSuccessListener {
                if (_binding == null) return@addOnSuccessListener
                Toast.makeText(context?.applicationContext, "Profile photo updated!", Toast.LENGTH_SHORT).show()
                selectedImageUri = null
            }
            .addOnFailureListener {
                if (_binding == null || !isAdded || (activity != null && requireActivity().isFinishing)) {
                    return@addOnFailureListener
                }
                Toast.makeText(context?.applicationContext, "Failed to save photo URL.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchUserData() {
        userValueListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return // View destroyed, do nothing
                currentUser = snapshot.getValue(User::class.java) // <-- STORE USER
                currentUser?.let {
                    if (it.role == "Seller") {
                        binding.nameTextInputLayout.hint = "Shop Name"
                    } else {
                        binding.nameTextInputLayout.hint = "Name"
                    }

                    binding.nameEditText.setText(it.name)
                    binding.phoneEditText.setText(it.phone)
                    binding.addressEditText.setText(it.address)
                    if (!it.photoUrl.isNullOrEmpty() && context != null) {
                        Glide.with(this@ProfileFragment)
                            .load(it.photoUrl)
                            .placeholder(R.drawable.ic_launcher_foreground)
                            .into(binding.profileImageView)
                    } else {
                        binding.profileImageView.setImageResource(R.drawable.ic_launcher_foreground)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding != null && isAdded && activity != null && !requireActivity().isFinishing) {
                    Toast.makeText(context, "Failed to load profile data.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        database.addListenerForSingleValueEvent(userValueListener!!)
    }

    private fun updateUserData() {
        val name = binding.nameEditText.text.toString().trim()
        val phone = binding.phoneEditText.text.toString().trim()
        val address = binding.addressEditText.text.toString().trim()
        val userUpdates = mapOf<String, Any>(
            "name" to name, "phone" to phone, "address" to address
        )
        database.updateChildren(userUpdates).addOnCompleteListener { task ->
            if (_binding == null || !isAdded || (activity != null && requireActivity().isFinishing)) {
                return@addOnCompleteListener // View destroyed or finishing
            }
            if (task.isSuccessful && selectedImageUri == null) {
                Toast.makeText(context?.applicationContext, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
            } else if (!task.isSuccessful) {
                Toast.makeText(context?.applicationContext, "Failed to update profile text data.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun logoutUser() {
        val hostActivity = activity
        if (hostActivity != null && isAdded) {
            auth.signOut()
            val intent = Intent(hostActivity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            intent.putExtra("FROM_LOGOUT", true)
            startActivity(intent)
            hostActivity.finish()
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to permanently delete your account? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteUserAccount() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteUserAccount() {
        val user = auth.currentUser
        if (user == null || currentUserUid == null) {
            if (_binding != null && isAdded && activity != null && !requireActivity().isFinishing) {
                Toast.makeText(context, "Not logged in.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // Check if the user is a seller
        if (currentUser?.role == "Seller") {
            // Start the seller data deletion chain
            Toast.makeText(context, "Deleting seller products...", Toast.LENGTH_SHORT).show()
            deleteAllSellerProducts {
                // This is the callback, executed after products are deleted
                Log.d("Delete", "Seller products deleted. Deleting user profile...")
                // Now delete the user's profile folder and DB entry
                deleteUserDbAndAuth()
            }
        } else {
            // User is a Buyer, just delete their profile
            Log.d("Delete", "User is a Buyer. Deleting user profile...")
            deleteUserDbAndAuth()
        }
    }

    // Finds and deletes all products and S3 images for the current seller.
    private fun deleteAllSellerProducts(onComplete: () -> Unit) {
        val productsRef = FirebaseDatabase.getInstance().getReference("Products")
        val sellerId = currentUserUid ?: return

        val query = productsRef.orderByChild("sellerId").equalTo(sellerId)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return
                if (!snapshot.exists()) {
                    Log.d("Delete", "Seller has no products to delete.")
                    onComplete()
                    return
                }

                val productCount = snapshot.childrenCount
                var deletedCount = 0

                Log.d("Delete", "Found $productCount products to delete.")

                for (productSnapshot in snapshot.children) {
                    val product = productSnapshot.getValue(Product::class.java)

                    // 1. Delete S3 image
                    product?.photoUrl?.let {
                        deleteProductImageFromS3(it)
                    }

                    // 2. Delete product from DB
                    productSnapshot.ref.removeValue().addOnCompleteListener {
                        deletedCount++
                        // When the last product is deleted, call the callback
                        if (deletedCount.toLong() == productCount) {
                            Log.d("Delete", "Finished deleting all products from DB.")
                            onComplete()
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding != null && isAdded && activity != null && !requireActivity().isFinishing) {
                    Log.e("Delete", "Failed to query products for deletion: ${error.message}")
                    Toast.makeText(context, "Failed to delete products. Aborting.", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    // Deletes the user's S3 profile photo folder, their "Users" entry, and their Auth account.
    private fun deleteUserDbAndAuth() {
        val user = auth.currentUser ?: return
        val appCtx = context?.applicationContext

        // 1. Delete S3 profile photo folder
        deleteS3ProfileFolder()

        // 2. Delete "Users" DB entry
        database.removeValue().addOnCompleteListener { dbTask ->
            if (_binding == null || !isAdded || (activity != null && requireActivity().isFinishing)) {
                return@addOnCompleteListener
            }
            if (dbTask.isSuccessful) {
                // 3. Delete Auth user
                user.delete().addOnCompleteListener { authTask ->
                    if (_binding == null || !isAdded || (activity != null && requireActivity().isFinishing)) {
                        return@addOnCompleteListener
                    }
                    if (authTask.isSuccessful) {
                        Toast.makeText(appCtx, "Account deleted successfully.", Toast.LENGTH_SHORT).show()
                        logoutUser()
                    } else {
                        Toast.makeText(appCtx, "Deletion failed. Please log in again and retry.", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Toast.makeText(appCtx, "Failed to delete user data.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Deletes a specific product image file from S3.
    private fun deleteProductImageFromS3(photoUrl: String) {
        if (!::s3Client.isInitialized) {
            Log.e("ProfileFragment_S3", "S3 client not init or photoUrl is null.")
            return
        }

        try {
            val objectKey = URL(photoUrl).path.substring(1) // remove leading '/'
            if (objectKey.isNotBlank()) {
                thread { // Run on a background thread
                    try {
                        s3Client.deleteObject(S3_BUCKET_NAME, objectKey)
                        Log.d("ProfileFragment_S3", "Deleted product image $objectKey from S3")
                    } catch (e: Exception) {
                        Log.e("ProfileFragment_S3", "Error deleting $objectKey from S3: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ProfileFragment_S3", "Error parsing S3 URL: ${e.message}", e)
        }
    }

    // Deletes the entire S3 folder for the user's profile photos.
    private fun deleteS3ProfileFolder() {
        if (!::s3Client.isInitialized) {
            Log.e("ProfileFragment_S3", "S3 client not initialized, skipping deletion.")
            return
        }
        if (currentUserUid.isNullOrEmpty()) {
            Log.e("ProfileFragment_S3", "User UID is null, cannot delete folder.")
            return
        }

        val folderKey = "profile-photos/$currentUserUid/"

        thread { // Run on a background thread
            try {
                Log.d("ProfileFragment_S3", "Listing objects for deletion in prefix: $folderKey")
                val listRequest = ListObjectsRequest()
                    .withBucketName(S3_BUCKET_NAME)
                    .withPrefix(folderKey)

                var objectListing = s3Client.listObjects(listRequest)

                while (true) {
                    for (summary in objectListing.objectSummaries) {
                        Log.d("ProfileFragment_S3", "Deleting object: ${summary.key}")
                        s3Client.deleteObject(S3_BUCKET_NAME, summary.key)
                    }

                    if (objectListing.isTruncated) {
                        objectListing = s3Client.listNextBatchOfObjects(objectListing)
                    } else {
                        break
                    }
                }
                Log.d("ProfileFragment_S3", "Successfully deleted folder contents for $folderKey")

            } catch (e: AmazonServiceException) {
                Log.e("ProfileFragment_S3", "S3 Service Error deleting folder: ${e.errorMessage}", e)
            } catch (e: AmazonClientException) {
                Log.e("ProfileFragment_S3", "S3 Client Error deleting folder: ${e.message}", e)
            } catch (e: Exception) {
                Log.e("ProfileFragment_S3", "Generic error deleting S3 folder: ${e.message}", e)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        userValueListener?.let { listener ->
            database.removeEventListener(listener)
        }
        userValueListener = null
        _binding = null
    }
}