package com.guc_proj.signaling_proj

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.guc_proj.signaling_proj.databinding.ActivitySellerHomeBinding
import com.guc_proj.signaling_proj.seller.SellerOrdersFragment
import com.guc_proj.signaling_proj.seller.SellerProductsFragment

class SellerHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySellerHomeBinding
    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: SellerPageAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySellerHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkNotificationPermission()

        viewPager = binding.sellerViewPager
        pagerAdapter = SellerPageAdapter(this)
        viewPager.adapter = pagerAdapter
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.bottomNavigation.menu.getItem(position).isChecked = true
            }
        })

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_seller_products -> viewPager.currentItem = 0
                R.id.nav_seller_orders -> viewPager.currentItem = 1
                R.id.nav_seller_voip -> viewPager.currentItem = 2
                R.id.nav_seller_profile -> viewPager.currentItem = 3
            }
            true
        }

        handleNavigationIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Save Context: I am currently a Seller
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putString("LAST_ACTIVE_MODE", "SELLER").apply()
    }

    private fun handleNavigationIntent(intent: Intent?) {
        if (intent?.getStringExtra("NAVIGATE_TO") == "VOIP") {
            viewPager.currentItem = 2
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private inner class SellerPageAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 4

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> SellerProductsFragment()
                1 -> SellerOrdersFragment()
                2 -> VoIPFragment()
                3 -> ProfileFragment()
                else -> SellerProductsFragment()
            }
        }
    }
}