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
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.nostr.tvframe.TVFrameApp
import com.nostr.tvframe.data.sync.SyncTrigger
import com.nostr.tvframe.util.DisplaySize
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
            app.photoRepository.refreshEnabledSources(SyncTrigger.BACKGROUND)

            // Pre-warm Coil's DISK cache for the next 25 photos (screensaver playlist first) so
            // crossfades never stutter. Requests run one at a time, are decoded at no more than
            // screen size (capped at 1080p) and skip the memory cache, so a background run can
            // never hold dozens of full-resolution 4K bitmaps at once.
            val repo = app.photoRepository
            val upcoming = (repo.screensaverPlaylistFlow.first() + repo.activePlaylistFlow.first())
                .distinctBy { it.id }
                .take(25)
            val imageLoader = applicationContext.imageLoader
            val (decodeW, decodeH) = DisplaySize.cappedPx(applicationContext)
            for (item in upcoming) {
                val model = item.resolveDisplayModel()
                // Local files / USB content:// URIs are not disk-cached by Coil; only remote URLs benefit.
                if (model !is String || !model.startsWith("http")) continue
                val request = ImageRequest.Builder(applicationContext)
                    .data(model)
                    .size(decodeW, decodeH)
                    .precision(Precision.INEXACT)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
                imageLoader.execute(request) // suspends until downloaded; must finish before quota pruning
            }

            app.smartCacheManager.enforceStorageQuota()
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "tvframe_periodic_prefetch"

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
