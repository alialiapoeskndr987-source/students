package com.yousef.stephealth.rem

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yousef.stephealth.MainActivity
import com.yousef.stephealth.data.AppDatabase
import com.yousef.stephealth.data.PatientEntity
import com.yousef.stephealth.data.ProjectEntity
import com.yousef.stephealth.data.todayIso
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/*
 * التنبيه اليومي (البند 7 من المتطلبات):
 * - إشعار يومي الساعة 8:00 مساءً بتوقيت الهاتف أثناء حالة «جاري» فقط.
 * - النص يذكر عدد القياسات الناقصة اليوم مقسمة على المجموعتين،
 *   أو «اكتملت قياسات اليوم» عند اكتمال الجميع.
 * - الضغط على الإشعار يفتح شاشة «قياسات اليوم المعلقة».
 * - WorkManager يجعل التنبيه ينجو من إعادة تشغيل الهاتف مع إعادة جدولة تلقائية.
 */

object ReminderScheduler {

    const val WORK_NAME = "daily_measurement_reminder"
    const val META_ENABLED = "reminder_enabled"
    const val META_HOUR = "reminder_hour"
    const val DEFAULT_HOUR = 20
    const val CHANNEL_ID = "daily_reminders"

    /** جدولة التنبيه اليومي على الساعة المحددة (توقيت الهاتف) */
    fun schedule(context: Context, hour: Int) {
        val now = Calendar.getInstance()
        val next = now.clone() as Calendar
        next.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
        next.set(Calendar.MINUTE, 0)
        next.set(Calendar.SECOND, 0)
        next.set(Calendar.MILLISECOND, 0)
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 1)
        val delayMs = next.timeInMillis - now.timeInMillis

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

/**
 * عامل العمل اليومي — يفحص المشروع الجاري ويحسب الناقص ثم يعرض الإشعار.
 * لا إشعار إذا: لا يوجد مشروع / الحالة ليست «جاري» / الإشعارات معطلة من النظام.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val db = AppDatabase.get(context)
        try {
            val project = db.projectDao().latest().first() ?: return Result.success()
            if (project.status != ProjectEntity.STATUS_RUNNING) return Result.success()

            val patients = db.patientDao().listForProject(project.id)
            if (patients.isEmpty()) return Result.success()
            val today = todayIso()
            val measured = db.measurementDao().measuredIdsOnOnce(project.id, today).toSet()
            val missing = patients.filter { it.id !in measured }

            val sportsMissing = missing.count { it.groupType == PatientEntity.GROUP_SPORTS }
            val controlMissing = missing.count { it.groupType == PatientEntity.GROUP_CONTROL }

            val text = if (missing.isEmpty()) {
                "اكتملت قياسات اليوم — كل المرضى سجلوا قياسهم"
            } else {
                "لديك ${missing.size} ${if (missing.size == 1) "قياس غير مسجل" else "قياسات غير مسجلة"} اليوم — " +
                    "$sportsMissing من المجموعة الرياضية و$controlMissing من الضابطة"
            }

            showNotification(context, project.name, text, missing.isNotEmpty())
        } catch (t: Throwable) {
            // التنبيه لا يجوز أن يوقف العمل الدوري
            return Result.success()
        }
        return Result.success()
    }

    private fun showNotification(context: Context, title: String, text: String, hasMissing: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm != null && nm.getNotificationChannel(ReminderScheduler.CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    ReminderScheduler.CHANNEL_ID,
                    "التنبيه اليومي للقياسات",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "تذكير يومي بتسجيل قياسات السكر" }
            )
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < 33
        if (!granted) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open", "pending")
        }
        val pi = PendingIntent.getActivity(
            context, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (hasMissing) "قياسات اليوم — $title" else "قياسات اليوم")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
        try {
            NotificationManagerCompat.from(context).notify(2001, builder.build())
        } catch (_: SecurityException) {
            // أُلغي الإذن — لا شيء
        }
    }
}
