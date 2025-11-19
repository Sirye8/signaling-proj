package com.guc_proj.signaling_proj

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.guc_proj.signaling_proj.databinding.FragmentVoipBinding
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.charset.Charset
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class VoIPFragment : Fragment(), SensorEventListener {

    private var _binding: FragmentVoipBinding? = null
    private val binding get() = _binding!!

    // Constants
    private val SIG_RING = "SIG:RING"
    private val SIG_ACC = "SIG:ACC"
    private val SIG_REJ = "SIG:REJ"
    private val SIG_END = "SIG:END"

    private val DISCOVERY_PORT = 5001
    private val DISCOVERY_MSG_PREFIX = "DISCOVER_VOIP:"

    // User Info
    private var myName: String = "Unknown"
    private var myRole: String = "User"

    // Call States
    private enum class CallState {
        IDLE, DIALING, RINGING, CONNECTED
    }
    private var currentState = CallState.IDLE

    // Network
    private var socket: DatagramSocket? = null
    private var discoverySocket: DatagramSocket? = null
    private var targetIpAddress: InetAddress? = null
    private var targetPortInt: Int = 5000
    private var myListeningPort: Int = 5000
    private var isListening = false
    private var isDiscoveryActive = true

    // Audio
    private val sampleRate = 16000
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val minBufSizeRecord = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)
    private val minBufSizeTrack = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, audioFormat)

    private lateinit var audioManager: AudioManager
    private lateinit var powerManager: PowerManager
    private lateinit var sensorManager: SensorManager
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var proximitySensor: Sensor? = null
    private var ringtonePlayer: MediaPlayer? = null

    // Helpers
    private val timerHandler = Handler(Looper.getMainLooper())
    private var callDurationSeconds = 0L

    private val portChangeHandler = Handler(Looper.getMainLooper())
    private val portChangeRunnable = Runnable {
        val portText = binding.portEditText.text.toString()
        if (portText.isNotEmpty()) {
            val newPort = portText.toInt()
            if (newPort != myListeningPort) restartSocket(newPort)
        }
    }

    // Discovery Data
    // Key: IP Address, Value: Info
    data class PeerInfo(var port: Int, var name: String, var role: String, var lastSeen: Long)
    private val discoveredPeers = ConcurrentHashMap<String, PeerInfo>()

    private val cleanupHandler = Handler(Looper.getMainLooper())
    private val cleanupRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val iterator = discoveredPeers.entries.iterator()
            var removed = false
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value.lastSeen > 12000) { // 12s timeout
                    iterator.remove()
                    removed = true
                }
            }
            if (removed) updatePeersUI()
            cleanupHandler.postDelayed(this, 5000)
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) initSocket(myListeningPort)
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVoipBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()
        audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        powerManager = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        sensorManager = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        proximityWakeLock = powerManager.newWakeLock(32, "com.guc:VoIPProximity")

        checkPermissions()
        displayLocalIp()
        fetchMyInfo()
        initSocket(5000)
        startDiscovery()

        cleanupHandler.post(cleanupRunnable)

        binding.startCallButton.setOnClickListener {
            if (currentState == CallState.IDLE) startOutgoingCall() else endCall()
        }
        binding.btnAccept.setOnClickListener { acceptIncomingCall() }
        binding.btnDecline.setOnClickListener { rejectIncomingCall() }
        binding.toggleSpeakerButton.setOnClickListener {
            val newState = !audioManager.isSpeakerphoneOn
            audioManager.isSpeakerphoneOn = newState
            Toast.makeText(context, "Speaker: ${if (newState) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        }

        binding.portEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                portChangeHandler.removeCallbacks(portChangeRunnable)
                portChangeHandler.postDelayed(portChangeRunnable, 1000)
            }
        })
    }

    private fun fetchMyInfo() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().getReference("Users").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val user = snapshot.getValue(User::class.java)
                    myName = user?.name ?: "Unknown"
                    myRole = user?.role ?: "User"
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // --- Socket ---
    private fun restartSocket(newPort: Int) {
        socket?.close()
        isListening = false
        Thread.sleep(200)
        initSocket(newPort)
    }

    private fun initSocket(port: Int) {
        if (socket != null && !socket!!.isClosed && port == myListeningPort) return
        myListeningPort = port
        try {
            socket = DatagramSocket(myListeningPort)
            socket?.broadcast = true
            isListening = true
            binding.statusTextView.text = "Status: Listening on Port $myListeningPort"
            thread { listenForPackets() }
        } catch (e: Exception) {
            binding.statusTextView.text = "Status: Port $myListeningPort Busy"
        }
    }

    private fun listenForPackets() {
        val buffer = ByteArray(minBufSizeRecord * 2)
        val packet = DatagramPacket(buffer, buffer.size)
        while (socket != null && !socket!!.isClosed && isListening) {
            try {
                socket!!.receive(packet)
                val length = packet.length
                if (length < 150) { // Increased limit to catch discovery packets if sent to main port
                    val msg = String(packet.data, 0, length, Charset.defaultCharset())
                    handleSignal(msg, packet.address)
                } else {
                    if (currentState == CallState.CONNECTED) playAudio(packet.data, length)
                }
            } catch (e: Exception) {}
        }
    }

    // --- Discovery ---
    private fun startDiscovery() {
        thread {
            try {
                discoverySocket = DatagramSocket(DISCOVERY_PORT)
                discoverySocket?.broadcast = true
                val buf = ByteArray(1024)
                val pkt = DatagramPacket(buf, buf.size)
                while (isDiscoveryActive && discoverySocket != null) {
                    discoverySocket?.receive(pkt)
                    val msg = String(pkt.data, 0, pkt.length)
                    val senderIp = pkt.address.hostAddress

                    if (senderIp != getLocalIpAddress()) {
                        if (msg.startsWith(DISCOVERY_MSG_PREFIX)) {
                            // Format: DISCOVER_VOIP:PORT|NAME|ROLE
                            val payload = msg.removePrefix(DISCOVERY_MSG_PREFIX)
                            val parts = payload.split("|")

                            val port = parts.getOrNull(0)?.toIntOrNull() ?: 5000
                            val name = parts.getOrNull(1) ?: "Unknown"
                            val role = parts.getOrNull(2) ?: "Peer"

                            val info = discoveredPeers[senderIp]
                            val now = System.currentTimeMillis()

                            if (info == null) {
                                discoveredPeers[senderIp] = PeerInfo(port, name, role, now)
                                updatePeersUI()
                            } else {
                                info.lastSeen = now
                                // Only update UI if data changed
                                if (info.port != port || info.name != name) {
                                    info.port = port
                                    info.name = name
                                    info.role = role
                                    updatePeersUI()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {}
        }

        // Broadcaster
        thread {
            while (isDiscoveryActive) {
                try {
                    // Format: DISCOVER_VOIP:PORT|NAME|ROLE
                    val msg = "$DISCOVERY_MSG_PREFIX$myListeningPort|$myName|$myRole"
                    val data = msg.toByteArray()
                    val pkt = DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT)
                    DatagramSocket().use { it.send(pkt) }
                } catch (e: Exception) {}
                Thread.sleep(3000)
            }
        }
    }

    private fun updatePeersUI() {
        activity?.runOnUiThread {
            if (_binding == null) return@runOnUiThread

            // Remove stale
            val viewsToRemove = mutableListOf<View>()
            for (i in 0 until binding.peersContainer.childCount) {
                val view = binding.peersContainer.getChildAt(i)
                val ip = view.tag as? String
                if (ip != null && !discoveredPeers.containsKey(ip)) {
                    viewsToRemove.add(view)
                }
            }
            viewsToRemove.forEach { binding.peersContainer.removeView(it) }

            // Add/Update
            for ((ip, info) in discoveredPeers) {
                val existingView = binding.peersContainer.findViewWithTag<View>(ip)
                if (existingView == null) {
                    addPeerRow(ip, info)
                } else {
                    // Update text fields if changed
                    val nameText = existingView.findViewById<TextView>(R.id.peerNameText)
                    val ipText = existingView.findViewById<TextView>(R.id.peerIpText)
                    val roleText = existingView.findViewById<TextView>(R.id.peerRoleText)

                    nameText.text = info.name
                    roleText.text = info.role
                    ipText.text = "$ip : ${info.port}"

                    // Update click listener
                    val callBtn = existingView.findViewById<Button>(R.id.peerCallButton)
                    callBtn.setOnClickListener {
                        binding.ipEditText.setText(ip)
                        binding.portEditText.setText(info.port.toString())
                        startOutgoingCall()
                    }
                }
            }

            if (binding.peersContainer.childCount > 0) {
                binding.scanningLayout.visibility = View.GONE
            } else {
                binding.scanningLayout.visibility = View.VISIBLE
            }
        }
    }

    private fun addPeerRow(ip: String, info: PeerInfo) {
        val itemView = layoutInflater.inflate(R.layout.item_peer, binding.peersContainer, false)
        itemView.tag = ip

        val nameText = itemView.findViewById<TextView>(R.id.peerNameText)
        val roleText = itemView.findViewById<TextView>(R.id.peerRoleText)
        val ipText = itemView.findViewById<TextView>(R.id.peerIpText)
        val callBtn = itemView.findViewById<Button>(R.id.peerCallButton)

        nameText.text = info.name
        roleText.text = info.role
        ipText.text = "$ip : ${info.port}"

        callBtn.setOnClickListener {
            binding.ipEditText.setText(ip)
            binding.portEditText.setText(info.port.toString())
            startOutgoingCall()
        }

        binding.peersContainer.addView(itemView)
    }

    // --- Call Logic ---
    private fun handleSignal(msg: String, senderIp: InetAddress) {
        activity?.runOnUiThread {
            when (msg) {
                SIG_RING -> {
                    if (currentState == CallState.IDLE) {
                        targetIpAddress = senderIp
                        showIncomingCallUI(senderIp.hostAddress)
                    }
                }
                SIG_ACC -> {
                    if (currentState == CallState.DIALING) {
                        stopRingtone()
                        startAudioSession()
                    }
                }
                SIG_REJ -> {
                    if (currentState == CallState.DIALING) {
                        stopRingtone()
                        resetToIdle("Call Rejected")
                    }
                }
                SIG_END -> resetToIdle("Call Ended")
            }
        }
    }

    private fun startOutgoingCall() {
        val ipStr = binding.ipEditText.text.toString().trim()
        val portStr = binding.portEditText.text.toString().trim()
        if (ipStr.isEmpty()) return
        try {
            targetIpAddress = InetAddress.getByName(ipStr)
            targetPortInt = if (portStr.isNotEmpty()) portStr.toInt() else 5000
            currentState = CallState.DIALING
            updateUIForDialing()
            thread {
                for (i in 1..10) {
                    if (currentState != CallState.DIALING) break
                    sendSignal(SIG_RING)
                    Thread.sleep(1000)
                }
                if (currentState == CallState.DIALING) {
                    activity?.runOnUiThread { resetToIdle("No Answer") }
                }
            }
        } catch (e: Exception) { Toast.makeText(context, "Invalid IP", Toast.LENGTH_SHORT).show() }
    }

    private fun showIncomingCallUI(callerIp: String?) {
        currentState = CallState.RINGING
        binding.incomingCallLayout.visibility = View.VISIBLE

        // Try to find name in discovered peers
        val knownPeer = discoveredPeers[callerIp]
        if (knownPeer != null) {
            binding.incomingIpText.text = "from ${knownPeer.name} (${knownPeer.role})"
        } else {
            binding.incomingIpText.text = "from ${callerIp ?: "Unknown"}"
        }

        playRingtone()
    }

    private fun acceptIncomingCall() {
        stopRingtone()
        binding.incomingCallLayout.visibility = View.GONE
        thread { sendSignal(SIG_ACC) }
        startAudioSession()
    }

    private fun rejectIncomingCall() {
        stopRingtone()
        binding.incomingCallLayout.visibility = View.GONE
        thread { sendSignal(SIG_REJ) }
        resetToIdle("Call Declined")
    }

    private fun startAudioSession() {
        currentState = CallState.CONNECTED
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
        binding.statusTextView.text = "Connected"
        binding.startCallButton.text = "End Call"
        binding.startCallButton.setBackgroundColor(requireContext().getColor(R.color.md_theme_error))
        startCallTimer()
        thread { recordAndSendAudio() }
    }

    private fun endCall() {
        thread { sendSignal(SIG_END) }
        resetToIdle("Call Ended")
    }

    private fun resetToIdle(statusMsg: String) {
        currentState = CallState.IDLE
        stopRingtone()
        stopCallTimer()
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        } catch (e: Exception) {}

        if (_binding != null) {
            binding.statusTextView.text = "Status: $statusMsg"
            binding.startCallButton.text = "Call"
            binding.startCallButton.setBackgroundColor(requireContext().getColor(R.color.md_theme_primary))
            binding.incomingCallLayout.visibility = View.GONE
            binding.ipEditText.isEnabled = true
            binding.portEditText.isEnabled = true
        }
    }

    private fun sendSignal(msg: String) {
        try {
            val data = msg.toByteArray()
            val packet = DatagramPacket(data, data.size, targetIpAddress, targetPortInt)
            socket?.send(packet)
        } catch (e: Exception) {}
    }

    private fun recordAndSendAudio() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        val buffer = ByteArray(minBufSizeRecord)
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, sampleRate, channelConfigIn, audioFormat, minBufSizeRecord)
        if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(recorder.audioSessionId).enabled = true
        recorder.startRecording()
        try {
            while (currentState == CallState.CONNECTED) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val packet = DatagramPacket(buffer, read, targetIpAddress, targetPortInt)
                    socket?.send(packet)
                }
            }
        } catch (e: Exception) {} finally { recorder.release() }
    }

    private var audioTrack: AudioTrack? = null
    private fun playAudio(data: ByteArray, length: Int) {
        if (audioTrack == null) audioTrack = AudioTrack(AudioManager.STREAM_VOICE_CALL, sampleRate, channelConfigOut, audioFormat, minBufSizeTrack, AudioTrack.MODE_STREAM).apply { play() }
        audioTrack?.write(data, 0, length)
    }

    private val timerRunnable = object : Runnable {
        override fun run() {
            callDurationSeconds++
            val m = callDurationSeconds / 60
            val s = callDurationSeconds % 60
            if (_binding != null) binding.timerTextView.text = String.format(Locale.US, "%02d:%02d", m, s)
            timerHandler.postDelayed(this, 1000)
        }
    }
    private fun startCallTimer() {
        callDurationSeconds = 0
        binding.timerTextView.text = "00:00"
        binding.timerTextView.visibility = View.VISIBLE
        timerHandler.postDelayed(timerRunnable, 1000)
    }
    private fun stopCallTimer() {
        timerHandler.removeCallbacks(timerRunnable)
        binding.timerTextView.visibility = View.GONE
    }
    private fun playRingtone() {
        try {
            ringtonePlayer = MediaPlayer.create(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            ringtonePlayer?.isLooping = true
            ringtonePlayer?.start()
        } catch (e: Exception) {}
    }
    private fun stopRingtone() {
        ringtonePlayer?.stop()
        ringtonePlayer?.release()
        ringtonePlayer = null
    }
    private fun updateUIForDialing() {
        binding.statusTextView.text = "Dialing..."
        binding.startCallButton.text = "End Call"
        binding.startCallButton.setBackgroundColor(requireContext().getColor(R.color.md_theme_error))
        binding.ipEditText.isEnabled = false
        binding.portEditText.isEnabled = false
    }
    private fun displayLocalIp() {
        binding.localIpChip.text = "My IP: ${getLocalIpAddress() ?: "Unknown"}"
    }
    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) return addr.hostAddress
                }
            }
        } catch (e: Exception) {}
        return null
    }
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    override fun onSensorChanged(event: SensorEvent?) {}
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onDestroyView() {
        super.onDestroyView()
        isDiscoveryActive = false
        isListening = false
        discoverySocket?.close()
        socket?.close()
        stopRingtone()
        stopCallTimer()
        portChangeHandler.removeCallbacks(portChangeRunnable)
        cleanupHandler.removeCallbacks(cleanupRunnable)
        _binding = null
    }
}