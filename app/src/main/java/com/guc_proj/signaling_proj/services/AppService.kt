package com.guc_proj.signaling_proj.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.guc_proj.signaling_proj.BuyerHomeActivity
import com.guc_proj.signaling_proj.DeliveryHomeActivity
import com.guc_proj.signaling_proj.Order
import com.guc_proj.signaling_proj.R
import com.guc_proj.signaling_proj.SellerHomeActivity
import com.guc_proj.signaling_proj.User
import com.guc_proj.signaling_proj.notifications.NotificationHelper
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class AppService : Service() {

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): AppService = this@AppService
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var audioManager: AudioManager
    private lateinit var powerManager: PowerManager
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private lateinit var database: DatabaseReference

    // --- VoIP State ---
    @Volatile var currentState = CallState.IDLE
        private set
    @Volatile var statusMessage: String = "Ready"
        private set
    var isUiVisible: Boolean = false

    // --- SOCKETS (Separated) ---
    private var sipSocket: DatagramSocket? = null
    private var rtpSocket: DatagramSocket? = null
    private var discoverySocket: DatagramSocket? = null

    private var targetIpAddress: InetAddress? = null

    // SIP uses 5060, RTP uses 5004
    private var mySipPort: Int = 5060
    private val myRtpPort: Int = 5004

    private var targetSipPort: Int = 5060
    private val targetRtpPort: Int = 5004

    private var isListening = false
    private var isDiscoveryActive = false

    // --- Audio Config ---
    private val sampleRate = 16000
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val minBufSizeRecord = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)
    private val minBufSizeTrack = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, audioFormat)
    private var ringtonePlayer: MediaPlayer? = null

    // --- Metadata ---
    var callStartTime: Long = 0L
    private var callerName: String = "Unknown"
    private var myName: String = "Unknown"
    private var myRole: String = "User"
    private var myUid: String? = null

    // --- RTP & SIP Variables ---
    private var rtpSequenceNumber = 0
    private var rtpTimestamp = 0L
    private val rtpSsrc = Random.nextInt()

    // --- Order Listeners ---
    private var myOrdersRef: Query? = null
    private var myOrdersListener: ChildEventListener? = null
    private var availableJobsRef: Query? = null
    private var availableJobsListener: ChildEventListener? = null

    data class PeerInfo(var port: Int, var name: String, var role: String, var lastSeen: Long)
    val discoveredPeers = ConcurrentHashMap<String, PeerInfo>()

    companion object {
        const val ACTION_START_CALL = "ACTION_START_CALL"
        const val ACTION_END_CALL = "ACTION_END_CALL"
        const val ACTION_ANSWER_CALL = "ACTION_ANSWER_CALL"
        const val ACTION_DECLINE_CALL = "ACTION_DECLINE_CALL"
        const val ACTION_TOGGLE_SPEAKER = "ACTION_TOGGLE_SPEAKER"
        const val ACTION_INIT = "ACTION_INIT"

        const val EXTRA_IP = "EXTRA_IP"
        const val EXTRA_PORT = "EXTRA_PORT"

        const val BROADCAST_UPDATE = "com.guc_proj.signaling_proj.UPDATE_UI"

        // SIP Methods
        private const val SIP_INVITE = "INVITE"
        private const val SIP_OK = "SIP/2.0 200 OK"
        private const val SIP_DECLINE = "SIP/2.0 603 Decline"
        private const val SIP_BYE = "BYE"

        private val DISCOVERY_PORT = 5001
        private val DISCOVERY_MSG_PREFIX = "DISCOVER_VOIP:"

        private val NOTIF_ID = 1001
        private val CHANNEL_APP_STATUS = "channel_app_status"
    }

    enum class CallState { IDLE, DIALING, RINGING, CONNECTED }

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        proximityWakeLock = powerManager.newWakeLock(32, "com.guc:VoIPProximity")
        database = FirebaseDatabase.getInstance().getReference("Orders")

        createStatusChannel()
        fetchMyInfoAndStartListeners()
    }

    private fun createStatusChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_APP_STATUS,
                "Background Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps the app active in background"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    // --- Unified Foreground Management ---
    private fun startStandbyForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_APP_STATUS)
            .setContentTitle("Signaling App")
            .setContentText("Running in background (SIP: $mySipPort, RTP: $myRtpPort)")
            .setSmallIcon(R.drawable.baseline_fastfood_24)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun startCallNotification() {
        val endIntent = PendingIntent.getService(this, 0, Intent(this, AppService::class.java).setAction(ACTION_END_CALL), PendingIntent.FLAG_IMMUTABLE)
        val speakerIntent = PendingIntent.getService(this, 0, Intent(this, AppService::class.java).setAction(ACTION_TOGGLE_SPEAKER), PendingIntent.FLAG_IMMUTABLE)

        val ip = targetIpAddress?.hostAddress ?: ""
        val notification = notificationHelper.getCallNotification(
            callerName, ip, callStartTime, endIntent, speakerIntent, audioManager.isSpeakerphoneOn
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    // --- Service Commands ---
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_INIT -> {
                val port = intent.getIntExtra(EXTRA_PORT, 5060)
                initSockets(port) // Initialize both SIP and RTP sockets
                startStandbyForeground()
                startDiscovery()
            }
            ACTION_START_CALL -> {
                val ip = intent.getStringExtra(EXTRA_IP)
                val port = intent.getIntExtra(EXTRA_PORT, 5060)
                if (ip != null) startOutgoingCall(ip, port)
            }
            ACTION_ANSWER_CALL -> acceptIncomingCall()
            ACTION_DECLINE_CALL -> rejectIncomingCall()
            ACTION_END_CALL -> endCall()
            ACTION_TOGGLE_SPEAKER -> toggleSpeaker()
        }
        return START_STICKY
    }

    // --- Setup Logic ---
    private fun fetchMyInfoAndStartListeners() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        myUid = uid

        FirebaseDatabase.getInstance().getReference("Users").child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(User::class.java)
                myName = user?.name ?: "Unknown"
                myRole = user?.role ?: "User"
                startOrderListeners(uid, myRole)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // --- Order Notification Logic ---
    private fun startOrderListeners(uid: String, role: String) {
        removeOrderListeners()
        if (role == "Seller") {
            myOrdersRef = database.orderByChild("sellerId").equalTo(uid)
            myOrdersListener = myOrdersRef?.addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val order = snapshot.getValue(Order::class.java) ?: return
                    if (order.status == Order.STATUS_PENDING) notificationHelper.showNotification("New Order", "New order from ${order.buyerName}", "ORDER", role)
                }
                override fun onChildChanged(snapshot: DataSnapshot, p: String?) {}
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, p: String?) {}
                override fun onCancelled(error: DatabaseError) {}
            })
        } else {
            myOrdersRef = database.orderByChild("buyerId").equalTo(uid)
            myOrdersListener = myOrdersRef?.addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, p: String?) {}
                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    val order = snapshot.getValue(Order::class.java) ?: return
                    val msg = when (order.status) {
                        Order.STATUS_ACCEPTED -> "Your order is accepted."
                        Order.STATUS_DELIVERED -> "Your order has arrived!"
                        else -> null
                    }
                    if (msg != null) notificationHelper.showNotification("Order Update", msg, "ORDER", role)
                }
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, p: String?) {}
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    private fun removeOrderListeners() {
        myOrdersListener?.let { myOrdersRef?.removeEventListener(it) }
        availableJobsListener?.let { availableJobsRef?.removeEventListener(it) }
    }

    // --- Socket & VoIP Logic ---
    private fun initSockets(sipPort: Int) {
        if (isListening && sipPort == mySipPort) return

        mySipPort = sipPort
        // myRtpPort is effectively constant 5004

        serviceScope.launch {
            try {
                // Close existing
                sipSocket?.close()
                rtpSocket?.close()

                // Initialize Separate Sockets
                sipSocket = DatagramSocket(mySipPort)
                sipSocket?.broadcast = true

                rtpSocket = DatagramSocket(myRtpPort)
                // RTP socket usually doesn't need broadcast, just point-to-point

                isListening = true
                updateStatus("SIP: $mySipPort | RTP: $myRtpPort")

                // Launch two independent listeners
                launch { listenForSip() }
                launch { listenForRtp() }

            } catch (e: Exception) {
                updateStatus("Error binding ports")
                Log.e("VoIP", "Socket bind error", e)
            }
        }
    }

    // Listener 1: SIP (Signaling) on Port 5060
    private suspend fun listenForSip() {
        val buffer = ByteArray(2048)
        val packet = DatagramPacket(buffer, buffer.size)

        while (currentCoroutineContext().isActive && sipSocket != null && !sipSocket!!.isClosed && isListening) {
            try {
                sipSocket!!.receive(packet)
                val length = packet.length
                if (length > 0) {
                    // Always treat port 5060 as Text (SIP)
                    val msg = String(packet.data, 0, length, Charset.defaultCharset())
                    handleSipSignal(msg, packet.address)
                }
            } catch (e: Exception) {
                if (sipSocket != null && !sipSocket!!.isClosed) Log.e("VoIP", "SIP Receive Error", e)
            }
        }
    }

    // Listener 2: RTP (Audio) on Port 5004
    private suspend fun listenForRtp() {
        val buffer = ByteArray(2048)
        val packet = DatagramPacket(buffer, buffer.size)

        while (currentCoroutineContext().isActive && rtpSocket != null && !rtpSocket!!.isClosed && isListening) {
            try {
                rtpSocket!!.receive(packet)
                val length = packet.length
                if (length > 0) {
                    // Always treat port 5004 as Binary (RTP)
                    if (currentState == CallState.CONNECTED) {
                        handleRtpPacket(buffer, length)
                    }
                }
            } catch (e: Exception) {
                if (rtpSocket != null && !rtpSocket!!.isClosed) Log.e("VoIP", "RTP Receive Error", e)
            }
        }
    }

    // --- Discovery & Scanning (ICMP Logic) ---
    private fun startDiscovery() {
        if (isDiscoveryActive) return
        isDiscoveryActive = true

        // Listener for Discovery Broadcasts
        serviceScope.launch {
            try {
                discoverySocket = DatagramSocket(DISCOVERY_PORT)
                discoverySocket?.broadcast = true
                val buf = ByteArray(1024)
                val pkt = DatagramPacket(buf, buf.size)
                while (isActive && isDiscoveryActive) {
                    discoverySocket?.receive(pkt)
                    val msg = String(pkt.data, 0, pkt.length)
                    val senderIp = pkt.address.hostAddress
                    if (senderIp != getLocalIpAddress()) {
                        if (msg.startsWith(DISCOVERY_MSG_PREFIX)) {
                            // Parse peer info
                            val payload = msg.removePrefix(DISCOVERY_MSG_PREFIX)
                            val parts = payload.split("|")
                            val port = parts.getOrNull(0)?.toIntOrNull() ?: 5060
                            val name = parts.getOrNull(1) ?: "Unknown"
                            val role = parts.getOrNull(2) ?: "Peer"
                            val host = senderIp ?: "Unknown"

                            val info = discoveredPeers[host]
                            val now = System.currentTimeMillis()

                            // IF NEW or Changed: Trigger ICMP Ping (Scanning requirement)
                            if (info == null || info.port != port) {
                                discoveredPeers[host] = PeerInfo(port, name, role, now)
                                broadcastUpdate()
                                executePing(host)
                            } else {
                                info.lastSeen = now
                            }
                        }
                    }
                }
            } catch (e: Exception) {}
        }

        // Broadcaster
        serviceScope.launch {
            while (isActive && isDiscoveryActive) {
                try {
                    val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                    val lastMode = prefs.getString("LAST_ACTIVE_MODE", null)
                    val effectiveRole = when (lastMode) {
                        "DELIVERY" -> "Delivery"
                        "SELLER" -> "Seller"
                        "BUYER" -> "Buyer"
                        else -> myRole
                    }
                    // We advertise SIP Port (mySipPort).
                    // RTP Port is implicitly 5004 for everyone in this system.
                    val msg = "$DISCOVERY_MSG_PREFIX$mySipPort|$myName|$effectiveRole"
                    val data = msg.toByteArray()
                    val pkt = DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT)
                    DatagramSocket().use { it.send(pkt) }
                } catch (e: Exception) {}
                delay(3000)
            }
        }

        // Cleanup stale peers
        serviceScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val iter = discoveredPeers.entries.iterator()
                var removed = false
                while (iter.hasNext()) {
                    if (now - iter.next().value.lastSeen > 12000) {
                        iter.remove()
                        removed = true
                    }
                }
                if (removed) broadcastUpdate()
                delay(5000)
            }
        }
    }

    private fun executePing(ip: String) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                // Shell command to ping
                val process = Runtime.getRuntime().exec("ping -c 1 -w 1 $ip")
                process.waitFor()
            } catch (e: Exception) {
                Log.e("VoIP", "Ping failed", e)
            }
        }
    }

    // --- SIP Signaling Logic ---
    private fun handleSipSignal(msg: String, senderIp: InetAddress) {
        serviceScope.launch {
            val lines = msg.split("\r\n")
            val requestLine = lines[0]

            if (requestLine.startsWith(SIP_INVITE)) {
                if (currentState == CallState.IDLE) {
                    targetIpAddress = senderIp
                    val host = senderIp.hostAddress ?: "Unknown"
                    callerName = discoveredPeers[host]?.name ?: "Unknown"
                    currentState = CallState.RINGING
                    playRingtone()
                    updateStatus("Incoming Call from $callerName")
                    broadcastUpdate()

                    if (!isUiVisible) notificationHelper.showIncomingCallNotification(callerName, host)
                }
            } else if (requestLine.startsWith(SIP_OK)) {
                if (currentState == CallState.DIALING) {
                    stopRingtone()
                    startAudioSession()
                }
            } else if (requestLine.startsWith(SIP_DECLINE)) {
                if (currentState == CallState.DIALING) {
                    stopRingtone()
                    resetToIdle("Call Rejected")
                }
            } else if (requestLine.startsWith(SIP_BYE)) {
                sendSipMessage(SIP_OK) // Acknowledge BYE
                resetToIdle("Call Ended")
            }
        }
    }

    private fun startOutgoingCall(ip: String, port: Int) {
        serviceScope.launch {
            try {
                targetIpAddress = InetAddress.getByName(ip)
                targetSipPort = port // SIP Port
                // targetRtpPort is fixed at 5004

                callerName = discoveredPeers[ip]?.name ?: "Remote User"
                currentState = CallState.DIALING
                updateStatus("Dialing $callerName...")
                broadcastUpdate()

                executePing(ip)

                // Send SIP INVITE
                for (i in 1..5) {
                    if (currentState != CallState.DIALING) break
                    sendSipMessage(SIP_INVITE)
                    delay(1000)
                }
                if (currentState == CallState.DIALING) {
                    resetToIdle("No Answer")
                }
            } catch (e: Exception) {
                updateStatus("Invalid IP")
            }
        }
    }

    private fun acceptIncomingCall() {
        stopRingtone()
        notificationHelper.cancelIncomingCallNotification()
        serviceScope.launch { sendSipMessage(SIP_OK) }
        startAudioSession()
        launchUI()
    }

    private fun rejectIncomingCall() {
        stopRingtone()
        notificationHelper.cancelIncomingCallNotification()
        serviceScope.launch { sendSipMessage(SIP_DECLINE) }
        resetToIdle("Call Declined")
    }

    private fun endCall() {
        serviceScope.launch { sendSipMessage(SIP_BYE) }
        resetToIdle("Call Ended")
    }

    // Sends Text SIP Messages via sipSocket (Port 5060)
    private fun sendSipMessage(methodType: String) {
        try {
            val targetHost = targetIpAddress?.hostAddress ?: "0.0.0.0"
            val myHost = getLocalIpAddress() ?: "0.0.0.0"

            val sipMsg = buildString {
                if (methodType.contains("SIP/2.0")) {
                    append("$methodType\r\n") // Response
                } else {
                    append("$methodType sip:$targetHost SIP/2.0\r\n") // Request
                }
                append("Via: SIP/2.0/UDP $myHost:$mySipPort;branch=z9hG4bK${System.currentTimeMillis()}\r\n")
                append("From: \"$myName\" <sip:$myHost>;tag=${System.currentTimeMillis()}\r\n")
                append("To: <sip:$targetHost>\r\n")
                append("Call-ID: ${myHost.hashCode()}-${targetHost.hashCode()}@$myHost\r\n")
                append("CSeq: 1 $methodType\r\n")
                append("Content-Length: 0\r\n")
                append("\r\n")
            }

            val data = sipMsg.toByteArray(Charset.defaultCharset())
            // Send to target SIP port (5060)
            val packet = DatagramPacket(data, data.size, targetIpAddress, targetSipPort)
            sipSocket?.send(packet)
        } catch (e: Exception) {}
    }

    private fun launchUI() {
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val lastMode = prefs.getString("LAST_ACTIVE_MODE", null)
        val activityClass = when (lastMode) {
            "DELIVERY" -> DeliveryHomeActivity::class.java
            "SELLER" -> SellerHomeActivity::class.java
            "BUYER" -> BuyerHomeActivity::class.java
            else -> {
                when (myRole) {
                    "Seller" -> SellerHomeActivity::class.java
                    else -> BuyerHomeActivity::class.java
                }
            }
        }
        val intent = Intent(this, activityClass).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("NAVIGATE_TO", "VOIP")
        }
        startActivity(intent)
    }

    // --- Audio & RTP Logic ---
    private fun startAudioSession() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateStatus("Audio Permission Missing")
            resetToIdle("Call Failed")
            return
        }

        currentState = CallState.CONNECTED
        callStartTime = System.currentTimeMillis()
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true

        rtpSequenceNumber = 0
        rtpTimestamp = 0

        updateStatus("Connected")
        broadcastUpdate()
        startCallNotification()

        serviceScope.launch { recordAndSendAudio() }
    }

    // Sends Binary RTP Packets via rtpSocket (Port 5004)
    private suspend fun recordAndSendAudio() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        val buffer = ByteArray(minBufSizeRecord)
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, sampleRate, channelConfigIn, audioFormat, minBufSizeRecord)
        try { AcousticEchoCanceler.create(recorder.audioSessionId).enabled = true } catch (e: Exception) {}

        try {
            recorder.startRecording()
            while (currentState == CallState.CONNECTED) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val rtpPacket = createRtpPacket(buffer, read)
                    // Send to target RTP port (5004)
                    val packet = DatagramPacket(rtpPacket, rtpPacket.size, targetIpAddress, targetRtpPort)
                    rtpSocket?.send(packet)
                }
            }
        } catch (e: Exception) {
            Log.e("VoIP", "Audio Send Error", e)
        } finally {
            try { recorder.stop(); recorder.release() } catch (e: Exception) {}
        }
    }

    // Wrap raw audio in RTP Header
    private fun createRtpPacket(payload: ByteArray, payloadLength: Int): ByteArray {
        val headerSize = 12
        val packet = ByteArray(headerSize + payloadLength)

        // RTP Header: V=2, P=0, X=0, CC=0 (0x80); PT=96 (0x60)
        packet[0] = 0x80.toByte()
        packet[1] = 0x60.toByte()

        // Sequence Number
        packet[2] = (rtpSequenceNumber shr 8).toByte()
        packet[3] = (rtpSequenceNumber and 0xFF).toByte()
        rtpSequenceNumber++

        // Timestamp
        packet[4] = (rtpTimestamp shr 24).toByte()
        packet[5] = (rtpTimestamp shr 16).toByte()
        packet[6] = (rtpTimestamp shr 8).toByte()
        packet[7] = (rtpTimestamp and 0xFF).toByte()
        rtpTimestamp += payloadLength

        // SSRC
        packet[8] = (rtpSsrc shr 24).toByte()
        packet[9] = (rtpSsrc shr 16).toByte()
        packet[10] = (rtpSsrc shr 8).toByte()
        packet[11] = (rtpSsrc and 0xFF).toByte()

        System.arraycopy(payload, 0, packet, headerSize, payloadLength)
        return packet
    }

    private fun handleRtpPacket(data: ByteArray, length: Int) {
        if (length > 12) {
            val payloadSize = length - 12
            val payload = ByteArray(payloadSize)
            System.arraycopy(data, 12, payload, 0, payloadSize)
            playAudio(payload, payloadSize)
        }
    }

    private var audioTrack: AudioTrack? = null
    private fun playAudio(data: ByteArray, length: Int) {
        if (audioTrack == null) {
            audioTrack = AudioTrack(AudioManager.STREAM_VOICE_CALL, sampleRate, channelConfigOut, audioFormat, minBufSizeTrack, AudioTrack.MODE_STREAM)
            audioTrack?.play()
        }
        audioTrack?.write(data, 0, length)
    }

    private fun resetToIdle(msg: String) {
        currentState = CallState.IDLE
        updateStatus(msg)
        stopRingtone()
        notificationHelper.cancelIncomingCallNotification()
        startStandbyForeground()

        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {}
        broadcastUpdate()
    }

    private fun toggleSpeaker() {
        audioManager.isSpeakerphoneOn = !audioManager.isSpeakerphoneOn
        if (currentState == CallState.CONNECTED) startCallNotification()
        broadcastUpdate()
    }

    private fun updateStatus(msg: String) {
        statusMessage = msg
        broadcastUpdate()
    }

    private fun broadcastUpdate() {
        val intent = Intent(BROADCAST_UPDATE)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun playRingtone() {
        try {
            ringtonePlayer = MediaPlayer.create(this, android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE))
            ringtonePlayer?.isLooping = true
            ringtonePlayer?.start()
        } catch (e: Exception) {}
    }

    private fun stopRingtone() {
        ringtonePlayer?.stop()
        ringtonePlayer?.release()
        ringtonePlayer = null
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

    override fun onDestroy() {
        isListening = false
        isDiscoveryActive = false

        sipSocket?.close()
        rtpSocket?.close()
        discoverySocket?.close()

        serviceScope.cancel()
        removeOrderListeners()
        stopRingtone()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder
}