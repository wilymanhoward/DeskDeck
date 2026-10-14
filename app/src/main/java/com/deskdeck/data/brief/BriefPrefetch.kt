package com.deskdeck.data.brief

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.deskdeck.data.settings.NightSettings
import com.deskdeck.data.settings.SettingsRepository
import com.deskdeck.di.ApplicationScope
import com.deskdeck.domain.night.NightScheduleCalculator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** How long before wake-up the brief is fetched. */
val BRIEF_PREFETCH_LEAD: Duration = Duration.ofMinutes(15)

/** Delay until the prefetch for [nextWake]: [lead] before it, or now if that's already past. */
fun prefetchDelay(now: Instant, nextWake: Instant, lead: Duration = BRIEF_PREFETCH_LEAD): Duration {
    val at = nextWake.minus(lead)
    return if (at.isAfter(now)) Duration.between(now, at) else Duration.ZERO
}

/**
 * Keeps one WorkManager job queued for the next morning: about [BRIEF_PREFETCH_LEAD] before the
 * next scheduled wake-up, when there's a network. WorkManager may run it somewhat later (Doze
 * batches background work); the brief refreshes itself when it opens if the cache is old.
 */
@Singleton
class BriefScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            settings.settings.map { it.night }.distinctUntilChanged().collect(::schedule)
        }
    }

    fun schedule(night: NightSettings) {
        val work = WorkManager.getInstance(context)
        val nextWake = if (night.brief) NightScheduleCalculator(ZoneId.systemDefault()).nextWakeAfter(Instant.now(), night) else null
        if (nextWake == null) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = OneTimeWorkRequestBuilder<BriefPrefetchWorker>()
            .setInitialDelay(prefetchDelay(Instant.now(), nextWake).toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val WORK_NAME = "morning-brief-prefetch"
    }
}

@HiltWorker
class BriefPrefetchWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: BriefRepository,
    private val settings: SettingsRepository,
    private val scheduler: BriefScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val night = settings.settings.first().night
        if (!night.enabled || !night.brief) return Result.success()
        val fetched = repository.refresh(night)
        if (!fetched && runAttemptCount < MAX_RETRIES) return Result.retry()
        // Queue tomorrow's.
        scheduler.schedule(night)
        return Result.success()
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
