package com.mehmet.ezanvakti

import android.app.AlarmManager
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
    
    fun scheduleNotification(context: Context, name: String, message: String, timeMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
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
    }
}
