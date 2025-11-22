package com.guc_proj.signaling_proj.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.guc_proj.signaling_proj.BuyerHomeActivity
import com.guc_proj.signaling_proj.R
import com.guc_proj.signaling_proj.SellerHomeActivity
import com.guc_proj.signaling_proj.services.AppService
import kotlin.random.Random

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ORDERS = "channel_orders"
        const val CHANNEL_JOBS = "channel_jobs"
        const val CHANNEL_CALLS = "channel_calls"
        const val CHANNEL_INCOMING = "channel_incoming_calls"
        const val INCOMING_CALL_ID = 2002
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            // ... (Channel creation logic same as before) ...
            val orderChannel = NotificationChannel(CHANNEL_ORDERS, "Order Updates", NotificationManager.IMPORTANCE_HIGH)
            val jobChannel = NotificationChannel(CHANNEL_JOBS, "Delivery Jobs", NotificationManager.IMPORTANCE_DEFAULT)
            val callChannel = NotificationChannel(CHANNEL_CALLS, "Active Calls", NotificationManager.IMPORTANCE_LOW)
            val incomingChannel = NotificationChannel(CHANNEL_INCOMING, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifications for incoming VoIP calls"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(orderChannel)
            manager.createNotificationChannel(jobChannel)
            manager.createNotificationChannel(callChannel)
            manager.createNotificationChannel(incomingChannel)
        }
    }

    fun showNotification(title: String, message: String, type: String, userRole: String) {
        // ... (Same implementation as before) ...
        val intent = if (userRole == "Seller") {
            Intent(context, SellerHomeActivity::class.java)
        } else {
            Intent(context, BuyerHomeActivity::class.java)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val channelId = if (type == "JOB") CHANNEL_JOBS else CHANNEL_ORDERS
        val icon = if (type == "JOB") R.drawable.baseline_fastfood_24 else R.drawable.baseline_emoji_food_beverage_24

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(Random.nextInt(), builder.build())
        } catch (e: SecurityException) {}
    }

    fun getCallNotification(name: String, ip: String, startTime: Long, endIntent: PendingIntent, speakerIntent: PendingIntent, isSpeakerOn: Boolean): Notification {
        val speakerActionText = if (isSpeakerOn) "Speaker Off" else "Speaker On"
        return NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.baseline_call_24)
            .setContentTitle("Call in Progress")
            .setContentText("$name ($ip)")
            .setOngoing(true)
            .setUsesChronometer(true)
            .setWhen(startTime)
            .addAction(R.drawable.ic_call_end, "End Call", endIntent)
            .addAction(R.drawable.ic_volume_up, speakerActionText, speakerIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun showIncomingCallNotification(name: String, ip: String) {
        // Updated to AppService
        val answerIntent = Intent(context, AppService::class.java).setAction(AppService.ACTION_ANSWER_CALL)
        val answerPendingIntent = PendingIntent.getService(context, 0, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val declineIntent = Intent(context, AppService::class.java).setAction(AppService.ACTION_DECLINE_CALL)
        val declinePendingIntent = PendingIntent.getService(context, 1, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.baseline_call_24)
            .setContentTitle("Incoming Call")
            .setContentText("$name ($ip)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setOngoing(true)
            .addAction(R.drawable.baseline_call_24, "Answer", answerPendingIntent)
            .addAction(R.drawable.ic_call_end, "Decline", declinePendingIntent)
            .setFullScreenIntent(answerPendingIntent, true)

        try {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.notify(INCOMING_CALL_ID, builder.build())
        } catch (e: SecurityException) {}
    }

    fun cancelIncomingCallNotification() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(INCOMING_CALL_ID)
    }
}