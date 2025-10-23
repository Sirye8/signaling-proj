package com.guc_proj.signaling_proj

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class BuyerHomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private var currentUserUid: String? = null

    private lateinit var welcomeTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_buyer_home)

        welcomeTextView = findViewById(R.id.welcomeTextView)

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            // Should not happen if redirected from Login, but good practice
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        currentUserUid = currentUser.uid
        database = FirebaseDatabase.getInstance().getReference("Users").child(currentUserUid!!)

        fetchUserName()

        findViewById<Button>(R.id.profileButton).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun fetchUserName() {
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(User::class.java)
                val userName = user?.name
                if (!userName.isNullOrEmpty()) {
                    welcomeTextView.text = "Welcome, $userName!"
                } else {
                    welcomeTextView.text = "Welcome, Buyer!"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(baseContext, "Failed to load user data.", Toast.LENGTH_SHORT).show()
                welcomeTextView.text = "Welcome, Buyer!"
            }
        })
    }
}