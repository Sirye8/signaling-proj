package com.guc_proj.signaling_proj

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.guc_proj.signaling_proj.databinding.ActivityProfileBinding

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            // Should not happen if logic is correct, but as a safeguard
            finish()
            return
        }

        database = FirebaseDatabase.getInstance().getReference("Users").child(currentUser.uid)

        fetchUserData()

        binding.saveChangesButton.setOnClickListener {
            updateUserData()
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
                Toast.makeText(baseContext, "Failed to load data.", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Update failed.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}