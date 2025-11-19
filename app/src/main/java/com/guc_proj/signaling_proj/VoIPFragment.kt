package com.guc_proj.signaling_proj

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.guc_proj.signaling_proj.databinding.FragmentVoipBinding
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import kotlin.concurrent.thread

class VoIPFragment : Fragment() {

    private var _binding: FragmentVoipBinding? = null
    private val binding get() = _binding!!

    private var isCalling = false
    private var socket: DatagramSocket? = null

    // Audio Config
    private val sampleRate = 16000
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private val minBufSizeRecord = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)
    private val minBufSizeTrack = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, audioFormat)

    private lateinit var audioManager: AudioManager

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(context, "Permission granted.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Microphone permission required.", Toast.LENGTH_LONG).show()
            }
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

        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        checkPermissions()
        displayLocalIp()

        binding.startCallButton.setOnClickListener {
            if (isCalling) {
                stopCall()
            } else {
                if (!hasPermission()) {
                    checkPermissions()
                    return@setOnClickListener
                }

                val ip = binding.ipEditText.text.toString().trim()
                val portStr = binding.portEditText.text.toString().trim()

                if (ip.isNotEmpty() && portStr.isNotEmpty()) {
                    startCall(ip, portStr.toInt())
                } else {
                    Toast.makeText(context, "Enter Target IP and Port", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.toggleSpeakerButton.setOnClickListener {
            val isSpeakerOn = !audioManager.isSpeakerphoneOn
            audioManager.isSpeakerphoneOn = isSpeakerOn
            Toast.makeText(context, "Speakerphone: $isSpeakerOn", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayLocalIp() {
        val ip = getLocalIpAddress()
        if (ip != null) {
            binding.localIpTextView.text = "My IP: $ip"
        } else {
            binding.localIpTextView.text = "My IP: Unavailable"
            Toast.makeText(context, "Could not detect IP. Connect to WiFi.", Toast.LENGTH_LONG).show()
        }
    }

    // New Helper Function: Iterates through network interfaces to find the IPv4 address
    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val inetAddress = addresses.nextElement()
                    // Check if it's not a loopback address (127.0.0.1) and is IPv4
                    if (!inetAddress.isLoopbackAddress && inetAddress is java.net.Inet4Address) {
                        return inetAddress.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun startCall(targetIp: String, targetPort: Int) {
        isCalling = true

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true

        binding.startCallButton.text = "End Call"
        binding.startCallButton.setBackgroundColor(requireContext().getColor(R.color.md_theme_error))
        binding.statusTextView.text = "Connected (Speaker On)"
        binding.ipEditText.isEnabled = false
        binding.portEditText.isEnabled = false

        try {
            socket = DatagramSocket(targetPort)
            Log.d("VoIP", "Socket created on port $targetPort")
        } catch (e: Exception) {
            Log.e("VoIP", "Socket bind failed: ${e.message}")
            Toast.makeText(context, "Port $targetPort busy. Try restarting app.", Toast.LENGTH_SHORT).show()
            stopCall()
            return
        }

        thread { sendAudio(targetIp, targetPort) }
        thread { receiveAudio() }
    }

    private fun stopCall() {
        isCalling = false
        socket?.close()
        socket = null

        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        } catch (e: Exception) { e.printStackTrace() }

        activity?.runOnUiThread {
            if (_binding != null) {
                binding.startCallButton.text = "Start Call"
                binding.startCallButton.setBackgroundColor(requireContext().getColor(R.color.md_theme_primary))
                binding.statusTextView.text = "Status: Idle"
                binding.ipEditText.isEnabled = true
                binding.portEditText.isEnabled = true
            }
        }
    }

    private fun sendAudio(targetIp: String, targetPort: Int) {
        var recorder: AudioRecord? = null
        try {
            val address = InetAddress.getByName(targetIp)
            val buffer = ByteArray(minBufSizeRecord)

            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return

            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfigIn,
                audioFormat,
                minBufSizeRecord
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("VoIP", "AudioRecord init failed")
                return
            }

            recorder.startRecording()
            Log.d("VoIP", "Recording started sending to $targetIp:$targetPort")

            while (isCalling) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    try {
                        val packet = DatagramPacket(buffer, read, address, targetPort)
                        socket?.send(packet)
                    } catch (e: Exception) {
                        if (isCalling) Log.e("VoIP", "Packet send fail: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VoIP", "Sender Loop Error: ${e.message}")
        } finally {
            try {
                recorder?.stop()
                recorder?.release()
            } catch (e: Exception) { /* Ignore */ }
        }
    }

    private fun receiveAudio() {
        var track: AudioTrack? = null
        try {
            val buffer = ByteArray(minBufSizeRecord * 2)

            track = AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                sampleRate,
                channelConfigOut,
                audioFormat,
                minBufSizeTrack,
                AudioTrack.MODE_STREAM
            )

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                Log.e("VoIP", "AudioTrack init failed")
                return
            }

            track.play()
            Log.d("VoIP", "Player started")

            while (isCalling) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    track.write(packet.data, 0, packet.length)
                } catch (e: Exception) {
                    if (isCalling) Log.e("VoIP", "Receive Loop Error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("VoIP", "Receiver Setup Error: ${e.message}")
        } finally {
            try {
                track?.stop()
                track?.release()
            } catch (e: Exception) { /* Ignore */ }
        }
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkPermissions() {
        if (!hasPermission()) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopCall()
        _binding = null
    }
}