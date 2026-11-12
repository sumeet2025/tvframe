package com.nostr.tvframe

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.nostr.tvframe.data.local.AppDatabase
import com.nostr.tvframe.data.local.SmbCredentialStore
import com.nostr.tvframe.data.local.UserPreferencesRepository
import com.nostr.tvframe.data.remote.WikimediaClient
import com.nostr.tvframe.data.repository.PhotoRepository
import com.nostr.tvframe.data.storage.SmartCacheManager
import com.nostr.tvframe.data.storage.SmbNetworkDataSource
import com.nostr.tvframe.data.storage.UsbStorageDataSource
import com.nostr.tvframe.data.sync.SyncStatusTracker
import com.nostr.tvframe.data.worker.PrefetchWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Application singleton managing the dependency container, dynamic Coil DiskCache
 * (supporting hot-swappable USB cache expansion), and periodic WorkManager pre-fetching.
 */
class TVFrameApp : Application(), ImageLoaderFactory {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: AppDatabase
        private set

    lateinit var preferencesRepository: UserPreferencesRepository
        private set

    lateinit var smbCredentialStore: SmbCredentialStore
        private set

    lateinit var usbStorageDataSource: UsbStorageDataSource
        private set

    lateinit var smbNetworkDataSource: SmbNetworkDataSource
        private set

    lateinit var smartCacheManager: SmartCacheManager
        private set

    lateinit var photoRepository: PhotoRepository
        private set

    lateinit var syncStatusTracker: SyncStatusTracker
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        preferencesRepository = UserPreferencesRepository(this)
        smbCredentialStore = SmbCredentialStore(this)
        usbStorageDataSource = UsbStorageDataSource(this, preferencesRepository)
        smbNetworkDataSource = SmbNetworkDataSource(smbCredentialStore)
        smartCacheManager = SmartCacheManager(
            context = this,
            photoDao = database.photoDao(),
            preferencesRepository = preferencesRepository,
            usbStorageDataSource = usbStorageDataSource
        )
        syncStatusTracker = SyncStatusTracker(this)
        photoRepository = PhotoRepository(
            context = this,
            database = database,
            photoDao = database.photoDao(),
            seenHistoryDao = database.seenHistoryDao(),
            wikimediaApi = WikimediaClient.apiService,
            usbStorageDataSource = usbStorageDataSource,
            smbNetworkDataSource = smbNetworkDataSource,
            smartCacheManager = smartCacheManager,
            preferencesRepository = preferencesRepository,
            syncStatusTracker = syncStatusTracker
        )

        // USB hot-plug events are delivered by the manifest-declared UsbHotPlugReceiver
        // (registering it again at runtime caused every event to be handled twice).
        PrefetchWorker.schedulePeriodicPrefetch(this)

        // Verify mounted USB path on startup and prune expired cache entries
        applicationScope.launch {
            usbStorageDataSource.verifyUsbMountOrFallback()
            smartCacheManager.enforceStorageQuota()
        }
    }

    /**
     * Dynamically configures Coil's DiskCache to use the external USB cache directory
     * when available and mounted, or seamlessly falls back to internal TV flash cache.
     */
    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "TVFrame/1.0 (Android TV; https://primal.net/s; s@primal.net)"
                    )
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.22)
                    .build()
            }
            .diskCache {
                // Coil builds the disk cache lazily on first use, which happens on its IO
                // dispatcher. Reading prefs / resolving the USB cache dir here (instead of with
                // runBlocking when the ImageLoader is created) keeps that work off the main thread.
                val activeCacheDir: File = smartCacheManager.resolveActiveCacheDirectory()
                val maxQuotaBytes = cacheQuotaMbBlocking().toLong() * 1024L * 1024L
                DiskCache.Builder()
                    .directory(File(activeCacheDir, "coil_image_cache"))
                    .maxSizeBytes(maxQuotaBytes)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    private fun cacheQuotaMbBlocking(): Int =
        runBlocking(Dispatchers.IO) { preferencesRepository.userPreferencesFlow.first().cacheQuotaMb }

    companion object {
        lateinit var instance: TVFrameApp
            private set
    }
}
