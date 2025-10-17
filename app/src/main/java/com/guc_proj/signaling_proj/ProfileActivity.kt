package com.guc_proj.signaling_proj

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.guc_proj.signaling_proj.databinding.ActivityProfileBinding

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private var currentUserUid: String? = null

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

        fetchUserData()

        binding.saveChangesButton.setOnClickListener {
            updateUserData()
        }

        binding.logoutButton.setOnClickListener {
            logoutUser()
        }

        binding.deleteUserButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun fetchUserData() {
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(User::class.java)
                user?.let {
                    binding.nameEditText.setText(it.name)
                    binding.phoneEditText.setText(it.phone)
                    binding.addressEditText.setText(it.address)
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
            "name" to name,
            "phone" to phone,
            "address" to address
        )

        database.updateChildren(userUpdates).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to update profile.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun logoutUser() {
        auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteUserAccount()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteUserAccount() {
        val user = auth.currentUser
        if (user != null && currentUserUid != null) {
            // 1. Delete user data from Realtime Database
            database.removeValue().addOnCompleteListener { dbTask ->
                if (dbTask.isSuccessful) {
                    // 2. Delete user from Authentication
                    user.delete().addOnCompleteListener { authTask ->
                        if (authTask.isSuccessful) {
                            Toast.makeText(this, "Account deleted successfully.", Toast.LENGTH_SHORT).show()
                            logoutUser() // Reuse logout logic to navigate
                        } else {
                            Toast.makeText(this, "Failed to delete account: ${authTask.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Failed to delete user data: ${dbTask.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}