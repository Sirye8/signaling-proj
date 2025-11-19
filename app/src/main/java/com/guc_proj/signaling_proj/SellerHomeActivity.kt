package com.guc_proj.signaling_proj

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySellerHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
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

        if (savedInstanceState == null) {
            viewPager.currentItem = 0
        }
    }

    private inner class SellerPageAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 4 // Increased count

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> SellerProductsFragment()
                1 -> SellerOrdersFragment()
                2 -> VoIPFragment() // Added VoIP
                3 -> ProfileFragment()
                else -> SellerProductsFragment()
            }
        }
    }
}