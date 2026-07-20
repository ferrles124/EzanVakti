package com.mehmet.ezanvakti

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.*

class PrayerNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("name") ?: "Vakit"
        val message = intent.getStringExtra("message") ?: "Vakit girdi"
        
        val notification = NotificationCompat.Builder(context, "prayer_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🕌 $name")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(
            name.hashCode(),
            notification
        )
    }
}

object PrayerNotificationService {
    
    private const val ONGOING_NOTIFICATION_ID = 1000
    private var currentOngoingNotification: Notification? = null
    
    fun scheduleNotification(context: Context, name: String, message: String, timeMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                showPermissionNotification(context)
                return
            }
        }
        
        val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
            putExtra("name", name)
            putExtra("message", message)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            name.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    timeMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    timeMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            showPermissionNotification(context)
        }
    }
    
    fun updateOngoingNotification(
        context: Context,
        currentPrayer: String,
        currentTime: String,
        nextPrayer: String,
        nextTime: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, "prayer_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🕌 $currentPrayer ($currentTime)")
            .setContentText("⏳ Sonraki: $nextPrayer $nextTime")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
        
        currentOngoingNotification = notification
        NotificationManagerCompat.from(context).notify(
            ONGOING_NOTIFICATION_ID,
            notification
        )
    }
    
    fun removeOngoingNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(ONGOING_NOTIFICATION_ID)
        currentOngoingNotification = null
    }
    
    private fun showPermissionNotification(context: Context) {
        val notification = NotificationCompat.Builder(context, "prayer_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⚠️ Alarm İzni Gerekli")
            .setContentText("Ezan vakitleri için alarm iznini etkinleştirin")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(999, notification)
    }
}
