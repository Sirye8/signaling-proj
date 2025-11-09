package com.guc_proj.signaling_proj

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.guc_proj.signaling_proj.databinding.ActivitySellerHomeBinding
import com.guc_proj.signaling_proj.seller.SellerProductsFragment

class SellerHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySellerHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySellerHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load default fragment
        if (savedInstanceState == null) {
            loadFragment(SellerProductsFragment())
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_seller_products -> {
                    loadFragment(SellerProductsFragment())
                    true
                }
                R.id.nav_seller_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    false
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.seller_fragment_container, fragment)
            .commit()
    }
}