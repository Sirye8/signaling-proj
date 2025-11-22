package com.guc_proj.signaling_proj

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.guc_proj.signaling_proj.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Adjust hint based on role
        binding.roleRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.sellerRadioButton) {
                binding.nameTextInputLayout.hint = "Shop Name"
            } else {
                binding.nameTextInputLayout.hint = "Name"
            }
        }

        binding.registerButton.setOnClickListener {
            registerUser()
        }

        binding.loginTextView.setOnClickListener {
            finish()
        }
    }

    private fun registerUser() {
        val name = binding.nameEditText.text.toString().trim()
        val phone = binding.phoneEditText.text.toString().trim()
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString().trim()

        val selectedRoleId = binding.roleRadioGroup.checkedRadioButtonId
        if (selectedRoleId == -1) {
            Toast.makeText(this, "Please select a role", Toast.LENGTH_SHORT).show()
            return
        }

        // "Delivery" is no longer a separate role at registration
        val role = when(selectedRoleId) {
            R.id.buyerRadioButton -> "Buyer"
            R.id.sellerRadioButton -> "Seller"
            else -> "Buyer"
        }

        if (name.isEmpty() || phone.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (isFinishing || isDestroyed) return@addOnCompleteListener
            if (task.isSuccessful) {
                val firebaseUser = auth.currentUser!!
                val user = User(
                    name = name,
                    phone = phone,
                    email = email,
                    role = role,
                    photoUrl = "",
                    credit = 0.0
                )

                FirebaseDatabase.getInstance().getReference("Users")
                    .child(firebaseUser.uid)
                    .setValue(user)
                    .addOnCompleteListener { dbTask ->
                        if (isFinishing || isDestroyed) return@addOnCompleteListener
                        if (dbTask.isSuccessful) {
                            Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()
                            val homeIntent = when(role) {
                                "Buyer" -> Intent(this, BuyerHomeActivity::class.java)
                                "Seller" -> Intent(this, SellerHomeActivity::class.java)
                                else -> Intent(this, BuyerHomeActivity::class.java)
                            }
                            homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(homeIntent)
                            finish()
                        } else {
                            Toast.makeText(this, "Database Error: ${dbTask.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}