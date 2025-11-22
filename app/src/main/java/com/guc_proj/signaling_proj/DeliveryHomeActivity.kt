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

        // Setup toolbar back navigation since this is now a sub-activity for Buyers
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

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
            }
            true
        }
    }

    private inner class DeliveryPageAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        // Removed Profile Fragment
        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> DeliveryOrdersFragment()
                1 -> DeliveryHistoryFragment()
                2 -> VoIPFragment()
                else -> DeliveryOrdersFragment()
            }
        }
    }
}