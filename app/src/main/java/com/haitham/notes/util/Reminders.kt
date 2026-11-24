package com.haitham.notes.util

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.haitham.notes.MainActivity
import com.haitham.notes.R
import com.haitham.notes.data.Repo

/** تذكيرات الملاحظات: منبه من النظام يظهر إشعاراً في الوقت المحدد، ويُعاد جدولته بعد إعادة تشغيل الجهاز. */
object Reminders {
    private const val CHANNEL = "reminders"
    const val EXTRA_NOTE = "reminder_note_id"

    private fun pi(ctx: Context, noteId: String, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(ctx, noteId.hashCode(), Intent(ctx, ReminderReceiver::class.java).putExtra(EXTRA_NOTE, noteId), flags)

    fun schedule(ctx: Context, noteId: String, at: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val p = pi(ctx, noteId, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    fun cancel(ctx: Context, noteId: String) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        pi(ctx, noteId, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it) }
    }

    fun rescheduleAll(ctx: Context) {
        val now = System.currentTimeMillis()
        Repo.notes.value.forEach { n -> if (n.reminderAt > now && !n.trashed) schedule(ctx, n.id, n.reminderAt) }
    }

    fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context, noteId: String) {
        val n = Repo.note(noteId) ?: return
        if (n.trashed || n.reminderAt <= 0) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "التذكيرات", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(ctx, noteId.hashCode(),
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val body = n.plainText
        val notif = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(n.title.ifBlank { "تذكير بملاحظة" })
            .setContentText(body.lines().firstOrNull { it.isNotBlank() }.orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(300)))
            .setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        if (canNotify(ctx)) nm.notify(noteId.hashCode(), notif)
        Repo.saveNote(n.copy(reminderAt = 0), touch = false)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Repo.init(ctx)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Reminders.rescheduleAll(ctx)
            else -> intent.getStringExtra(Reminders.EXTRA_NOTE)?.let { Reminders.show(ctx, it) }
        }
    }
}
