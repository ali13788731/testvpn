package ir.pingscore.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ir.pingscore.app.data.AppSettings
import ir.pingscore.app.data.SettingsStore
import ir.pingscore.app.data.TestPipeline
import ir.pingscore.app.diag.TestMode
import ir.pingscore.app.diag.TestSource
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** تست دوره‌ای پس‌زمینه؛ همیشه حالت «سریع». نتیجه فقط در تاریخچه‌ی محلی ذخیره می‌شود. */
class PeriodicTestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext).load()
        if (!settings.backgroundEnabled) return Result.success()
        try {
            TestPipeline(applicationContext).run(
                mode = TestMode.QUICK,
                source = TestSource.BACKGROUND,
                name = null,
                includeSpeed = settings.backgroundIncludeSpeed,
                settings = settings,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // شکست یک دوره نباید باعث retry پشت‌سرهم (و مصرف باتری) شود.
        }
        return Result.success()
    }
}

object BackgroundScheduler {
    private const val WORK_NAME = "pingscore_periodic"

    /** بازه‌های مجاز (دقیقه). حداقل WorkManager برای کار دوره‌ای ۱۵ دقیقه است. */
    val intervalOptions = listOf(15, 30, 60, 180, 360, 720, 1440)

    fun apply(context: Context, settings: AppSettings) {
        val wm = WorkManager.getInstance(context.applicationContext)
        if (!settings.backgroundEnabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (settings.backgroundWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val interval = settings.backgroundIntervalMinutes.coerceAtLeast(15).toLong()
        val request = PeriodicWorkRequestBuilder<PeriodicTestWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
