package com.guc_proj.signaling_proj

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.guc_proj.signaling_proj.databinding.ActivityDeliveryHomeBinding
import com.guc_proj.signaling_proj.delivery.DeliveryHistoryFragment
import com.guc_proj.signaling_proj.delivery.DeliveryOrdersFragment
import com.guc_proj.signaling_proj.services.AppService

class DeliveryHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeliveryHomeBinding
    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: DeliveryPageAdapter

    // --- Service Binding Logic ---
    private var appService: AppService? = null
    private var isBound = false
    private val serviceListeners = mutableListOf<(AppService) -> Unit>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as AppService.LocalBinder
            appService = binder.getService()
            isBound = true
            // Notify any fragments waiting for the service
            serviceListeners.forEach { it(appService!!) }
            serviceListeners.clear()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            appService = null
        }
    }

    // Helper method for Fragments to get the service instance
    fun getService(listener: (AppService) -> Unit) {
        if (appService != null && isBound) {
            listener(appService!!)
        } else {
            serviceListeners.add(listener)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeliveryHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Bind to AppService immediately (ADDED)
        val serviceIntent = Intent(this, AppService::class.java)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        checkNotificationPermission()

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

        handleNavigationIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unbind service to prevent leaks (ADDED)
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Save Context: I am currently a Delivery Driver
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putString("LAST_ACTIVE_MODE", "DELIVERY").apply()
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

    private inner class DeliveryPageAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
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