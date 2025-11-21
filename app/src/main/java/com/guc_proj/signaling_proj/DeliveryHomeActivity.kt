package com.guc_proj.signaling_proj

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.guc_proj.signaling_proj.databinding.ActivityDeliveryHomeBinding
import com.guc_proj.signaling_proj.delivery.DeliveryHistoryFragment
import com.guc_proj.signaling_proj.delivery.DeliveryOrdersFragment

class DeliveryHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeliveryHomeBinding
    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: DeliveryPageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeliveryHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewPager = binding.deliveryViewPager
        pagerAdapter = DeliveryPageAdapter(this)
        viewPager.adapter = pagerAdapter

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.bottomNavigation.menu.getItem(position).isChecked = true
            }
        })

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_delivery_jobs -> viewPager.currentItem = 0
                R.id.nav_delivery_history -> viewPager.currentItem = 1
                R.id.nav_delivery_voip -> viewPager.currentItem = 2
                R.id.nav_delivery_profile -> viewPager.currentItem = 3
            }
            true
        }
    }

    private inner class DeliveryPageAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 4

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> DeliveryOrdersFragment() // Active Jobs & Available
                1 -> DeliveryHistoryFragment() // Completed Jobs & Earnings
                2 -> VoIPFragment()
                3 -> ProfileFragment()
                else -> DeliveryOrdersFragment()
            }
        }
    }
}