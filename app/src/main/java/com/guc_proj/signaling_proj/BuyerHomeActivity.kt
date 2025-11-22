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
import com.guc_proj.signaling_proj.buyer.BuyerOrdersFragment
import com.guc_proj.signaling_proj.buyer.CartFragment
import com.guc_proj.signaling_proj.buyer.ShopsFragment
import com.guc_proj.signaling_proj.databinding.ActivityBuyerHomeBinding

class BuyerHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBuyerHomeBinding
    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: BuyerPageAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBuyerHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkNotificationPermission()

        viewPager = binding.buyerViewPager
        pagerAdapter = BuyerPageAdapter(this)
        viewPager.adapter = pagerAdapter

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position < binding.bottomNavigation.menu.size()) {
                    binding.bottomNavigation.menu.getItem(position).isChecked = true
                }
            }
        })

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_buyer_shops -> viewPager.currentItem = 0
                R.id.nav_buyer_cart -> viewPager.currentItem = 1
                R.id.nav_buyer_orders -> viewPager.currentItem = 2
                R.id.nav_buyer_voip -> viewPager.currentItem = 3
                R.id.nav_buyer_profile -> viewPager.currentItem = 4
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
        // Save Context: I am currently a Buyer
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putString("LAST_ACTIVE_MODE", "BUYER").apply()
    }

    private fun handleNavigationIntent(intent: Intent?) {
        val navigateTo = intent?.getStringExtra("NAVIGATE_TO")
        if (navigateTo == "CART") {
            viewPager.currentItem = 1
        } else if (navigateTo == "VOIP") {
            viewPager.currentItem = 3
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private inner class BuyerPageAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 5

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> ShopsFragment()
                1 -> CartFragment()
                2 -> BuyerOrdersFragment()
                3 -> VoIPFragment()
                4 -> ProfileFragment()
                else -> ShopsFragment()
            }
        }
    }
}