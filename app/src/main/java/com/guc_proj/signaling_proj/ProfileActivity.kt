package com.guc_proj.signaling_proj

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.guc_proj.signaling_proj.databinding.ActivityProfileBinding
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private var currentUserUid: String? = null

    // --- AWS S3 VARIABLES ---
    private lateinit var s3Client: AmazonS3Client
    private lateinit var transferUtility: TransferUtility
    private var selectedImageUri: Uri? = null

    // --- AWS CREDENTIALS ---
    private val AWS_ACCESS_KEY = ""
    private val AWS_SECRET_KEY = ""
    private val S3_BUCKET_NAME = ""
    // -----------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        currentUserUid = currentUser.uid
        binding.emailEditText.setText(currentUser.email)
        database = FirebaseDatabase.getInstance().getReference("Users").child(currentUserUid!!)

        initS3Client()
        fetchUserData()

        // --- CLICK LISTENERS ---
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
            Glide.with(this).load(uri).into(binding.profileImageView)
            Toast.makeText(this, "Photo selected. Press 'Save Changes' to upload.", Toast.LENGTH_LONG).show()
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
            Log.e("ProfileActivity_S3", "Error initializing S3 client: ${e.message}")
            Toast.makeText(this, "Failed to connect to storage service.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadImageToS3(uri: Uri) {
        if (!::transferUtility.isInitialized) {
            Toast.makeText(this, "Storage service not initialized.", Toast.LENGTH_SHORT).show()
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
                return
            }
        } catch (e: Exception) {
            Log.e("ProfileActivity_S3", "File preparation error: ${e.message}")
            if (file.exists()) file.delete()
            return
        }

        val objectKey = "profile-photos/${currentUserUid}/${UUID.randomUUID()}.jpg"
        Toast.makeText(this, "Uploading photo...", Toast.LENGTH_SHORT).show()

        val transferObserver = transferUtility.upload(
            S3_BUCKET_NAME,
            objectKey,
            file,
            CannedAccessControlList.PublicRead
        )

        transferObserver.setTransferListener(object : TransferListener {
            override fun onStateChanged(id: Int, state: TransferState) {
                if (state == TransferState.COMPLETED) {
                    val photoUrl = s3Client.getUrl(S3_BUCKET_NAME, objectKey).toString()
                    saveImageUrlToFirebase(photoUrl)
                    file.delete()
                }
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {}

            // --- CORRECTED ERROR HANDLING ---
            override fun onError(id: Int, ex: Exception) {
                Log.e("ProfileActivity_S3", "S3 Upload Error ID $id: ${ex.message}", ex)
                when (ex) {
                    is AmazonClientException -> {
                        // This handles network errors and other client-side issues.
                        Toast.makeText(applicationContext, "Upload failed. Please check your network connection.", Toast.LENGTH_LONG).show()
                    }
                    is AmazonServiceException -> {
                        // This handles errors from the AWS service itself (e.g., access denied).
                        Toast.makeText(applicationContext, "Storage service error: ${ex.errorMessage}", Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        // A general fallback for any other unexpected errors.
                        Toast.makeText(applicationContext, "An unknown upload error occurred.", Toast.LENGTH_SHORT).show()
                    }
                }
                file.delete() // Clean up the temporary file on any error
            }
        })
    }

    private fun saveImageUrlToFirebase(photoUrl: String) {
        database.child("photoUrl").setValue(photoUrl)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile photo updated!", Toast.LENGTH_SHORT).show()
                selectedImageUri = null
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save photo URL.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchUserData() {
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(User::class.java)
                user?.let {
                    // Set hint based on role
                    if (it.role == "Seller") {
                        binding.nameTextInputLayout.hint = "Shop Name"
                    } else {
                        binding.nameTextInputLayout.hint = "Name"
                    }

                    binding.nameEditText.setText(it.name)
                    binding.phoneEditText.setText(it.phone)
                    binding.addressEditText.setText(it.address)
                    if (!it.photoUrl.isNullOrEmpty()) {
                        Glide.with(this@ProfileActivity)
                            .load(it.photoUrl)
                            .placeholder(R.drawable.ic_launcher_foreground)
                            .into(binding.profileImageView)
                    } else {
                        binding.profileImageView.setImageResource(R.drawable.ic_launcher_foreground)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(baseContext, "Failed to load profile data.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateUserData() {
        val name = binding.nameEditText.text.toString().trim()
        val phone = binding.phoneEditText.text.toString().trim()
        val address = binding.addressEditText.text.toString().trim()
        val userUpdates = mapOf<String, Any>(
            "name" to name, "phone" to phone, "address" to address
        )
        database.updateChildren(userUpdates).addOnCompleteListener { task ->
            if (task.isSuccessful && selectedImageUri == null) {
                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
            } else if (!task.isSuccessful) {
                Toast.makeText(this, "Failed to update profile text data.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun logoutUser() {
        auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to permanently delete your account?")
            .setPositiveButton("Delete") { _, _ -> deleteUserAccount() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteUserAccount() {
        val user = auth.currentUser
        if (user != null && currentUserUid != null) {
            database.removeValue().addOnCompleteListener { dbTask ->
                if (dbTask.isSuccessful) {
                    user.delete().addOnCompleteListener { authTask ->
                        if (authTask.isSuccessful) {
                            Toast.makeText(this, "Account deleted successfully.", Toast.LENGTH_SHORT).show()
                            logoutUser()
                        } else {
                            Toast.makeText(this, "Deletion failed. Please log in again and retry.", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Failed to delete user data.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}