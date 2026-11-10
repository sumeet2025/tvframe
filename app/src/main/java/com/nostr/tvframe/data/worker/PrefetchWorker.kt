package com.nostr.tvframe.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil.imageLoader
import coil.request.ImageRequest
import com.nostr.tvframe.TVFrameApp
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic background worker that pre-fetches upcoming Wikimedia & SMB photos into the
 * custom Coil disk cache (USB or internal flash) and runs LRU quota pruning while
 * preserving Favorited photos.
 */
class PrefetchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val app = TVFrameApp.instance
            app.photoRepository.refreshEnabledSources()

            // Pre-warm Coil's disk cache for the top 25 playlist items so crossfades never stutter
            val playlist = app.photoRepository.activePlaylistFlow.first().take(25)
            val imageLoader = applicationContext.imageLoader
            for (item in playlist) {
                val request = ImageRequest.Builder(applicationContext)
                    .data(item.resolveDisplayModel())
                    .build()
                imageLoader.enqueue(request)
            }

            app.smartCacheManager.enforceStorageQuota()
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "tvframe_periodic_prefetch"

        fun schedulePeriodicPrefetch(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<PrefetchWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
