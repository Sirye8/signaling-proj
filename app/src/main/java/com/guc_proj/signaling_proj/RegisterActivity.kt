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

        binding.registerButton.setOnClickListener {
            registerUser()
        }

        binding.loginTextView.setOnClickListener {
            finish() // Go back to LoginActivity
        }
    }

    private fun registerUser() {
        val name = binding.nameEditText.text.toString().trim()
        val phone = binding.phoneEditText.text.toString().trim()
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString().trim()

        // Get selected role
        val selectedRoleId = binding.roleRadioGroup.checkedRadioButtonId
        if (selectedRoleId == -1) {
            Toast.makeText(this, "Please select a role (Buyer/Seller)", Toast.LENGTH_SHORT).show()
            return
        }
        val role = if (selectedRoleId == R.id.buyerRadioButton) "Buyer" else "Seller"

        if (name.isEmpty() || phone.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val firebaseUser = auth.currentUser!!
                // Create User object including the role, photoUrl starts empty
                val user = User(name, phone, "", email, role, "")

                // Save user data to Firebase Realtime Database
                FirebaseDatabase.getInstance().getReference("Users")
                    .child(firebaseUser.uid)
                    .setValue(user)
                    .addOnCompleteListener { dbTask ->
                        if (dbTask.isSuccessful) {
                            Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()

                            // Redirect to the appropriate home screen
                            val homeIntent = if (role == "Buyer") {
                                Intent(this, BuyerHomeActivity::class.java)
                            } else {
                                Intent(this, SellerHomeActivity::class.java)
                            }
                            // Clear back stack and start new home activity
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