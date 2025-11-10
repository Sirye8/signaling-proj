package com.guc_proj.signaling_proj

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
            var selectedFragment: Fragment? = null
            when (item.itemId) {
                R.id.nav_seller_products -> {
                    selectedFragment = SellerProductsFragment()
                }
                R.id.nav_seller_profile -> {
                    selectedFragment = ProfileFragment()
                }
            }

            if (selectedFragment != null) {
                loadFragment(selectedFragment)
            }

            true // Return true to show the item as selected
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.seller_fragment_container, fragment)
            .commit()
    }
}