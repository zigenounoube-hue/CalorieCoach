package fr.caloriecoach.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

object WorkoutReminderScheduler {
    private const val action = "fr.caloriecoach.app.WORKOUT_REMINDER"
    private const val channelId = "workout_reminders"
    private const val prefsName = "workout_reminders"

    fun schedule(context: Context, enabled: Boolean, days: Set<Int>, hour: Int, minute: Int) {
        val prefs = context.getSharedPreferences(prefsName, 0)
        prefs.edit().putBoolean("enabled", enabled).putStringSet("days", days.map { it.toString() }.toSet()).putInt("hour", hour).putInt("minute", minute).apply()
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (day in 1..7) alarms.cancel(pendingIntent(context, day))
        if (enabled) days.forEach { scheduleDay(context, it, hour, minute) }
    }

    private fun scheduleDay(context: Context, day: Int, hour: Int, minute: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23)); set(Calendar.MINUTE, minute.coerceIn(0, 59)); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            val diff = (day - get(Calendar.DAY_OF_WEEK) + 7) % 7
            add(Calendar.DAY_OF_YEAR, diff)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 7)
        }
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent(context, day))
    }

    private fun pendingIntent(context: Context, day: Int): PendingIntent {
        val intent = Intent(context, WorkoutReminderReceiver::class.java).setAction(action).putExtra("day", day)
        return PendingIntent.getBroadcast(context, day, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    internal fun showAndRepeat(context: Context, day: Int) {
        val prefs = context.getSharedPreferences(prefsName, 0)
        val selected = prefs.getStringSet("days", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        val hour = prefs.getInt("hour", 18); val minute = prefs.getInt("minute", 0)
        if (!prefs.getBoolean("enabled", false) || day !in selected) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(channelId, "Rappels de séance", NotificationManager.IMPORTANCE_DEFAULT))
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Petit rappel : c’est l’heure de bouger")
            .setContentText("Ta séance t’attend. Adapte-la à ton énergie du jour.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(200 + day, notification)
        scheduleDay(context, day, hour, minute)
    }
}

class WorkoutReminderReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WorkoutReminderScheduler.showAndRepeat(context, intent.getIntExtra("day", -1))
    }
}
