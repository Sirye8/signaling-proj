package com.guc_proj.signaling_proj

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.guc_proj.signaling_proj.buyer.BuyerOrdersFragment
import com.guc_proj.signaling_proj.buyer.CartFragment
import com.guc_proj.signaling_proj.buyer.ShopsFragment
import com.guc_proj.signaling_proj.databinding.ActivityBuyerHomeBinding

class BuyerHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBuyerHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBuyerHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if we need to navigate to a specific tab (e.g. Cart)
        val navigateTo = intent.getStringExtra("NAVIGATE_TO")
        if (savedInstanceState == null) {
            if (navigateTo == "CART") {
                loadFragment(CartFragment())
                binding.bottomNavigation.selectedItemId = R.id.nav_buyer_cart
            } else {
                loadFragment(ShopsFragment())
            }
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            var selectedFragment: Fragment? = null
            when (item.itemId) {
                R.id.nav_buyer_shops -> selectedFragment = ShopsFragment()
                R.id.nav_buyer_cart -> selectedFragment = CartFragment()
                R.id.nav_buyer_orders -> selectedFragment = BuyerOrdersFragment()
                R.id.nav_buyer_profile -> selectedFragment = ProfileFragment()
            }
            if (selectedFragment != null) {
                loadFragment(selectedFragment)
            }
            true
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val navigateTo = intent?.getStringExtra("NAVIGATE_TO")
        if (navigateTo == "CART") {
            loadFragment(CartFragment())
            binding.bottomNavigation.selectedItemId = R.id.nav_buyer_cart
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.buyer_fragment_container, fragment)
            .commit()
    }
}