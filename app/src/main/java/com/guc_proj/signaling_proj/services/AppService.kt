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

    private var socket: DatagramSocket? = null
    private var discoverySocket: DatagramSocket? = null
    private var targetIpAddress: InetAddress? = null
    private var targetPortInt: Int = 5000
    private var myListeningPort: Int = 5000

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

        private val SIG_RING = "SIG:RING"
        private val SIG_ACC = "SIG:ACC"
        private val SIG_REJ = "SIG:REJ"
        private val SIG_END = "SIG:END"

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
            .setContentText("Running in background") // Generic text as requested
            .setSmallIcon(R.drawable.baseline_fastfood_24)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Combine types: microphone (for calls) + dataSync (for orders)
            startForeground(NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
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
        startForeground(NOTIF_ID, notification)
    }

    // --- Service Commands ---
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_INIT -> {
                startStandbyForeground()
                val port = intent.getIntExtra(EXTRA_PORT, 5000)
                initSocket(port)
                startDiscovery()
            }
            ACTION_START_CALL -> {
                val ip = intent.getStringExtra(EXTRA_IP)
                val port = intent.getIntExtra(EXTRA_PORT, 5000)
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
                    if (order.status == Order.STATUS_PENDING) {
                        notificationHelper.showNotification("New Order", "New order from ${order.buyerName}", "ORDER", role)
                    }
                }
                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    val order = snapshot.getValue(Order::class.java) ?: return
                    if (order.status == Order.STATUS_OUT_FOR_DELIVERY) {
                        notificationHelper.showNotification("Order Dispatched", "Order for ${order.buyerName} picked up.", "ORDER", role)
                    } else if (order.status == Order.STATUS_DELIVERED) {
                        notificationHelper.showNotification("Order Delivered", "Order for ${order.buyerName} delivered.", "ORDER", role)
                    }
                }
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {}
            })
        } else {
            myOrdersRef = database.orderByChild("buyerId").equalTo(uid)
            myOrdersListener = myOrdersRef?.addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    val order = snapshot.getValue(Order::class.java) ?: return
                    val status = order.status
                    val shopName = order.sellerName ?: "Shop"
                    val msg = when (status) {
                        Order.STATUS_ACCEPTED -> "Your order is accepted."
                        Order.STATUS_PREPARING -> "Your order is being prepared."
                        Order.STATUS_READY_FOR_PICKUP -> if(order.deliveryType == Order.TYPE_PICKUP) "Ready for pickup!" else null
                        Order.STATUS_OUT_FOR_DELIVERY -> "Your order is out for delivery."
                        Order.STATUS_DELIVERED -> "Your order has arrived!"
                        Order.STATUS_REJECTED -> "Your order was cancelled."
                        else -> null
                    }
                    if (msg != null) notificationHelper.showNotification("Order Update ($shopName)", msg, "ORDER", role)
                }
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {}
            })

            availableJobsRef = database.orderByChild("status").equalTo(Order.STATUS_READY_FOR_PICKUP)
            availableJobsListener = availableJobsRef?.addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val order = snapshot.getValue(Order::class.java) ?: return
                    if (order.deliveryType == Order.TYPE_DELIVERY &&
                        order.deliveryPersonId == null &&
                        order.buyerId != uid) {
                        notificationHelper.showNotification("Job Available", "Pickup from ${order.sellerName}", "JOB", role)
                    }
                }
                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    private fun removeOrderListeners() {
        myOrdersListener?.let { myOrdersRef?.removeEventListener(it) }
        availableJobsListener?.let { availableJobsRef?.removeEventListener(it) }
    }

    // --- Socket & VoIP Logic ---
    private fun initSocket(port: Int) {
        if (socket != null && !socket!!.isClosed && port == myListeningPort) return
        myListeningPort = port
        serviceScope.launch {
            try {
                socket?.close()
                socket = DatagramSocket(myListeningPort)
                socket?.broadcast = true
                isListening = true
                updateStatus("Listening on Port $myListeningPort")
                listenForPackets()
            } catch (e: Exception) {
                updateStatus("Error: Port $myListeningPort Busy")
            }
        }
    }

    private suspend fun listenForPackets() {
        val buffer = ByteArray(minBufSizeRecord * 2)
        val packet = DatagramPacket(buffer, buffer.size)
        while (currentCoroutineContext().isActive && socket != null && !socket!!.isClosed && isListening) {
            try {
                socket!!.receive(packet)
                val length = packet.length
                if (length < 150) {
                    val msg = String(packet.data, 0, length, Charset.defaultCharset())
                    handleSignal(msg, packet.address)
                } else {
                    if (currentState == CallState.CONNECTED) playAudio(packet.data, length)
                }
            } catch (e: Exception) {
                if (socket != null && !socket!!.isClosed) Log.e("VoIP", "Socket receive error", e)
            }
        }
    }

    private fun startDiscovery() {
        if (isDiscoveryActive) return
        isDiscoveryActive = true

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
                            val payload = msg.removePrefix(DISCOVERY_MSG_PREFIX)
                            val parts = payload.split("|")
                            val port = parts.getOrNull(0)?.toIntOrNull() ?: 5000
                            val name = parts.getOrNull(1) ?: "Unknown"
                            val role = parts.getOrNull(2) ?: "Peer"
                            val host = senderIp ?: "Unknown"

                            val info = discoveredPeers[host]
                            val now = System.currentTimeMillis()
                            if (info == null || info.port != port || info.name != name || info.role != role) {
                                discoveredPeers[host] = PeerInfo(port, name, role, now)
                                broadcastUpdate()
                            } else {
                                info.lastSeen = now
                            }
                        }
                    }
                }
            } catch (e: Exception) {}
        }

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
                    val msg = "$DISCOVERY_MSG_PREFIX$myListeningPort|$myName|$effectiveRole"
                    val data = msg.toByteArray()
                    val pkt = DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT)
                    DatagramSocket().use { it.send(pkt) }
                } catch (e: Exception) {}
                delay(3000)
            }
        }

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

    private fun handleSignal(msg: String, senderIp: InetAddress) {
        serviceScope.launch {
            when (msg) {
                SIG_RING -> {
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

    private fun startOutgoingCall(ip: String, port: Int) {
        serviceScope.launch {
            try {
                targetIpAddress = InetAddress.getByName(ip)
                targetPortInt = port
                callerName = discoveredPeers[ip]?.name ?: "Remote User"
                currentState = CallState.DIALING
                updateStatus("Dialing $callerName...")
                broadcastUpdate()
                for (i in 1..10) {
                    if (currentState != CallState.DIALING) break
                    sendSignal(SIG_RING)
                    delay(1000)
                }
                if (currentState == CallState.DIALING) resetToIdle("No Answer")
            } catch (e: Exception) {
                updateStatus("Invalid IP")
            }
        }
    }

    private fun acceptIncomingCall() {
        stopRingtone()
        notificationHelper.cancelIncomingCallNotification()
        serviceScope.launch { sendSignal(SIG_ACC) }
        startAudioSession()
        launchUI()
    }

    private fun rejectIncomingCall() {
        stopRingtone()
        notificationHelper.cancelIncomingCallNotification()
        serviceScope.launch { sendSignal(SIG_REJ) }
        resetToIdle("Call Declined")
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

    private fun endCall() {
        serviceScope.launch { sendSignal(SIG_END) }
        resetToIdle("Call Ended")
    }

    private fun sendSignal(msg: String) {
        try {
            val data = msg.toByteArray()
            val packet = DatagramPacket(data, data.size, targetIpAddress, targetPortInt)
            socket?.send(packet)
        } catch (e: Exception) {}
    }

    private fun startAudioSession() {
        currentState = CallState.CONNECTED
        callStartTime = System.currentTimeMillis()
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
        updateStatus("Connected")
        broadcastUpdate()
        startCallNotification() // Active Call Notification
        serviceScope.launch { recordAndSendAudio() }
    }

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
                    val packet = DatagramPacket(buffer, read, targetIpAddress, targetPortInt)
                    socket?.send(packet)
                }
            }
        } catch (e: Exception) {} finally {
            try { recorder.stop(); recorder.release() } catch (e: Exception) {}
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

        startStandbyForeground() // Revert to Generic "Running in background"

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
        socket?.close()
        discoverySocket?.close()
        serviceScope.cancel()
        removeOrderListeners()
        stopRingtone()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder
}