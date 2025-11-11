package com.guc_proj.signaling_proj

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.guc_proj.signaling_proj.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        val fromLogout = intent.getBooleanExtra("FROM_LOGOUT", false)
        if (auth.currentUser != null && !fromLogout) {
            redirectUser(auth.currentUser!!.uid)
        }

        binding.loginButton.setOnClickListener {
            loginUser()
        }

        binding.registerTextView.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun loginUser() {
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (isFinishing || isDestroyed) return@addOnCompleteListener
            if (task.isSuccessful) {
                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                // Redirect based on role after successful login
                redirectUser(auth.currentUser!!.uid)
            } else {
                Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Reads the user's role from Firebase and redirects to the appropriate home activity.
     */
    private fun redirectUser(userId: String) {
        val userRef = FirebaseDatabase.getInstance().getReference("Users").child(userId)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isFinishing || isDestroyed) return@onDataChange
                if (!snapshot.exists()) {
                    // Handle case where user exists in Auth but not in Database (rare)
                    Toast.makeText(applicationContext, "User data not found. Please register again.", Toast.LENGTH_LONG).show()
                    auth.signOut() // Log out the user
                    return
                }

                val user = snapshot.getValue(User::class.java)
                val role = user?.role

                val homeIntent: Intent = when (role) {
                    "Buyer" -> Intent(this@LoginActivity, BuyerHomeActivity::class.java)
                    "Seller" -> Intent(this@LoginActivity, SellerHomeActivity::class.java)
                    else -> {
                        // Handle invalid or missing role
                        Toast.makeText(applicationContext, "User role is invalid or missing. Logging out.", Toast.LENGTH_LONG).show()
                        auth.signOut()
                        return // Stop execution
                    }
                }

                // Clear back stack and start the appropriate home activity
                homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(homeIntent)
                finish() // Close LoginActivity
            }

            override fun onCancelled(error: DatabaseError) {
                if (isFinishing || isDestroyed) return@onCancelled
                Toast.makeText(applicationContext, "Failed to read user role: ${error.message}", Toast.LENGTH_SHORT).show()
                auth.signOut() // Log out on error
            }
        })
    }
}