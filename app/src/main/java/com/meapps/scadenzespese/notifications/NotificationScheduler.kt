package com.meapps.scadenzespese.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.meapps.scadenzespese.GestionaleApplication
import com.meapps.scadenzespese.data.EntryEntity
import com.meapps.scadenzespese.data.EntryStatus
import com.meapps.scadenzespese.data.EntryType
import com.meapps.scadenzespese.util.euro
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class NotificationScheduler(private val context: Context) {
    private val work = WorkManager.getInstance(context)
    fun schedule(id: Long, entry: EntryEntity, reminderDays: List<Int>) {
        cancel(id)
        if (entry.status != EntryStatus.ACTIVE) return
        val due = LocalDate.ofEpochDay(entry.nextDate)
        (reminderDays + listOf(-1, -3, -7)).distinct().forEach { daysBefore ->
            val whenLocal = due.minusDays(daysBefore.toLong()).atTime(LocalTime.of(9, 0))
            val delay = Duration.between(LocalDateTime.now(), whenLocal).toMillis()
            if (delay > 0) {
                val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf("entryId" to id, "daysBefore" to daysBefore))
                    .addTag("entry-$id")
                    .build()
                work.enqueueUniqueWork("entry-$id-reminder-$daysBefore", ExistingWorkPolicy.REPLACE, request)
            }
        }
    }
    fun cancel(id: Long) = work.cancelAllWorkByTag("entry-$id").also {
        // unique jobs have deterministic names and are also cancelled before every reschedule
        (listOf(30,15,7,1,0,-1,-3,-7) + (0..365)).distinct().forEach { offset ->
            work.cancelUniqueWork("entry-$id-reminder-$offset")
        }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong("entryId", -1)
        val daysBefore = inputData.getInt("daysBefore", 0)
        val app = applicationContext as GestionaleApplication
        val item = app.database.dao().entrySnapshot().firstOrNull { it.id == id } ?: return Result.success()
        if (item.status != EntryStatus.ACTIVE) return Result.success()
        val today = LocalDate.now().toEpochDay()
        if (daysBefore < 0 && item.nextDate >= today) return Result.success()
        createChannel()
        val amount = item.amountCents?.let { " · ${euro(it)}" }.orEmpty()
        val timing = when {
            daysBefore < 0 -> "Scadenza ancora irrisolta"
            daysBefore == 0 -> if (item.type == EntryType.EXPENSE) "Addebito previsto oggi" else "Scade oggi"
            daysBefore == 1 -> if (item.type == EntryType.EXPENSE) "Addebito previsto domani" else "Scade domani"
            else -> "Scade tra $daysBefore giorni"
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(item.name)
            .setContentText("$timing$amount")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true).build()
        if (android.os.Build.VERSION.SDK_INT < 33 ||
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(applicationContext).notify((id*100+daysBefore).toInt(), notification)
        }
        return Result.success()
    }
    private fun createChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Scadenze e pagamenti",
            NotificationManager.IMPORTANCE_HIGH).apply { description="Promemoria locali delle scadenze" })
    }
    companion object { const val CHANNEL = "scadenze" }
}
