package com.guc_proj.signaling_proj

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.guc_proj.signaling_proj.databinding.FragmentVoipBinding
import com.guc_proj.signaling_proj.services.AppService
import java.net.NetworkInterface
import java.util.Locale

class VoIPFragment : Fragment() {

    private var _binding: FragmentVoipBinding? = null
    private val binding get() = _binding!!

    private var appService: AppService? = null

    private val uiHandler = Handler(Looper.getMainLooper())
    private val uiRunnable = object : Runnable {
        override fun run() {
            updateTimerUI()
            uiHandler.postDelayed(this, 1000)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) connectToService()
    }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AppService.BROADCAST_UPDATE) {
                updateUI()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVoipBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        checkPermissions()
        displayLocalIp()
        connectToService()

        binding.startCallButton.setOnClickListener {
            if (appService?.currentState == AppService.CallState.IDLE) {
                val ip = binding.ipEditText.text.toString()
                // Default to 5060 for SIP
                val port = binding.portEditText.text.toString().toIntOrNull() ?: 5060

                val intent = Intent(requireContext(), AppService::class.java).apply {
                    action = AppService.ACTION_START_CALL
                    putExtra(AppService.EXTRA_IP, ip)
                    putExtra(AppService.EXTRA_PORT, port)
                }
                requireContext().startService(intent)
            } else {
                val intent = Intent(requireContext(), AppService::class.java).apply {
                    action = AppService.ACTION_END_CALL
                }
                requireContext().startService(intent)
            }
        }

        binding.btnAccept.setOnClickListener {
            val intent = Intent(requireContext(), AppService::class.java).apply { action = AppService.ACTION_ANSWER_CALL }
            requireContext().startService(intent)
        }

        binding.btnDecline.setOnClickListener {
            val intent = Intent(requireContext(), AppService::class.java).apply { action = AppService.ACTION_DECLINE_CALL }
            requireContext().startService(intent)
        }

        binding.toggleSpeakerButton.setOnClickListener {
            val intent = Intent(requireContext(), AppService::class.java).apply { action = AppService.ACTION_TOGGLE_SPEAKER }
            requireContext().startService(intent)
        }
    }

    private fun connectToService() {
        val callback: (AppService?) -> Unit = { service ->
            if (service != null) {
                this.appService = service
                service.isUiVisible = true
                if (isAdded && _binding != null) {
                    updateUI()
                }
            }
        }
        (activity as? SellerHomeActivity)?.getService(callback)
        (activity as? BuyerHomeActivity)?.getService(callback)
        (activity as? DeliveryHomeActivity)?.getService(callback)
    }

    private fun updateUI() {
        if (!isAdded || _binding == null || context == null || appService == null) return

        val service = appService!!

        binding.statusTextView.text = service.statusMessage

        if (service.currentState == AppService.CallState.IDLE) {
            binding.startCallButton.text = "Call"
            binding.startCallButton.setBackgroundColor(requireContext().getColor(R.color.md_theme_primary))
            binding.ipEditText.isEnabled = true
            binding.portEditText.isEnabled = true
            binding.timerTextView.visibility = View.GONE
            uiHandler.removeCallbacks(uiRunnable)
        } else {
            binding.startCallButton.text = "End Call"
            binding.startCallButton.setBackgroundColor(requireContext().getColor(R.color.md_theme_error))
            binding.ipEditText.isEnabled = false
            binding.portEditText.isEnabled = false

            if (service.currentState == AppService.CallState.CONNECTED) {
                binding.timerTextView.visibility = View.VISIBLE
                uiHandler.removeCallbacks(uiRunnable)
                uiHandler.post(uiRunnable)
            }
        }

        if (service.currentState == AppService.CallState.RINGING) {
            binding.incomingCallLayout.visibility = View.VISIBLE
            binding.incomingIpText.text = service.statusMessage.replace("Incoming Call from ", "")
        } else {
            binding.incomingCallLayout.visibility = View.GONE
        }

        updatePeersList(service.discoveredPeers)
    }

    private fun updateTimerUI() {
        if (appService == null) return
        val duration = (System.currentTimeMillis() - appService!!.callStartTime) / 1000
        val m = duration / 60
        val s = duration % 60
        if (_binding != null) binding.timerTextView.text = String.format(Locale.US, "%02d:%02d", m, s)
    }

    private fun updatePeersList(peers: Map<String, AppService.PeerInfo>) {
        if (_binding == null) return

        val peersSnapshot = peers.toMap()
        binding.peersContainer.removeAllViews()

        if (peersSnapshot.isEmpty()) {
            binding.scanningLayout.visibility = View.VISIBLE
        } else {
            binding.scanningLayout.visibility = View.GONE
            val inflater = LayoutInflater.from(requireContext())

            for ((ip, info) in peersSnapshot) {
                val itemView = inflater.inflate(R.layout.item_peer, binding.peersContainer, false)

                itemView.findViewById<TextView>(R.id.peerNameText).text = info.name
                itemView.findViewById<TextView>(R.id.peerRoleText).text = info.role
                itemView.findViewById<TextView>(R.id.peerIpText).text = "$ip : ${info.port}"

                itemView.findViewById<Button>(R.id.peerCallButton).setOnClickListener {
                    binding.ipEditText.setText(ip)
                    binding.portEditText.setText(info.port.toString())

                    val intent = Intent(requireContext(), AppService::class.java).apply {
                        action = AppService.ACTION_START_CALL
                        putExtra(AppService.EXTRA_IP, ip)
                        putExtra(AppService.EXTRA_PORT, info.port)
                    }
                    requireContext().startService(intent)
                }
                binding.peersContainer.addView(itemView)
            }
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun displayLocalIp() {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        binding.localIpChip.text = "My IP: ${addr.hostAddress}"
                        return
                    }
                }
            }
        } catch (e: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        connectToService()
        ContextCompat.registerReceiver(
            requireContext(),
            updateReceiver,
            IntentFilter(AppService.BROADCAST_UPDATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        appService?.isUiVisible = false
        requireContext().unregisterReceiver(updateReceiver)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        uiHandler.removeCallbacks(uiRunnable)
        _binding = null
    }
}