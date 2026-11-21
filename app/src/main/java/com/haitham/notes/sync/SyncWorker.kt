package com.haitham.notes.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.haitham.notes.data.Repo
import java.util.concurrent.TimeUnit

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Repo.init(applicationContext)
        val email = Repo.prefs.accountEmail ?: return Result.success()
        if (!Repo.prefs.autoSync) return Result.success()
        val token = GoogleAuth.silentToken(applicationContext, email)
        if (token == null) { Repo.prefs.lastError = "يلزم تسجيل الدخول بحساب جوجل من جديد"; return Result.success() }
        return if (SyncManager.sync(applicationContext, token).isSuccess) Result.success()
        else if (runAttemptCount < 3) Result.retry() else Result.success()
    }
}

object SyncScheduler {
    private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** مزامنة يومية تلقائية */
    fun scheduleDaily(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS).setConstraints(net).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("daily_sync", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** مزامنة قريبة بعد أي تعديل (تُجمع التعديلات المتتالية في مزامنة واحدة) */
    fun requestSoon(ctx: Context) {
        val p = Repo.prefs
        if (p.accountEmail == null || !p.autoSync) return
        val req = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(net).setInitialDelay(45, TimeUnit.SECONDS).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("soon_sync", ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelAll(ctx: Context) = WorkManager.getInstance(ctx).run {
        cancelUniqueWork("daily_sync"); cancelUniqueWork("soon_sync")
    }
}
