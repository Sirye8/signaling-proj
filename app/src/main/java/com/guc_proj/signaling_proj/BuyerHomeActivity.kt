package com.guc_proj.signaling_proj

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.guc_proj.signaling_proj.buyer.CartFragment
import com.guc_proj.signaling_proj.buyer.ShopsFragment
import com.guc_proj.signaling_proj.databinding.ActivityBuyerHomeBinding

class BuyerHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBuyerHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBuyerHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load default fragment
        if (savedInstanceState == null) {
            loadFragment(ShopsFragment())
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            var selectedFragment: Fragment? = null
            when (item.itemId) {
                R.id.nav_buyer_shops -> {
                    selectedFragment = ShopsFragment()
                }
                R.id.nav_buyer_cart -> {
                    selectedFragment = CartFragment()
                }
                R.id.nav_buyer_profile -> {
                    selectedFragment = ProfileFragment()
                }
            }

            if (selectedFragment != null) {
                loadFragment(selectedFragment)
            }

            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.buyer_fragment_container, fragment)
            .commit()
    }
}